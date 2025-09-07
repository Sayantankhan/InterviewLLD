# Rate Limiting Algorithms

This document explains common rate-limiting algorithms you can include in your project: **Fixed Window**, **Sliding Window (counter and log variants)**, and **Leaky Bucket**. For each algorithm you'll find: a short description, behavior, pseudocode, pros/cons, complexity, and example usage patterns (including a Redis-friendly approach).

---

## 1. Fixed Window

**Description**
A simple approach that divides time into consecutive windows of fixed size (e.g., 1 minute). Each client (or key) has a counter per window. Once the counter for the current window exceeds the limit, further requests are rejected until the next window.

**Behavior**
- Time is partitioned into windows `[0..W), [W..2W), ...`.
- All requests within the same window count against the same quota.
- At window boundary the counter resets.

**Pseudocode**
```text
limit = 100
window = 60  # seconds
now = current_unix_sec()
window_start = (now // window) * window
count = get_counter(key, window_start)
if count < limit:
    increment_counter(key, window_start)
    allow()
else:
    reject()
```

**Pros**
- Extremely simple to implement.
- Low memory footprint (one counter per key per window).

**Cons**
- Bursty behavior at window boundaries: a client can send `limit` requests at the end of window N and `limit` at the start of window N+1, effectively doubling allowed burst in a short time.

**Redis implementation tip**
Use `INCR` with an expiry equal to the window size. Example: `INCR key:window_start` and set TTL for `window` seconds when creating the key.

**Complexity**
- Time: O(1) per request.
- Space: O(#active_keys).

---

## 2. Sliding Window (Counter approximation)

**Description**
Sliding Window Counter smooths the fixed-window boundary problem by keeping counters for two adjacent windows and computing a weighted sum based on how far we are into the current window.

**Behavior**
- Keep counters for current window and previous window.
- Compute weighted count: `count = current_count * (t / W) + prev_count * (1 - t / W)` where `t` is elapsed time into current window.
- This gives a linear interpolation and reduces boundary bursts.

**Pseudocode**
```text
limit = 100
W = 60
now = current_unix_sec()
current_window = (now // W)
t = (now % W) / W    # fraction into the current window
curr = get_counter(key, current_window)
prev = get_counter(key, current_window - 1)
weighted = curr + prev * (1 - t)
if weighted < limit:
    increment_counter(key, current_window)
    allow()
else:
    reject()
```

**Pros**
- Smooths bursts across window boundaries.
- Still relatively cheap: only two counters per key.

**Cons**
- Approximation — not exact.
- More computation than fixed-window but still small.

**Redis tip**
Store two counters with keys like `key:window:<n>`. Use `MULTI/EXEC` or Lua for atomic read+increment.

**Complexity**
- Time: O(1).
- Space: O(#active_keys * 2).

---

## 3. Sliding Window Log (exact)

**Description**
The sliding window log records the timestamps of every request (or their bucketed timestamps). To check the rate, remove timestamps older than the sliding window and count the remaining.

**Behavior**
- For a window of length `W`, keep a list of request timestamps (or compacted counters keyed by smaller granularity).
- On new request, purge timestamps < `now - W`, then count entries.

**Pseudocode**
```text
limit = 100
W = 60
now = now_ms()
trim_list(key, now - W)
count = length(list(key))
if count < limit:
    push_tail(list(key), now)
    allow()
else:
    reject()
```

**Pros**
- Exact enforcement of `limit` over the sliding window.

**Cons**
- Memory heavy for high-rate keys (storing timestamps per request).
- Expensive to trim frequently for many clients.

**Redis tip**
Use `ZADD` with score = timestamp and `ZREMRANGEBYSCORE` to trim old entries. Use `ZCARD` for count — wrap in a Lua script for atomicity and performance.

**Complexity**
- Time: O(log N) for inserting into sorted set and O(log N + M) to trim (depends on implementation). N = number of entries in window.
- Space: O(N) per key (N ≈ limit).

---

## 4. Leaky Bucket

**Description**
Leaky Bucket enforces a constant outflow rate from a queue (the bucket). Incoming requests are enqueued; the bucket leaks at a fixed rate. If the bucket is full on arrival, requests are rejected (or queued/delayed). It smooths bursts by enforcing a steady processing rate.

**Behavior (token analogy)**
- The bucket has capacity `C` and leak rate `r` (requests per second).
- Track `water` (current fill) and `last_checked` time.
- On each request, compute `leaked = r * (now - last_checked)` and reduce `water` by `leaked` (clamp >= 0). If `water < C`, accept and `water += 1`. Else reject.

**Pseudocode**
```text
capacity = 10     # bucket size
rate = 1.0        # leaks per second (throughput)
water = 0
last = now()

on_request():
    now = now()
    elapsed = now - last
    leaked = elapsed * rate
    water = max(0, water - leaked)
    last = now
    if water < capacity:
        water += 1
        allow()
    else:
        reject()
```

**Pros**
- Smoothes bursts and enforces a steady processing rate.
- Simple and deterministic.

**Cons**
- If you want to allow short-lived bursts up to some capacity, set `capacity` accordingly.
- Implementation must ensure atomic updates in distributed settings.

**Redis tip**
Use a small Lua script storing `water` and `last` in a hash. Compute leaks, update, and return allow/reject atomically.

**Complexity**
- Time: O(1) per request.
- Space: O(1) per key.

---

## 5. Token Bucket (briefly, because people often compare it with Leaky Bucket)

**Description**
Token Bucket is similar to Leaky Bucket but tokens are added at a fixed rate up to a maximum capacity. To process a request you remove a token; if none available, reject. Allows bursts up to bucket size but enforces long-term rate.

**Relationship to Leaky Bucket**
- Leaky Bucket enforces a constant outflow; Token Bucket permits bursts while limiting average rate. They are duals and often confused.

---

## 6. Comparison and When to Use Which

- **Fixed Window**: Use when you need the simplest implementation and bursts at window edges are acceptable. Good for low-precision quotas (e.g., API key soft limits).
- **Sliding Window Counter**: A good middle-ground — small extra cost, smoother behavior.
- **Sliding Window Log**: Use when you need exact enforcement over a moving interval (e.g., security-sensitive throttling) and can handle memory cost.
- **Leaky Bucket**: Use when you want to enforce a steady processing rate (e.g., outgoing requests to a downstream service) and smooth traffic.
- **Token Bucket**: Use when you want to allow bursts while keeping a steady average.

---

## 7. Distributed considerations

- Race conditions: use atomic operations (Redis Lua scripts, transactions) to prevent incorrect counts under concurrency.
- Clock skew: prefer monotonic timestamps where possible or design tolerances if nodes use different clocks.
- Storage: choose memory-efficient structures (counters vs sorted sets) depending on QPS and number of clients.
- Sliding window log with Redis sorted sets is accurate but more storage- and CPU-intensive.

---

## 8. Example: Redis Lua for Sliding Window Log (template)

```lua
-- ARGV[1] = key
-- ARGV[2] = now_ms
-- ARGV[3] = window_ms
-- ARGV[4] = limit
local key = ARGV[1]
local now = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
local limit = tonumber(ARGV[4])
local min = now - window

redis.call('ZREMRANGEBYSCORE', key, 0, min)
local cnt = redis.call('ZCARD', key)
if cnt < limit then
  redis.call('ZADD', key, now, tostring(now))
  redis.call('PEXPIRE', key, window)
  return 1
else
  return 0
end
```

---

## 9. Quick Summary Table

| Algorithm | Accuracy | Memory | Burst control | Use case |
|---|---:|---:|---:|---|
| Fixed Window | Low | Low | Poor at boundary | Simple quotas |
| Sliding Counter | Medium | Low | Better | General-purpose |
| Sliding Log | High | High | Excellent | Accurate security throttles |
| Leaky Bucket | High (rate) | Low | Good (bounded by capacity) | Smooth downstream traffic |
| Token Bucket | High (avg) | Low | Excellent | Allow bursts with rate limit |

---

If you want, I can:
- Add concrete code samples in Go/Node/Python or a Redis module-ready implementation.
- Produce an SVG diagram showing time windows and counts.
- Add a sample test harness (load-testing) with expected results.



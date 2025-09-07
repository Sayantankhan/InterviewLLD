# Rate Limiting Algorithms

This document explains common rate-limiting algorithms you can include in your project: **Fixed Window**, **Sliding Window (counter and log variants)**, and **Leaky Bucket**. For each algorithm you'll find: a short description, behavior, pseudocode, pros/cons, complexity, and example usage patterns (including a Redis-friendly approach).

---

## 1. Fixed Window

**Description**
A simple approach that divides time into consecutive windows of fixed size (e.g., 1 minute). Each client (or key) has a counter per window. Once the counter for the current window exceeds the limit, further requests are rejected until the next window.

**Behavior**

* Time is partitioned into windows `[0..W), [W..2W), ...`.
* All requests within the same window count against the same quota.
* At window boundary the counter resets.

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

* Extremely simple to implement.
* Low memory footprint (one counter per key per window).

**Cons**

* Bursty behavior at window boundaries: a client can send `limit` requests at the end of window N and `limit` at the start of window N+1, effectively doubling allowed burst in a short time.

**Redis implementation tip**
Use `INCR` with an expiry equal to the window size. Example: `INCR key:window_start` and set TTL for `window` seconds when creating the key.

**Complexity**

* Time: O(1) per request.
* Space: O(#active\_keys).

---

## 2. Sliding Window (Counter approximation)

**Description**
Sliding Window Counter smooths the fixed-window boundary problem by keeping counters for two adjacent windows and computing a weighted sum based on how far we are into the current window.

**Behavior**

* Keep counters for current window and previous window.
* Compute weighted count: `count = current_count * (t / W) + prev_count * (1 - t / W)` where `t` is elapsed time into current window.
* This gives a linear interpolation and reduces boundary bursts.

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

* Smooths bursts across window boundaries.
* Still relatively cheap: only two counters per key.

**Cons**

* Approximation — not exact.
* More computation than fixed-window but still small.

**Redis tip**
Store two counters with keys like `key:window:<n>`. Use `MULTI/EXEC` or Lua for atomic read+increment.

**Complexity**

* Time: O(1).
* Space: O(#active\_keys \* 2).

---

## 3. Sliding Window Log (exact)

**Description**
The sliding window log records the timestamps of every request (or their bucketed timestamps). To check the rate, remove timestamps older than the sliding window and count the remaining.

**Behavior**

* For a window of length `W`, keep a list of request timestamps (or compacted counters keyed by smaller granularity).
* On new request, purge timestamps < `now - W`, then count entries.

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

* Exact enforcement of `limit` over the sliding window.

**Cons**

* Memory heavy for high-rate keys (storing timestamps per request).
* Expensive to trim frequently for many clients.

**Redis tip**
Use `ZADD` with score = timestamp and `ZREMRANGEBYSCORE` to trim old entries. Use `ZCARD` for count — wrap in a Lua script for atomicity and performance.

**Complexity**

* Time: O(log N) for inserting into sorted set and O(log N + M) to trim (depends on implementation). N = number of entries in window.
* Space: O(N) per key (N ≈ limit).

---

## 4. Leaky Bucket

**Description**
Leaky Bucket enforces a constant outflow rate from a queue (the bucket). Incoming requests are enqueued; the bucket leaks at a fixed rate. If the bucket is full on arrival, requests

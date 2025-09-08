package ratelimiter;

import java.util.*;
import java.util.concurrent.*;

public class RateLimiter {

    public interface RateLimiterInterface {
        boolean allowRequests(String userId);
    }

    public enum RateLimiterType {
        FIXED_WINDOW,
        SLIDING_WINDOW,
        LEAKY_BUCKET;
    }

    public static class FixedWindowRateLimiter implements RateLimiterInterface {

        private final int maxRequests;
        private final long windowSizeMills;
        private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
        private long windowStart;

        public FixedWindowRateLimiter(int maxRequest, long windowSizeMills) {
            this.maxRequests = maxRequest;
            this.windowSizeMills = windowSizeMills;
            this.windowStart = System.currentTimeMillis();
        }

        @Override
        public synchronized boolean allowRequests(String userId) {
            long currTime = System.currentTimeMillis();
            if(currTime - windowStart >= windowSizeMills) {
                requestCounts.remove(userId);
                windowStart = System.currentTimeMillis();
            }
            requestCounts.put(userId, requestCounts.getOrDefault(userId, 0)+1);
            return requestCounts.get(userId) <= maxRequests;
        }
    }

    public static class SlidingWindowRateLimiter implements RateLimiterInterface {

        private final int maxRequests;
        private final long windowSizeMills;
        private final Map<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();

        public SlidingWindowRateLimiter(int maxRequests, long windowSizeMills) {
            this.maxRequests = maxRequests;
            this.windowSizeMills = windowSizeMills;
        }

        @Override
        public synchronized boolean allowRequests(String userId) {
            long currTime = System.currentTimeMillis();
            requestLogs.putIfAbsent(userId, new LinkedList<>());
            Deque<Long> timestamps = requestLogs.get(userId);
            while(!timestamps.isEmpty() && currTime - timestamps.peek() >= windowSizeMills) {
                timestamps.pollFirst();
            }

            if(timestamps.size() < maxRequests) {
                timestamps.add(currTime);
                return true;
            }
            return false;
        }
    }

    public static class LeakyBucketRateLimiter implements RateLimiterInterface {
        private final int capacity;
        private final long leakRateSecs;
        private final Queue<Long> bucket = new LinkedList<>();
        private final ScheduledExecutorService scheduledExecutorService;

        public LeakyBucketRateLimiter(int capacity, long leakRateSecs) {
            this.capacity = capacity;
            this.leakRateSecs = leakRateSecs;
            this.scheduledExecutorService = Executors.newScheduledThreadPool(1);
            scheduledExecutorService.scheduleAtFixedRate(this::leakRequests,
                    0, leakRateSecs, TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized boolean allowRequests(String userId) {
            long currentTime = System.currentTimeMillis();
            if(bucket.size() < capacity) {
                bucket.offer(currentTime);
                return true;
            }
            return false;
        }

        private synchronized void leakRequests(){
            System.out.println("[INFO] Running leak Requests Reset");
            long now = System.currentTimeMillis();
            while (!bucket.isEmpty() && (now - bucket.peek()) >= this.leakRateSecs) {
                bucket.poll();
            }
        }
    }

    public class RateLimiterFactory {
        public static RateLimiterInterface createInstance(RateLimiterType type, int maxRequests, long windowSizeMills) {
            return switch (type) {
                case FIXED_WINDOW -> new FixedWindowRateLimiter(maxRequests, windowSizeMills);
                case SLIDING_WINDOW -> new SlidingWindowRateLimiter(maxRequests, windowSizeMills);
                case LEAKY_BUCKET -> new LeakyBucketRateLimiter(maxRequests, (int) windowSizeMills);
                default -> throw new IllegalArgumentException();
            };
        }
    }

    public static class RateLimiterService {
        private final Map<String, RateLimiterInterface> userRateLimiters = new ConcurrentHashMap<>();

        public void registerUser(String userId, RateLimiterType type, int maxRequests, long windowSizeMills) {
            userRateLimiters.put(userId, RateLimiterFactory.createInstance(type, maxRequests, windowSizeMills * 1000));
        }

        public boolean allowRequest(String userId) {
            RateLimiterInterface rateLimiter = userRateLimiters.get(userId);
            if(rateLimiter == null) throw new IllegalArgumentException("User is not registerd");
            return rateLimiter.allowRequests(userId);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        RateLimiterService service = new RateLimiterService();

        service.registerUser("user_1", RateLimiterType.FIXED_WINDOW, 5, 10);
        service.registerUser("user_2", RateLimiterType.SLIDING_WINDOW, 3, 5);
        service.registerUser("user_3", RateLimiterType.LEAKY_BUCKET, 4, 4);

        for (int i = 0; i < 100; i++) {
            System.out.println("User 1 Request " + (i + 1) + " : " + service.allowRequest("user_1"));
            System.out.println("User 2 Request " + (i + 1) + " : " + service.allowRequest("user_2"));
            System.out.println("User 3 Request " + (i + 1) + " : " + service.allowRequest("user_3"));
            System.out.println("=============================");
            Thread.sleep(1000);

        }

    }
}

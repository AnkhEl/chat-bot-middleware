package com.alex.chatcommercebot.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** The lock is tested with a thread-safe fake that behaves like Redis SET NX plus compare-and-delete. */
class CustomerLockTest {

    static class FakeLocks implements KeyValueStore {
        final ConcurrentHashMap<String, String> held = new ConcurrentHashMap<>();
        final AtomicInteger refusals = new AtomicInteger();
        volatile int refuseFirst;

        @Override
        public Optional<String> tryLock(String key, Duration ttl) {
            if (refuseFirst > 0 && refusals.getAndIncrement() < refuseFirst) {
                return Optional.empty();
            }
            String token = UUID.randomUUID().toString();
            return held.putIfAbsent(key, token) == null ? Optional.of(token) : Optional.empty();
        }

        @Override
        public void unlock(String key, String token) {
            held.remove(key, token);
        }

        @Override
        public boolean markFirstSeen(String key, Duration ttl) {
            return true;
        }

        @Override
        public long increment(String key, Duration ttl) {
            return 1;
        }
    }

    private final FakeLocks store = new FakeLocks();

    private CustomerLock lock(long maxWaitMillis) {
        return new CustomerLock(store, Duration.ofMillis(maxWaitMillis), Duration.ofMillis(1));
    }

    @Test
    void runsTheTaskAndReleasesTheLock() {
        AtomicInteger ran = new AtomicInteger();

        boolean result = lock(100).run("27821234567", ran::incrementAndGet);

        assertTrue(result);
        assertEquals(1, ran.get());
        assertTrue(store.held.isEmpty());
    }

    @Test
    void releasesTheLockEvenIfTheTaskThrows() {
        assertThrows(IllegalStateException.class,
                () -> lock(100).run("27821234567", () -> {
                    throw new IllegalStateException("boom");
                }));

        assertTrue(store.held.isEmpty());
    }

    @Test
    void waitsForABusyLockAndThenRuns() {
        store.refuseFirst = 5;
        AtomicInteger ran = new AtomicInteger();

        boolean result = lock(2000).run("27821234567", ran::incrementAndGet);

        assertTrue(result);
        assertEquals(1, ran.get());
    }

    @Test
    void givesUpWithoutRunningWhenTheLockStaysBusy() {
        store.held.put("wa:lock:27821234567", "someone-else");
        AtomicInteger ran = new AtomicInteger();

        boolean result = lock(30).run("27821234567", ran::incrementAndGet);

        assertFalse(result);
        assertEquals(0, ran.get());
        // Someone else's lock is left alone.
        assertEquals("someone-else", store.held.get("wa:lock:27821234567"));
    }

    @Test
    void differentCustomersDoNotBlockEachOther() {
        store.held.put("wa:lock:27820000001", "busy");
        AtomicInteger ran = new AtomicInteger();

        assertTrue(lock(30).run("27820000002", ran::incrementAndGet));
        assertEquals(1, ran.get());
    }

    @Test
    void onlyOneTaskPerCustomerRunsAtATime() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        CustomerLock lock = lock(10_000);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                results.add(pool.submit(() -> lock.run("27821234567", () -> {
                    int now = active.incrementAndGet();
                    maxActive.accumulateAndGet(now, Math::max);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    done.incrementAndGet();
                    active.decrementAndGet();
                })));
            }
            for (Future<Boolean> f : results) {
                assertTrue(f.get(20, TimeUnit.SECONDS));
            }
        }

        assertEquals(12, done.get());
        assertEquals(1, maxActive.get(), "two tasks for the same customer ran at the same time");
        assertTrue(store.held.isEmpty());
    }
}

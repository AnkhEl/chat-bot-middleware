package com.alex.chatcommercebot.conversation;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets only one message per customer be processed at a time.
 *
 * <p>Each message runs on its own thread, so two quick taps from the same phone could otherwise load the same
 * session, change it separately, and overwrite each other. Holding this lock around the whole message makes
 * them take turns. The lock lives in Redis, so it also works with more than one copy of the app.
 *
 * <p>Waiting messages poll for the lock, so strict first-come-first-served order is not guaranteed. That is fine
 * for people tapping a menu; it is not a queue.
 */
final class CustomerLock {

    private static final Logger log = LoggerFactory.getLogger(CustomerLock.class);

    /** A held lock frees itself after this long, so a crash cannot lock a customer out. */
    private static final Duration HOLD_AT_MOST = Duration.ofSeconds(30);

    private final KeyValueStore store;
    private final Duration maxWait;
    private final Duration retryEvery;

    CustomerLock(KeyValueStore store, Duration maxWait, Duration retryEvery) {
        this.store = store;
        this.maxWait = maxWait;
        this.retryEvery = retryEvery;
    }

    /**
     * Runs the task while holding this customer's lock, waiting up to {@code maxWait} for it.
     *
     * @return true if the task ran; false if the lock could not be taken in time (the task did not run)
     */
    boolean run(String phone, Runnable task) {
        String key = "wa:lock:" + phone;
        long giveUpAt = System.nanoTime() + maxWait.toNanos();

        Optional<String> token = store.tryLock(key, HOLD_AT_MOST);
        while (token.isEmpty()) {
            if (System.nanoTime() >= giveUpAt) {
                return false;
            }
            try {
                Thread.sleep(retryEvery);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            token = store.tryLock(key, HOLD_AT_MOST);
        }

        try {
            task.run();
            return true;
        } finally {
            try {
                store.unlock(key, token.get());
            } catch (RuntimeException e) {
                // The lock frees itself soon anyway; do not hide the real outcome of the task.
                log.warn("Could not release a customer lock: {}", e.getClass().getSimpleName());
            }
        }
    }
}

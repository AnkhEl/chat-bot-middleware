package com.alex.chatcommercebot.conversation;

import java.time.Duration;
import java.util.Optional;

/** The atomic operations the bot needs from Redis, so the logic can be tested without Redis. */
public interface KeyValueStore {

    /**
     * Atomically records the key if it is not present.
     *
     * @return true only for the first caller within the ttl
     */
    boolean markFirstSeen(String key, Duration ttl);

    /**
     * Atomically increments a counter; the ttl starts when the counter is created.
     *
     * @return the value after incrementing (1 for the first call)
     */
    long increment(String key, Duration ttl);

    /**
     * Takes the lock if nobody holds it. The lock frees itself after the ttl, so a crashed holder cannot block
     * anyone for ever.
     *
     * @return a token to pass to {@link #unlock}, or empty if someone else holds the lock
     */
    Optional<String> tryLock(String key, Duration ttl);

    /** Releases the lock, but only if it is still held with this token (never someone else's later lock). */
    void unlock(String key, String token);
}

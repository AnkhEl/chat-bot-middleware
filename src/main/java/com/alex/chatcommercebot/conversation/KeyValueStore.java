package com.alex.chatcommercebot.conversation;

import java.time.Duration;

/** The two atomic operations the bot needs from Redis, so the logic can be tested without Redis. */
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
}

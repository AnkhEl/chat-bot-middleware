package com.alex.chatcommercebot.store;

import java.time.Duration;

import com.alex.chatcommercebot.conversation.KeyValueStore;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisKeyValueStore implements KeyValueStore {

    private final StringRedisTemplate redis;

    RedisKeyValueStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean markFirstSeen(String key, Duration ttl) {
        // SET key 1 NX EX <ttl>: atomic, so two simultaneous deliveries cannot both win.
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        if (value == null) {
            return 0L;
        }
        if (value == 1L) {
            redis.expire(key, ttl);
        } else {
            // Self-heal if a crash once left a counter without an expiry.
            Long remaining = redis.getExpire(key);
            if (remaining != null && remaining == -1L) {
                redis.expire(key, ttl);
            }
        }
        return value;
    }
}

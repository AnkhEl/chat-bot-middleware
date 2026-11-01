package com.alex.chatcommercebot.store;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.alex.chatcommercebot.conversation.KeyValueStore;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisKeyValueStore implements KeyValueStore {

    /** Delete the key only if it still holds our token. One script, so the check and the delete cannot be split. */
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

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

    @Override
    public Optional<String> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        // SET key token NX EX <ttl>: only one caller can win.
        boolean won = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl));
        return won ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void unlock(String key, String token) {
        redis.execute(UNLOCK, List.of(key), token);
    }
}

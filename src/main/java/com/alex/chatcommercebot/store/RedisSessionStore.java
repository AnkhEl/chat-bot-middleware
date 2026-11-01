package com.alex.chatcommercebot.store;

import java.time.Duration;

import com.alex.chatcommercebot.conversation.Session;
import com.alex.chatcommercebot.conversation.SessionStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Sessions as JSON under {@code wa:session:<phone>}. Every save restarts a 2-hour idle timer, so a customer
 * who goes quiet starts again from the menu.
 */
@Component
class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);
    private static final Duration IDLE_TTL = Duration.ofHours(2);

    private final StringRedisTemplate redis;
    private final SessionCodec codec = new SessionCodec();

    RedisSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Session load(String phone) {
        String json = redis.opsForValue().get(key(phone));
        if (json == null) {
            return Session.empty();
        }
        return codec.fromJson(json).orElseGet(() -> {
            // Never log the stored text: it can contain what the customer ordered.
            log.warn("Unreadable session discarded");
            return Session.empty();
        });
    }

    @Override
    public void save(String phone, Session session) {
        redis.opsForValue().set(key(phone), codec.toJson(session), IDLE_TTL);
    }

    @Override
    public void clear(String phone) {
        redis.delete(key(phone));
    }

    private static String key(String phone) {
        return "wa:session:" + phone;
    }
}

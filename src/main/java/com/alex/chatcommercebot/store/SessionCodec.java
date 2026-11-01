package com.alex.chatcommercebot.store;

import java.util.Optional;

import com.alex.chatcommercebot.conversation.Session;

import tools.jackson.databind.json.JsonMapper;

/** Turns a {@link Session} into JSON and back. Separate from Redis so it can be tested on its own. */
final class SessionCodec {

    private final JsonMapper mapper = JsonMapper.builder().build();

    String toJson(Session session) {
        return mapper.writeValueAsString(session);
    }

    /** Empty if the text is not a valid session (damaged, or written by a different version). */
    Optional<Session> fromJson(String json) {
        try {
            return Optional.ofNullable(mapper.readValue(json, Session.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}

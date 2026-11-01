package com.alex.chatcommercebot.conversation;

/** Keeps each customer's {@link Session} between messages. The Redis version expires idle sessions. */
public interface SessionStore {

    /**
     * @param phone customer number, digits only
     * @return the stored session, or a new empty one if there is none, it expired, or it cannot be read
     */
    Session load(String phone);

    /** Stores the session and restarts its idle timer. */
    void save(String phone, Session session);

    /** Forgets the session (for example after an order is placed). */
    void clear(String phone);
}

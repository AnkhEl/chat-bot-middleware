package com.alex.chatcommercebot.conversation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What the bot remembers about one customer between messages: where they are in the flow and what is in
 * their cart. Immutable; change it by building a new one. Stored as JSON in Redis for a limited time
 * ({@link SessionStore}), so losing it only means the customer starts again from the menu.
 *
 * <p>The cart lives here, not in Magento, until checkout: nothing is reserved or written to Magento
 * until the customer confirms the order.
 *
 * @param state where the customer is in the flow
 * @param cart  the items so far (never null)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Session(State state, List<CartLine> cart) {

    /** Where the customer is. More states (entering items, confirming) arrive with the steps that need them. */
    public enum State {
        IDLE
    }

    public Session {
        state = state == null ? State.IDLE : state;
        cart = cart == null ? List.of() : List.copyOf(cart);
    }

    public static Session empty() {
        return new Session(State.IDLE, List.of());
    }

    /** Derived from the cart, so it is not stored. */
    @JsonIgnore
    public boolean isCartEmpty() {
        return cart.isEmpty();
    }
}

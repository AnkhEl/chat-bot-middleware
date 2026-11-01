package com.alex.chatcommercebot.conversation;

/**
 * A message received from a customer, already stripped of provider-specific JSON.
 *
 * @param messageId provider message id, used to drop duplicate webhook deliveries
 * @param from      sender number as sent by the provider (digits, e.g. 27821234567)
 * @param text      message text; for a tap on a list row, the row's title
 * @param replyId   id of the list row the customer tapped, or null for a typed message
 */
public record InboundMessage(String messageId, String from, String text, String replyId) {

    /** A typed text message. */
    public InboundMessage(String messageId, String from, String text) {
        this(messageId, from, text, null);
    }

    public boolean isReply() {
        return replyId != null && !replyId.isBlank();
    }
}

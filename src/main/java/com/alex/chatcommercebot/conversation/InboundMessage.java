package com.alex.chatcommercebot.conversation;

/**
 * A text message received from a customer, already stripped of provider-specific JSON.
 *
 * @param messageId provider message id, used to drop duplicate webhook deliveries
 * @param from      sender number as sent by the provider (digits, e.g. 27821234567)
 * @param text      message text
 */
public record InboundMessage(String messageId, String from, String text) {
}

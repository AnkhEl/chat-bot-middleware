package com.alex.chatcommercebot.infobip;

import java.util.List;

import com.alex.chatcommercebot.conversation.InboundMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Infobip's inbound WhatsApp webhook body. Only the fields we use are mapped; everything else is ignored.
 * Step 3 reads plain text messages only. Interactive replies (list / button taps) arrive with other
 * message types and are added together with the menu.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfobipInbound(List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String from, String messageId, Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String type, String text) {
    }

    /** The text messages in this delivery, as provider-neutral objects. */
    public List<InboundMessage> toInboundMessages() {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .filter(r -> r != null && r.message() != null)
                .filter(r -> "TEXT".equalsIgnoreCase(r.message().type()))
                .filter(r -> notBlank(r.messageId()) && notBlank(r.from()) && notBlank(r.message().text()))
                .map(r -> new InboundMessage(r.messageId(), r.from(), r.message().text()))
                .toList();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

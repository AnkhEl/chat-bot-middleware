package com.alex.chatcommercebot.infobip;

import java.util.List;
import java.util.Optional;

import com.alex.chatcommercebot.conversation.InboundMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Infobip's inbound WhatsApp webhook body. Only the fields we use are mapped; everything else is ignored.
 *
 * <p>Two message types are understood:
 * <ul>
 *   <li>{@code TEXT}: {@code message.text}</li>
 *   <li>{@code INTERACTIVE_LIST_REPLY}: the customer tapped a list row; {@code message.id} is the row id
 *       and {@code message.title} its title.</li>
 * </ul>
 * Anything else (images, locations, reactions) is ignored for now.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfobipInbound(List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String from, String messageId, Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String type, String text, String id, String title) {
    }

    /** The messages in this delivery that we understand, as provider-neutral objects. */
    public List<InboundMessage> toInboundMessages() {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .filter(r -> r != null && r.message() != null)
                .filter(r -> notBlank(r.messageId()) && notBlank(r.from()))
                .map(InfobipInbound::convert)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<InboundMessage> convert(Result r) {
        Message m = r.message();
        if ("TEXT".equalsIgnoreCase(m.type()) && notBlank(m.text())) {
            return Optional.of(new InboundMessage(r.messageId(), r.from(), m.text()));
        }
        if ("INTERACTIVE_LIST_REPLY".equalsIgnoreCase(m.type()) && notBlank(m.id())) {
            return Optional.of(new InboundMessage(r.messageId(), r.from(), m.title(), m.id()));
        }
        return Optional.empty();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

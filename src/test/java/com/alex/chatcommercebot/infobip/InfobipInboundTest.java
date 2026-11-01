package com.alex.chatcommercebot.infobip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.alex.chatcommercebot.conversation.InboundMessage;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Parses payloads shaped like the ones Infobip sends, then maps them to our own message type. */
class InfobipInboundTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static List<InboundMessage> parse(String json) {
        return MAPPER.readValue(json, InfobipInbound.class).toInboundMessages();
    }

    @Test
    void textMessageIsMapped() {
        List<InboundMessage> messages = parse("""
                {"results":[{"from":"27821234567","to":"447860099299","messageId":"m1",
                 "message":{"type":"TEXT","text":"hi"},"contact":{"name":"Alex"}}],
                 "messageCount":1,"pendingMessageCount":0}""");

        assertEquals(1, messages.size());
        assertEquals("hi", messages.get(0).text());
        assertFalse(messages.get(0).isReply());
    }

    @Test
    void listTapIsMappedToAReplyWithTheRowId() {
        List<InboundMessage> messages = parse("""
                {"results":[{"from":"27821234567","to":"447860099299","messageId":"m2",
                 "pairedMessageId":"out-1",
                 "message":{"id":"menu_help","title":"Help","description":"How it works",
                            "type":"INTERACTIVE_LIST_REPLY","context":{"from":"447860099299","id":"out-1"}}}],
                 "messageCount":1,"pendingMessageCount":0}""");

        assertEquals(1, messages.size());
        InboundMessage tap = messages.get(0);
        assertTrue(tap.isReply());
        assertEquals("menu_help", tap.replyId());
        assertEquals("m2", tap.messageId());
        assertEquals("27821234567", tap.from());
    }

    @Test
    void typesWeDoNotHandleAreIgnored() {
        List<InboundMessage> messages = parse("""
                {"results":[
                  {"from":"27821234567","messageId":"a","message":{"type":"IMAGE"}},
                  {"from":"27821234567","messageId":"b","message":{"type":"LOCATION"}},
                  {"from":"27821234567","messageId":"c","message":{"type":"INTERACTIVE_LIST_REPLY"}},
                  {"from":"27821234567","messageId":"d","message":{"type":"TEXT","text":"  "}},
                  {"from":"27821234567","messageId":"e"}]}""");

        assertTrue(messages.isEmpty());
    }

    @Test
    void aMixedDeliveryKeepsEveryUnderstoodMessage() {
        List<InboundMessage> messages = parse("""
                {"results":[
                  {"from":"27821234567","messageId":"a","message":{"type":"TEXT","text":"hi"}},
                  {"from":"27821234567","messageId":"b","message":{"type":"IMAGE"}},
                  {"from":"27821234567","messageId":"c","message":{"type":"INTERACTIVE_LIST_REPLY","id":"menu_cart","title":"View cart"}}]}""");

        assertEquals(List.of("a", "c"), messages.stream().map(InboundMessage::messageId).toList());
    }

    @Test
    void emptyOrMissingResultsGiveNothing() {
        assertTrue(parse("{}").isEmpty());
        assertTrue(parse("{\"results\":[]}").isEmpty());
    }
}

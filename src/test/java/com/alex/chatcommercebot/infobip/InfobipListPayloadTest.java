package com.alex.chatcommercebot.infobip;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.alex.chatcommercebot.conversation.ListMessage;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Checks the JSON we send to Infobip has the shape their interactive-list endpoint expects. */
class InfobipListPayloadTest {

    @Test
    void listIsSerialisedInInfobipsShape() {
        ListMessage list = new ListMessage("Hi Thabo!", "Open menu", "Main menu",
                List.of(new ListMessage.Row("menu_help", "Help", "How it works"),
                        new ListMessage.Row("menu_x", "No description", null)));
        InfobipMessageSender.ListContent content = new InfobipMessageSender.ListContent(
                new InfobipMessageSender.Content(list.body()),
                new InfobipMessageSender.Action(list.buttonText(),
                        List.of(new InfobipMessageSender.Section(list.sectionTitle(), list.rows()))));

        String json = JsonMapper.builder().build()
                .writeValueAsString(new InfobipMessageSender.SendList("27600000000", "27821234567", content));

        assertTrue(json.contains("\"from\":\"27600000000\""), json);
        assertTrue(json.contains("\"to\":\"27821234567\""), json);
        assertTrue(json.contains("\"body\":{\"text\":\"Hi Thabo!\"}"), json);
        assertTrue(json.contains("\"action\":{\"title\":\"Open menu\""), json);
        assertTrue(json.contains("\"sections\":[{\"title\":\"Main menu\""), json);
        assertTrue(json.contains("\"id\":\"menu_help\""), json);
        assertTrue(json.contains("\"description\":\"How it works\""), json);
        // A row without a description must leave the field out, not send null.
        assertFalse(json.contains("\"description\":null"), json);
    }
}

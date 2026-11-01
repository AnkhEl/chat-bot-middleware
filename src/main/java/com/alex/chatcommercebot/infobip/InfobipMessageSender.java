package com.alex.chatcommercebot.infobip;

import com.alex.chatcommercebot.config.AppProperties;
import com.alex.chatcommercebot.config.ClientFactory;
import com.alex.chatcommercebot.conversation.ListMessage;
import com.alex.chatcommercebot.conversation.MessageSender;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends free-form text and interactive lists through Infobip's WhatsApp API:
 * POST /whatsapp/1/message/text and POST /whatsapp/1/message/interactive/list.
 * Free-form messages are only allowed inside the 24-hour window after the customer's last message,
 * which is always the case here because every send is a reply.
 */
@Component
class InfobipMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(InfobipMessageSender.class);

    private final RestClient client;
    private final String senderNumber;

    InfobipMessageSender(AppProperties props) {
        this.client = ClientFactory.infobip(props.infobip());
        this.senderNumber = props.infobip().sender();
    }

    @Override
    public void sendText(String to, String text) {
        post("/whatsapp/1/message/text", new SendText(senderNumber, to, new Content(text)));
    }

    @Override
    public void sendList(String to, ListMessage list) {
        ListContent content = new ListContent(
                new Content(list.body()),
                new Action(list.buttonText(), List.of(new Section(list.sectionTitle(), list.rows()))));
        post("/whatsapp/1/message/interactive/list", new SendList(senderNumber, to, content));
    }

    private void post(String path, Object body) {
        try {
            client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Status only: the body can echo the customer's number or message.
            log.error("Infobip rejected a message (HTTP {})", e.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("Could not reach Infobip: {}", e.getMostSpecificCause().getClass().getSimpleName());
        }
    }

    record SendText(String from, String to, Content content) {
    }

    record Content(String text) {
    }

    record SendList(String from, String to, ListContent content) {
    }

    /** Infobip names the main text "body.text"; the button and sections sit under "action". */
    record ListContent(Content body, Action action) {
    }

    record Action(String title, List<Section> sections) {
    }

    record Section(String title, List<ListMessage.Row> rows) {
    }
}

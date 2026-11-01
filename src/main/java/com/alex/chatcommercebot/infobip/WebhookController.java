package com.alex.chatcommercebot.infobip;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.alex.chatcommercebot.config.AppProperties;
import com.alex.chatcommercebot.conversation.ConversationService;
import com.alex.chatcommercebot.conversation.InboundMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Infobip's inbound-message webhook and acknowledges it immediately; the actual work runs on a
 * virtual thread so Infobip never waits on Magento.
 *
 * <p>The shared secret travels in the URL path ({@code /webhooks/whatsapp/<secret>}). That works
 * with any webhook configuration; if your Infobip setup supports a request header or signature, prefer that.
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final byte[] secret;
    private final ConversationService conversations;
    private final ExecutorService executor;

    public WebhookController(AppProperties props, ConversationService conversations,
                             @Qualifier("webhookExecutor") ExecutorService executor) {
        this.secret = props.webhookSecret().getBytes(StandardCharsets.UTF_8);
        this.conversations = conversations;
        this.executor = executor;
    }

    @PostMapping("/{token}")
    public ResponseEntity<Void> receive(@PathVariable String token, @RequestBody InfobipInbound body) {
        // Constant-time comparison, so response timing reveals nothing about the secret.
        if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), secret)) {
            return ResponseEntity.status(401).build();
        }

        int received = body.results() == null ? 0 : body.results().size();
        List<InboundMessage> messages = body.toInboundMessages();
        log.info("Webhook received: {} result(s), {} text message(s)", received, messages.size());

        for (InboundMessage message : messages) {
            executor.submit(() -> handleSafely(message));
        }
        return ResponseEntity.ok().build();
    }

    private void handleSafely(InboundMessage message) {
        try {
            conversations.handle(message);
        } catch (RuntimeException e) {
            // Never lets one bad message take down the worker; the stack trace has no customer data.
            log.error("Failed to handle message {}", message.messageId(), e);
        }
    }
}

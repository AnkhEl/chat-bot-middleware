package com.alex.chatcommercebot.conversation;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles one inbound customer message.
 *
 * <p>Scope so far: phone verification ("VERIFY 482913"), the verified / not-verified gate, and the
 * main menu shown to verified customers.
 *
 * <p>This class deliberately has no Spring imports, so it is unit-tested without a container.
 * It is safe to call twice with the same message id: the second call is ignored.
 */
public final class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    /** "VERIFY 123456", also tolerating case, a colon, extra spaces and a trailing full stop. */
    private static final Pattern VERIFY =
            Pattern.compile("^\\s*verify[\\s:]+(\\d{6})\\s*[.!]?\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Duration DEDUPE_TTL = Duration.ofHours(48);
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final Duration UNKNOWN_REPLY_COOLDOWN = Duration.ofMinutes(1);

    /**
     * @param storefrontUrl     public store URL, used in the "please verify" reply
     * @param messagesPerMinute messages handled per sender per minute; the rest are ignored
     */
    public record Settings(String storefrontUrl, int messagesPerMinute) {
    }

    private final MagentoGateway magento;
    private final MessageSender sender;
    private final KeyValueStore store;
    private final Settings settings;

    public ConversationService(MagentoGateway magento, MessageSender sender, KeyValueStore store, Settings settings) {
        this.magento = magento;
        this.sender = sender;
        this.store = store;
        this.settings = settings;
    }

    public void handle(InboundMessage message) {
        if (message == null || isBlank(message.messageId()) || isBlank(message.from())) {
            return;
        }

        // The provider retries webhooks, so the same message can arrive more than once.
        if (!store.markFirstSeen("wa:msg:" + message.messageId(), DEDUPE_TTL)) {
            log.debug("Ignoring duplicate message {}", message.messageId());
            return;
        }

        String phone = message.from().replaceAll("\\D", "");
        if (phone.isEmpty()) {
            return;
        }

        // Stops a single number from making us hammer Magento or send unlimited replies.
        if (store.increment("wa:rate:" + phone, RATE_WINDOW) > settings.messagesPerMinute()) {
            log.warn("Rate limit exceeded for {}; message ignored", Redact.phone(phone));
            return;
        }

        String text = message.text() == null ? "" : message.text().trim();
        Matcher verify = VERIFY.matcher(text);

        try {
            if (verify.matches()) {
                handleVerify(phone, verify.group(1));
            } else {
                handleOther(phone);
            }
        } catch (MagentoUnavailableException e) {
            log.error("Magento unavailable while handling a message from {}: {}", Redact.phone(phone), e.getMessage());
            sender.sendText(phone, Messages.TEMPORARY_PROBLEM);
        }
    }

    private void handleVerify(String phone, String code) {
        // The code itself is never logged.
        MagentoGateway.VerifyResult result = magento.verify(phone, code);

        if (result.success()) {
            log.info("Number {} verified", Redact.phone(phone));
            sender.sendText(phone, Messages.verified(result.firstName()));
        } else {
            log.info("Verification failed for {}", Redact.phone(phone));
            sender.sendText(phone, Messages.VERIFY_FAILED);
        }
    }

    private void handleOther(String phone) {
        MagentoGateway.LookupResult customer = magento.lookup(phone);

        if (!customer.found() || !customer.phoneVerified()) {
            // Strangers get at most one reply a minute, so nobody can use us to spam.
            if (store.markFirstSeen("wa:unknown:" + phone, UNKNOWN_REPLY_COOLDOWN)) {
                sender.sendText(phone, Messages.notVerified(settings.storefrontUrl()));
            }
            return;
        }

        sender.sendList(phone, Menu.forCustomer(customer.firstName()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

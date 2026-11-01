package com.alex.chatcommercebot.conversation;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles one inbound customer message.
 *
 * <p>Scope so far: phone verification ("VERIFY 482913"), the verified / not-verified gate, and the
 * main menu shown to verified customers, taps on the menu rows, and the per-customer session.
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
    private final SessionStore sessions;
    private final Settings settings;

    public ConversationService(MagentoGateway magento, MessageSender sender, KeyValueStore store,
                               SessionStore sessions, Settings settings) {
        this.magento = magento;
        this.sender = sender;
        this.store = store;
        this.sessions = sessions;
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
            if (message.isReply()) {
                handleReply(phone, message.replyId());
            } else if (verify.matches()) {
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
        MagentoGateway.LookupResult customer = verifiedCustomer(phone);
        if (customer == null) {
            return;
        }
        touchSession(phone);
        sender.sendList(phone, Menu.forCustomer(customer.firstName()));
    }

    /** The customer tapped a row of the menu. */
    private void handleReply(String phone, String replyId) {
        MagentoGateway.LookupResult customer = verifiedCustomer(phone);
        if (customer == null) {
            return;
        }

        Session session = touchSession(phone);

        Optional<MenuAction> action = MenuAction.fromId(replyId);
        if (action.isEmpty()) {
            // Not one of our rows (an old menu, or a forged message): just show the menu again.
            log.info("Unknown menu reply from {}", Redact.phone(phone));
            sender.sendList(phone, Menu.forCustomer(customer.firstName()));
            return;
        }

        log.info("Menu choice {} from {}", action.get(), Redact.phone(phone));
        switch (action.get()) {
            case HELP -> sender.sendText(phone, Messages.HELP);
            case VIEW_CART -> sender.sendText(phone, Messages.cart(session));
            // The other rows are built in the next steps.
            default -> sender.sendText(phone, Messages.comingSoon(action.get().title()));
        }
    }

    /** Loads the customer's session and saves it straight back, which restarts its idle timer. */
    private Session touchSession(String phone) {
        Session session = sessions.load(phone);
        sessions.save(phone, session);
        return session;
    }

    /**
     * The customer for this number if it is verified. Otherwise the stranger gets at most one
     * "please verify" reply a minute and this returns null.
     */
    private MagentoGateway.LookupResult verifiedCustomer(String phone) {
        MagentoGateway.LookupResult customer = magento.lookup(phone);

        if (!customer.found() || !customer.phoneVerified()) {
            // Strangers get at most one reply a minute, so nobody can use us to spam.
            if (store.markFirstSeen("wa:unknown:" + phone, UNKNOWN_REPLY_COOLDOWN)) {
                sender.sendText(phone, Messages.notVerified(settings.storefrontUrl()));
            }
            return null;
        }
        return customer;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

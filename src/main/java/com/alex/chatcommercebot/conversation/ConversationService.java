package com.alex.chatcommercebot.conversation;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles one inbound customer message.
 *
 * <p>Scope so far: phone verification ("VERIFY 482913"), the verified / not-verified gate, the main menu,
 * taps on its rows, a per-customer session, and the first conversation state (entering items by SKU).
 *
 * <p>The conversation is a plain state machine: the session's {@link Session.State} decides what the
 * customer's next message means, and each handler returns the session to keep.
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
    private final CustomerLock lock;

    public ConversationService(MagentoGateway magento, MessageSender sender, KeyValueStore store,
                               SessionStore sessions, Settings settings) {
        this(magento, sender, store, sessions, settings,
                new CustomerLock(store, Duration.ofSeconds(10), Duration.ofMillis(50)));
    }

    /** For tests, which need a lock that gives up quickly. */
    ConversationService(MagentoGateway magento, MessageSender sender, KeyValueStore store,
                        SessionStore sessions, Settings settings, CustomerLock lock) {
        this.magento = magento;
        this.sender = sender;
        this.store = store;
        this.sessions = sessions;
        this.settings = settings;
        this.lock = lock;
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

        // One message per customer at a time, so quick taps cannot overwrite each other's session changes.
        boolean ran = lock.run(phone, () -> process(phone, message));
        if (!ran) {
            log.warn("Customer {} was busy for too long; message dropped", Redact.phone(phone));
        }
    }

    private void process(String phone, InboundMessage message) {
        String text = message.text() == null ? "" : message.text().trim();
        Matcher verify = VERIFY.matcher(text);

        try {
            if (!message.isReply() && verify.matches()) {
                handleVerify(phone, verify.group(1));
                return;
            }

            MagentoGateway.LookupResult customer = verifiedCustomer(phone);
            if (customer == null) {
                return;
            }

            // What the customer's message means depends on the session: where they are and what they are doing.
            // Each handler returns the session to keep; saving it also restarts the 2-hour idle timer.
            Session session = sessions.load(phone);
            Session next = message.isReply()
                    ? handleReply(phone, customer, session, message.replyId())
                    : handleText(phone, customer, session, text);
            sessions.save(phone, next);
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

    /** The customer typed something (not a VERIFY code). */
    private Session handleText(String phone, MagentoGateway.LookupResult customer, Session session, String text) {
        return switch (session.state()) {
            case ENTERING_ITEMS -> handleItemEntry(phone, customer, session, text);
            case IDLE -> {
                sender.sendList(phone, Menu.forCustomer(customer.firstName()));
                yield session;
            }
        };
    }

    /** The customer is typing product codes and quantities. */
    private Session handleItemEntry(String phone, MagentoGateway.LookupResult customer, Session session, String text) {
        String word = text.replaceAll("[.!\\s]+$", "").toLowerCase(Locale.ROOT);
        switch (word) {
            case "menu", "cancel" -> {
                sender.sendList(phone, Menu.forCustomer(customer.firstName()));
                return session.withState(Session.State.IDLE);
            }
            case "done" -> {
                sender.sendText(phone, Messages.cart(session));
                return session.withState(Session.State.IDLE);
            }
            default -> {
                // Not a command: read it as items.
            }
        }

        OrderLineParser.Result parsed = OrderLineParser.parse(text);
        if (parsed.tooMany()) {
            sender.sendText(phone, Messages.TOO_MANY_ITEMS);
        } else if (parsed.lines().isEmpty()) {
            sender.sendText(phone, Messages.itemsNotUnderstood(parsed.unreadable()));
        } else {
            // Next step: check these SKUs with Magento and add them to the cart. For now just show what was read.
            sender.sendText(phone, Messages.itemsRead(parsed.lines(), parsed.unreadable()));
        }
        return session;
    }

    /** The customer tapped a row of the menu. Tapping any row also ends item entry. */
    private Session handleReply(String phone, MagentoGateway.LookupResult customer, Session session, String replyId) {
        Session idle = session.withState(Session.State.IDLE);

        Optional<MenuAction> action = MenuAction.fromId(replyId);
        if (action.isEmpty()) {
            // Not one of our rows (an old menu, or a forged message): just show the menu again.
            log.info("Unknown menu reply from {}", Redact.phone(phone));
            sender.sendList(phone, Menu.forCustomer(customer.firstName()));
            return idle;
        }

        log.info("Menu choice {} from {}", action.get(), Redact.phone(phone));
        return switch (action.get()) {
            case HELP -> {
                sender.sendText(phone, Messages.HELP);
                yield idle;
            }
            case VIEW_CART -> {
                sender.sendText(phone, Messages.cart(session));
                yield idle;
            }
            case ENTER_SKUS -> {
                sender.sendText(phone, Messages.ENTER_ITEMS_PROMPT);
                yield session.withState(Session.State.ENTERING_ITEMS);
            }
            // The other rows are built in the next steps.
            default -> {
                sender.sendText(phone, Messages.comingSoon(action.get().title()));
                yield idle;
            }
        };
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

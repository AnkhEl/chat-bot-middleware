package com.alex.chatcommercebot.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring, no Redis, no network. */
class ConversationServiceTest {

    private static final String PHONE = "27821234567";

    // ---- fakes ---------------------------------------------------------------------------------

    static class FakeMagento implements MagentoGateway {
        VerifyResult verifyResult = new VerifyResult(false, null);
        LookupResult lookupResult = LookupResult.notFound();
        boolean down;
        final List<String> verifyCalls = new ArrayList<>();
        int lookupCalls;

        @Override
        public VerifyResult verify(String phone, String code) {
            if (down) {
                throw new MagentoUnavailableException("down");
            }
            verifyCalls.add(phone + "/" + code);
            return verifyResult;
        }

        @Override
        public LookupResult lookup(String phone) {
            if (down) {
                throw new MagentoUnavailableException("down");
            }
            lookupCalls++;
            return lookupResult;
        }
    }

    static class RecordingSender implements MessageSender {
        final List<String> sent = new ArrayList<>();
        final List<ListMessage> lists = new ArrayList<>();

        @Override
        public void sendText(String to, String text) {
            sent.add(to + "|" + text);
        }

        @Override
        public void sendList(String to, ListMessage list) {
            sent.add(to + "|[list] " + list.body());
            lists.add(list);
        }

        String last() {
            return sent.get(sent.size() - 1);
        }
    }

    static class InMemoryStore implements KeyValueStore {
        final Map<String, Long> counters = new HashMap<>();

        @Override
        public boolean markFirstSeen(String key, Duration ttl) {
            return counters.putIfAbsent(key, 1L) == null;
        }

        @Override
        public long increment(String key, Duration ttl) {
            return counters.merge(key, 1L, Long::sum);
        }
    }

    private final FakeMagento magento = new FakeMagento();
    private final RecordingSender sender = new RecordingSender();
    private final InMemoryStore store = new InMemoryStore();

    private ConversationService service(int messagesPerMinute) {
        return new ConversationService(magento, sender, store,
                new ConversationService.Settings("https://shop.example/", messagesPerMinute));
    }

    private static InboundMessage msg(String id, String from, String text) {
        return new InboundMessage(id, from, text);
    }

    // ---- tests ---------------------------------------------------------------------------------

    @Test
    void unknownNumberSayingHiGetsOneNotVerifiedReplyPerMinute() {
        ConversationService service = service(20);

        service.handle(msg("m1", PHONE, "hi"));
        service.handle(msg("m2", PHONE, "hello again"));

        assertEquals(1, sender.sent.size());
        assertTrue(sender.last().startsWith(PHONE + "|Hi! We don't recognise this number yet."));
        assertTrue(sender.last().contains("https://shop.example/phone/account/"));
    }

    @Test
    void numberSavedButUnverifiedIsTreatedLikeAStranger() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, false, "Thabo");

        service(20).handle(msg("m1", PHONE, "menu"));

        assertTrue(sender.last().contains("don't recognise this number"));
    }

    @Test
    void verifiedNumberGetsTheMenuWithTheirName() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, true, "Thabo");

        service(20).handle(msg("m1", "+27 82 123 4567", "hi"));

        assertEquals(1, sender.sent.size());
        assertTrue(sender.last().startsWith(PHONE + "|[list] Hi Thabo!"));
        ListMessage menu = sender.lists.get(0);
        assertEquals(6, menu.rows().size());
        assertEquals("menu_order_again", menu.rows().get(0).id());
    }

    @Test
    void menuRowIdsAreUnique() {
        long distinct = Menu.forCustomer(null).rows().stream().map(ListMessage.Row::id).distinct().count();

        assertEquals(MenuAction.values().length, distinct);
    }

    @Test
    void listMessageRejectsValuesWhatsAppWouldRefuse() {
        ListMessage.Row ok = new ListMessage.Row("a", "A", null);

        assertThrows(IllegalArgumentException.class, () -> new ListMessage("b", "x".repeat(21), "S", List.of(ok)));
        assertThrows(IllegalArgumentException.class, () -> new ListMessage("b", "Open", "S", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ListMessage("b", "Open", "S", java.util.Collections.nCopies(11, ok)));
        assertThrows(IllegalArgumentException.class, () -> new ListMessage.Row("a", "x".repeat(25), null));
        assertThrows(IllegalArgumentException.class, () -> new ListMessage.Row("a", "T", "x".repeat(73)));
    }

    private static InboundMessage tap(String id, String from, String rowId) {
        return new InboundMessage(id, from, "ignored title", rowId);
    }

    @Test
    void tappingHelpSendsTheHelpText() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, true, "Thabo");

        service(20).handle(tap("t1", PHONE, MenuAction.HELP.id()));

        assertEquals(PHONE + "|" + Messages.HELP, sender.last());
    }

    @Test
    void tappingARowThatIsNotBuiltYetSaysSoByName() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, true, "Thabo");

        service(20).handle(tap("t1", PHONE, MenuAction.BROWSE.id()));

        assertTrue(sender.last().contains("\"Browse categories\" is not ready yet"));
    }

    @Test
    void everyMenuRowIsHandled() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, true, "Thabo");
        ConversationService service = service(100);

        int i = 0;
        for (MenuAction action : MenuAction.values()) {
            service.handle(tap("t" + i++, PHONE, action.id()));
        }

        assertEquals(MenuAction.values().length, sender.sent.size());
        assertTrue(sender.lists.isEmpty());
    }

    @Test
    void anUnknownRowIdShowsTheMenuAgain() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, true, "Thabo");

        service(20).handle(tap("t1", PHONE, "some_old_row"));

        assertEquals(1, sender.lists.size());
    }

    @Test
    void aTapFromAnUnverifiedNumberIsTreatedLikeAStranger() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, false, "Thabo");

        service(20).handle(tap("t1", PHONE, MenuAction.HELP.id()));

        assertTrue(sender.last().contains("don't recognise this number"));
        assertTrue(sender.lists.isEmpty());
    }

    @Test
    void theSameTapDeliveredTwiceIsHandledOnce() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, true, "Thabo");
        ConversationService service = service(20);

        service.handle(tap("same", PHONE, MenuAction.HELP.id()));
        service.handle(tap("same", PHONE, MenuAction.HELP.id()));

        assertEquals(1, sender.sent.size());
    }

    @Test
    void menuActionLookupById() {
        assertEquals(MenuAction.SEARCH, MenuAction.fromId("menu_search").orElseThrow());
        assertTrue(MenuAction.fromId("nope").isEmpty());
        assertTrue(MenuAction.fromId(null).isEmpty());
    }

    @Test
    void correctVerifyCodeConfirmsAndSendsDigitsOnlyToMagento() {
        magento.verifyResult = new MagentoGateway.VerifyResult(true, "Thabo");

        service(20).handle(msg("m1", "+" + PHONE, "VERIFY 482913"));

        assertEquals(List.of(PHONE + "/482913"), magento.verifyCalls);
        assertTrue(sender.last().contains("Thank you Thabo"));
        assertTrue(sender.last().contains("your number is verified"));
    }

    @Test
    void wrongCodeGetsTheGenericFailureMessage() {
        magento.verifyResult = new MagentoGateway.VerifyResult(false, null);

        service(20).handle(msg("m1", PHONE, "verify 111111"));

        assertEquals(PHONE + "|" + Messages.VERIFY_FAILED, sender.last());
    }

    @Test
    void duplicateWebhookDeliveryIsHandledOnce() {
        magento.verifyResult = new MagentoGateway.VerifyResult(true, "Thabo");
        ConversationService service = service(20);

        service.handle(msg("same-id", PHONE, "VERIFY 482913"));
        service.handle(msg("same-id", PHONE, "VERIFY 482913"));

        assertEquals(1, magento.verifyCalls.size());
        assertEquals(1, sender.sent.size());
    }

    @Test
    void verifyPatternAcceptsReasonableVariantsOnly() {
        magento.verifyResult = new MagentoGateway.VerifyResult(true, "A");
        ConversationService service = service(100);

        service.handle(msg("1", PHONE, "verify 123456"));
        service.handle(msg("2", PHONE, "  VERIFY: 123456  "));
        service.handle(msg("3", PHONE, "Verify 123456."));
        assertEquals(3, magento.verifyCalls.size());

        service.handle(msg("4", PHONE, "VERIFY 12345"));      // 5 digits
        service.handle(msg("5", PHONE, "VERIFY 1234567"));    // 7 digits
        service.handle(msg("6", PHONE, "please VERIFY 123456")); // extra words
        service.handle(msg("7", PHONE, "VERIFY"));            // no code
        assertEquals(3, magento.verifyCalls.size());
    }

    @Test
    void senderIsRateLimitedPerMinute() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, true, "Thabo");
        ConversationService service = service(3);

        for (int i = 1; i <= 6; i++) {
            service.handle(msg("m" + i, PHONE, "hi"));
        }

        assertEquals(3, sender.sent.size());
        assertEquals(3, magento.lookupCalls);
    }

    @Test
    void differentSendersHaveSeparateRateLimits() {
        magento.lookupResult = new MagentoGateway.LookupResult(true, true, "X");
        ConversationService service = service(1);

        service.handle(msg("a1", "27820000001", "hi"));
        service.handle(msg("b1", "27820000002", "hi"));

        assertEquals(2, sender.sent.size());
    }

    @Test
    void whenMagentoIsDownTheCustomerGetsATemporaryProblemMessage() {
        magento.down = true;

        service(20).handle(msg("m1", PHONE, "VERIFY 482913"));

        assertEquals(PHONE + "|" + Messages.TEMPORARY_PROBLEM, sender.last());
    }

    @Test
    void incompleteMessagesAreIgnored() {
        ConversationService service = service(20);

        service.handle(null);
        service.handle(msg(null, PHONE, "hi"));
        service.handle(msg("m1", null, "hi"));
        service.handle(msg("m2", "no digits", "hi"));

        assertTrue(sender.sent.isEmpty());
        assertEquals(0, magento.lookupCalls);
    }

    @Test
    void redactKeepsOnlyTheLastFourDigits() {
        assertEquals("*******4567", Redact.phone(PHONE));
        assertEquals("***", Redact.phone("123"));
        assertEquals("***", Redact.phone(null));
    }
}

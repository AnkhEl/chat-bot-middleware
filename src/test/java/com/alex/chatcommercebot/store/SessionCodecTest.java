package com.alex.chatcommercebot.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.alex.chatcommercebot.conversation.CartLine;
import com.alex.chatcommercebot.conversation.Session;

import org.junit.jupiter.api.Test;

class SessionCodecTest {

    private final SessionCodec codec = new SessionCodec();

    @Test
    void aSessionSurvivesAJsonRoundTrip() {
        Session original = new Session(Session.State.IDLE, List.of(new CartLine("SKU-1", 2), new CartLine("SKU-2", 1)));

        Session copy = codec.fromJson(codec.toJson(original)).orElseThrow();

        assertEquals(original, copy);
    }

    @Test
    void theItemEntryStateRoundTrips() {
        Session original = new Session(Session.State.ENTERING_ITEMS, List.of());

        assertEquals(original, codec.fromJson(codec.toJson(original)).orElseThrow());
    }

    @Test
    void anEmptySessionRoundTrips() {
        assertEquals(Session.empty(), codec.fromJson(codec.toJson(Session.empty())).orElseThrow());
    }

    @Test
    void onlyTheRealFieldsAreStored() {
        String json = codec.toJson(Session.empty());

        assertTrue(json.contains("\"state\""), json);
        assertTrue(json.contains("\"cart\""), json);
        assertFalse(json.contains("cartEmpty"), json);
    }

    @Test
    void unknownFieldsFromANewerVersionAreIgnored() {
        Session session = codec.fromJson("{\"state\":\"IDLE\",\"cart\":[],\"somethingNew\":42}").orElseThrow();

        assertTrue(session.isCartEmpty());
    }

    @Test
    void damagedOrIncompatibleJsonGivesNothingInsteadOfFailing() {
        assertTrue(codec.fromJson("not json at all").isEmpty());
        assertTrue(codec.fromJson("{\"state\":\"NO_SUCH_STATE\",\"cart\":[]}").isEmpty());
        assertTrue(codec.fromJson("{\"state\":\"IDLE\",\"cart\":[{\"sku\":\"A\",\"quantity\":0}]}").isEmpty());
        assertTrue(codec.fromJson("").isEmpty());
    }
}

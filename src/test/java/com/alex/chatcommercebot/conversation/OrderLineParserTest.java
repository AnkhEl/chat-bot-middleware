package com.alex.chatcommercebot.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class OrderLineParserTest {

    private static List<CartLine> lines(String text) {
        return OrderLineParser.parse(text).lines();
    }

    @Test
    void readsTheCommonWaysOfWritingAQuantity() {
        assertEquals(List.of(new CartLine("SKU-1", 2)), lines("SKU-1 x 2"));
        assertEquals(List.of(new CartLine("SKU-1", 2)), lines("SKU-1 X 2"));
        assertEquals(List.of(new CartLine("SKU-1", 2)), lines("SKU-1 x2"));
        assertEquals(List.of(new CartLine("SKU-1", 2)), lines("SKU-1 2"));
        assertEquals(List.of(new CartLine("SKU-1", 2)), lines("SKU-1 *2"));
        assertEquals(List.of(new CartLine("SKU-1", 2)), lines("SKU-1 × 2"));
    }

    @Test
    void aMissingQuantityMeansOne() {
        assertEquals(List.of(new CartLine("24-MB01", 1)), lines("24-MB01"));
    }

    @Test
    void aSkuThatEndsInDigitsOrContainsAnXIsNotSplit() {
        assertEquals(List.of(new CartLine("24-MB01", 3)), lines("24-MB01 x 3"));
        assertEquals(List.of(new CartLine("BOX2", 1)), lines("BOX2"));
        assertEquals(List.of(new CartLine("AXE-1", 4)), lines("AXE-1 4"));
    }

    @Test
    void readsOneItemPerLineAndAlsoSemicolons() {
        assertEquals(List.of(new CartLine("A-1", 2), new CartLine("B-2", 1), new CartLine("C-3", 5)),
                lines("A-1 x 2\r\nB-2\n\n  C-3 5  "));
        assertEquals(List.of(new CartLine("A-1", 2), new CartLine("B-2", 3)), lines("A-1 2; B-2 3"));
    }

    @Test
    void linesThatCannotBeReadAreReportedSeparately() {
        OrderLineParser.Result result = OrderLineParser.parse("A-1 x 2\nthis is not an item\nB-2 x 0\nC-3 x 5000");

        assertEquals(List.of(new CartLine("A-1", 2)), result.lines());
        assertEquals(List.of("this is not an item", "B-2 x 0", "C-3 x 5000"), result.unreadable());
        assertFalse(result.tooMany());
    }

    @Test
    void theQuantityLimitIsInclusive() {
        assertEquals(List.of(new CartLine("A-1", OrderLineParser.MAX_QUANTITY)), lines("A-1 x " + OrderLineParser.MAX_QUANTITY));
        assertTrue(lines("A-1 x " + (OrderLineParser.MAX_QUANTITY + 1)).isEmpty());
    }

    @Test
    void tooManyLinesReadsNothing() {
        String text = String.join("\n", java.util.Collections.nCopies(OrderLineParser.MAX_LINES + 1, "A-1"));

        OrderLineParser.Result result = OrderLineParser.parse(text);

        assertTrue(result.tooMany());
        assertTrue(result.lines().isEmpty());
        assertFalse(OrderLineParser.parse(String.join("\n",
                java.util.Collections.nCopies(OrderLineParser.MAX_LINES, "A-1"))).tooMany());
    }

    @Test
    void emptyOrBlankTextGivesNothing() {
        assertTrue(OrderLineParser.parse("").lines().isEmpty());
        assertTrue(OrderLineParser.parse("   \n  ").lines().isEmpty());
        assertTrue(OrderLineParser.parse(null).lines().isEmpty());
    }
}

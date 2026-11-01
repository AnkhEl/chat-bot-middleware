package com.alex.chatcommercebot.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what a customer types when ordering by product code: one item per line (or separated by semicolons),
 * each a SKU with an optional quantity.
 *
 * <pre>
 * SKU-123 x 2        SKU-123 2        SKU-123 *2        SKU-123 (quantity 1)
 * </pre>
 *
 * Whether a SKU exists is Magento's decision; this class only reads the shape of the text.
 */
final class OrderLineParser {

    static final int MAX_LINES = 20;
    static final int MAX_QUANTITY = 999;

    /** A SKU (no spaces), then optionally a quantity, which may be preceded by x, × or *. */
    private static final Pattern LINE =
            Pattern.compile("^(\\S{1,64})(?:\\s+[x×*]?\\s*(\\d{1,4}))?$", Pattern.CASE_INSENSITIVE);

    /**
     * @param lines      the lines that could be read
     * @param unreadable the lines that could not (as typed, trimmed)
     * @param tooMany    true if more than {@link #MAX_LINES} lines were sent; nothing is read then
     */
    record Result(List<CartLine> lines, List<String> unreadable, boolean tooMany) {
    }

    private OrderLineParser() {
    }

    static Result parse(String text) {
        List<String> raw = new ArrayList<>();
        for (String part : (text == null ? "" : text).split("[\\r\\n;]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                raw.add(trimmed);
            }
        }
        if (raw.size() > MAX_LINES) {
            return new Result(List.of(), List.of(), true);
        }

        List<CartLine> lines = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        for (String line : raw) {
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                unreadable.add(line);
                continue;
            }
            int quantity = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                unreadable.add(line);
                continue;
            }
            lines.add(new CartLine(m.group(1), quantity));
        }
        return new Result(lines, unreadable, false);
    }
}

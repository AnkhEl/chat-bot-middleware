package com.alex.chatcommercebot.conversation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A WhatsApp interactive list: a message with one button that opens a list of tappable rows.
 * Provider-neutral. The limits below are WhatsApp's, checked here so a mistake fails in a test
 * instead of as a rejected message in production.
 *
 * @param body         main text (max 1024 characters)
 * @param buttonText   label of the button that opens the list (max 20)
 * @param sectionTitle heading inside the list (max 24)
 * @param rows         1 to 10 rows
 */
public record ListMessage(String body, String buttonText, String sectionTitle, List<Row> rows) {

    public static final int MAX_BODY = 1024;
    public static final int MAX_BUTTON = 20;
    public static final int MAX_SECTION_TITLE = 24;
    public static final int MAX_ROWS = 10;

    public ListMessage {
        require(body, MAX_BODY, "body");
        require(buttonText, MAX_BUTTON, "buttonText");
        require(sectionTitle, MAX_SECTION_TITLE, "sectionTitle");
        if (rows == null || rows.isEmpty() || rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("A list needs 1 to " + MAX_ROWS + " rows");
        }
        rows = List.copyOf(rows);
    }

    /**
     * One tappable row.
     *
     * @param id          comes back in the customer's tap (max 200); what the code switches on
     * @param title       shown to the customer (max 24)
     * @param description optional second line (max 72)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(String id, String title, String description) {

        public static final int MAX_ID = 200;
        public static final int MAX_TITLE = 24;
        public static final int MAX_DESCRIPTION = 72;

        public Row {
            require(id, MAX_ID, "row id");
            require(title, MAX_TITLE, "row title");
            if (description != null && description.length() > MAX_DESCRIPTION) {
                throw new IllegalArgumentException("Row description is longer than " + MAX_DESCRIPTION);
            }
        }
    }

    private static void require(String value, int max, String name) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " must be 1 to " + max + " characters");
        }
    }
}

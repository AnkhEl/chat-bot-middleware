package com.alex.chatcommercebot.conversation;

import java.util.List;

/** Customer-facing texts in one place, so wording can change without touching the logic. */
final class Messages {

    static final String VERIFY_FAILED =
            "That code is invalid or has expired. Please request a new one under "
                    + "My Account > Phone & WhatsApp on our website.";

    static final String TEMPORARY_PROBLEM =
            "Sorry, we are having trouble right now. Please try again in a few minutes.";

    static final String HELP =
            "Here is how ordering on WhatsApp works:\n\n"
                    + "• Send any message to see the menu.\n"
                    + "• Pick what you want to do from the list.\n"
                    + "• You always confirm before an order is placed.\n\n"
                    + "Ordering is being switched on step by step, so some options are not ready yet.";

    static final String ENTER_ITEMS_PROMPT =
            "🔢 Send the items you want, one per line: the product code, then the quantity.\n\n"
                    + "SKU-123 x 2\n"
                    + "SKU-456 x 1\n\n"
                    + "Send MENU to go back.";

    static final String TOO_MANY_ITEMS =
            "That is a lot at once. Please send up to " + OrderLineParser.MAX_LINES + " items at a time.";

    private Messages() {
    }

    static String verified(String firstName) {
        return "✅ Thank you" + nameSuffix(firstName) + ", your number is verified.\n\n"
                + "You can now message us here to order.";
    }

    static String cart(Session session) {
        if (session.isCartEmpty()) {
            return "🛒 Your cart is empty.\n\nSend any message to open the menu and start adding items.";
        }
        StringBuilder text = new StringBuilder("🛒 Your cart:\n");
        for (CartLine line : session.cart()) {
            text.append("\n• ").append(line.sku()).append(" × ").append(line.quantity());
        }
        return text.toString();
    }

    /** Echo of what was read from a list of typed items. Nothing is added to the cart yet. */
    static String itemsRead(List<CartLine> lines, List<String> unreadable) {
        StringBuilder text = new StringBuilder("I read:\n");
        for (CartLine line : lines) {
            text.append("\n• ").append(line.sku()).append(" × ").append(line.quantity());
        }
        if (!unreadable.isEmpty()) {
            text.append("\n\nI could not read:");
            for (String line : unreadable) {
                text.append("\n• ").append(shorten(line));
            }
        }
        text.append("\n\nChecking these against our products and adding them to your cart comes in the next step, ")
                .append("so nothing has been added yet.\n\nSend more items, DONE to see your cart, or MENU to go back.");
        return text.toString();
    }

    static String itemsNotUnderstood(List<String> unreadable) {
        StringBuilder text = new StringBuilder("I could not read that:");
        for (String line : unreadable) {
            text.append("\n• ").append(shorten(line));
        }
        text.append("\n\nPlease send one item per line, the product code and then the quantity, like SKU-123 x 2. ")
                .append("Send MENU to go back.");
        return text.toString();
    }

    private static String shorten(String line) {
        return line.length() <= 40 ? line : line.substring(0, 40) + "…";
    }

    /** Placeholder reply until a menu option is built. */
    static String comingSoon(String feature) {
        return "\"" + feature + "\" is not ready yet, we are still building it. "
                + "Send any message to see the menu again.";
    }

    static String notVerified(String storefrontUrl) {
        String base = storefrontUrl == null ? "" : storefrontUrl.replaceAll("/+$", "");
        return "Hi! We don't recognise this number yet.\n\n"
                + "Please add and verify it under My Account > Phone & WhatsApp at "
                + base + "/phone/account/ and then message us again.";
    }

    private static String nameSuffix(String firstName) {
        return firstName == null || firstName.isBlank() ? "" : " " + firstName.trim();
    }
}

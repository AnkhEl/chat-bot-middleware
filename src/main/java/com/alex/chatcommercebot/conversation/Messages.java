package com.alex.chatcommercebot.conversation;

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

    private Messages() {
    }

    static String verified(String firstName) {
        return "✅ Thank you" + nameSuffix(firstName) + ", your number is verified.\n\n"
                + "You can now message us here to order.";
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

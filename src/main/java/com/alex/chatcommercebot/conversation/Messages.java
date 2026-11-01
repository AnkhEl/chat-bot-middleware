package com.alex.chatcommercebot.conversation;

/** Customer-facing texts in one place, so wording can change without touching the logic. */
final class Messages {

    static final String VERIFY_FAILED =
            "That code is invalid or has expired. Please request a new one under "
                    + "My Account > Phone & WhatsApp on our website.";

    static final String TEMPORARY_PROBLEM =
            "Sorry, we are having trouble right now. Please try again in a few minutes.";

    private Messages() {
    }

    static String verified(String firstName) {
        return "✅ Thank you" + nameSuffix(firstName) + ", your number is verified.\n\n"
                + "You can now message us here to order.";
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

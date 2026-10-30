package com.alex.chatcommercebot.conversation;

/** Keeps personal data out of the logs: only the last four digits of a number are ever printed. */
final class Redact {

    private Redact() {
    }

    static String phone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "***";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}

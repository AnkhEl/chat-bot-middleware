package com.alex.chatcommercebot.conversation;

import java.util.Arrays;
import java.util.List;

/** Builds the main menu shown to a verified customer. */
final class Menu {

    private Menu() {
    }

    static ListMessage forCustomer(String firstName) {
        String name = firstName == null || firstName.isBlank() ? "" : " " + firstName.trim();
        List<ListMessage.Row> rows = Arrays.stream(MenuAction.values())
                .map(a -> new ListMessage.Row(a.id(), a.title(), a.description()))
                .toList();
        return new ListMessage("Hi" + name + "! What would you like to do?", "Open menu", "Main menu", rows);
    }
}

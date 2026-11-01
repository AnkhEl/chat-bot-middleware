package com.alex.chatcommercebot.conversation;

/** The rows of the main menu. The id is what WhatsApp sends back when the customer taps the row. */
public enum MenuAction {

    ORDER_AGAIN("menu_order_again", "Order again", "Repeat one of your earlier orders"),
    BROWSE("menu_browse", "Browse categories", "See what we sell"),
    SEARCH("menu_search", "Search products", "Find a product by name"),
    ENTER_SKUS("menu_skus", "Enter SKUs", "Type product codes and quantities"),
    VIEW_CART("menu_cart", "View cart", "See what you have so far"),
    HELP("menu_help", "Help", "How ordering on WhatsApp works");

    private final String id;
    private final String title;
    private final String description;

    MenuAction(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public String id() {
        return id;
    }

    String title() {
        return title;
    }

    String description() {
        return description;
    }
}

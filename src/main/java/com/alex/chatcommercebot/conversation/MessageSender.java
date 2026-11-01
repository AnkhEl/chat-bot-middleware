package com.alex.chatcommercebot.conversation;

/** Sends replies to a customer (inside the 24-hour window, so no template is needed). */
public interface MessageSender {

    /**
     * @param to   recipient number, digits only (e.g. 27821234567)
     * @param text message body
     */
    void sendText(String to, String text);

    /**
     * Sends an interactive list (a button that opens tappable rows).
     *
     * @param to   recipient number, digits only
     * @param list the list to show
     */
    void sendList(String to, ListMessage list);
}

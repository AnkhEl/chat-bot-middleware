package com.alex.chatcommercebot.conversation;

/** Magento could not be reached, timed out, rejected our token, or returned an unusable answer. */
public class MagentoUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MagentoUnavailableException(String message) {
        super(message);
    }

    public MagentoUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

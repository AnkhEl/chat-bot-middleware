package com.alex.chatcommercebot.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One line of a customer's cart: a product and how many.
 *
 * @param sku      Magento SKU
 * @param quantity how many, at least 1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CartLine(String sku, int quantity) {

    public CartLine {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("A cart line needs a SKU");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
    }
}

package com.alex.chatcommercebot.config;

import org.springframework.web.client.RestClient;

/**
 * Public entry point to the package-private {@link RestClients} helper, so the magento and
 * infobip packages can build their clients without duplicating timeout setup.
 */
public final class ClientFactory {

    private ClientFactory() {
    }

    public static RestClient magento(AppProperties.Magento magento) {
        return RestClients.create(magento.baseUrl(), "Bearer " + magento.token(), magento.timeout());
    }

    public static RestClient infobip(AppProperties.Infobip infobip) {
        // Infobip authenticates with "Authorization: App <api key>".
        return RestClients.create(infobip.baseUrl(), "App " + infobip.apiKey(), infobip.timeout());
    }
}

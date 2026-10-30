
package com.alex.chatcommercebot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
@SpringBootTest
@TestPropertySource(properties = {
        "app.webhook-secret=test-webhook-secret",
        "app.storefront-url=https://shop.example",
        "app.magento.base-url=https://magento.example",
        "app.magento.token=test-magento-token",
        "app.infobip.base-url=https://test.api.infobip.com",
        "app.infobip.api-key=test-api-key",
        "app.infobip.sender=27000000000"
})
class ChatCommerceBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
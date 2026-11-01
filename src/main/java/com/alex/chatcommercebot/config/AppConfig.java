package com.alex.chatcommercebot.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.alex.chatcommercebot.conversation.*;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
class AppConfig {

    /** One virtual thread per webhook message: the HTTP request returns immediately, work continues here. */
    @Bean(name = "webhookExecutor", destroyMethod = "close")
    ExecutorService webhookExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ConversationService conversationService(MagentoGateway magento, MessageSender sender,
                                            KeyValueStore store, SessionStore sessions, AppProperties props) {
        return new ConversationService(magento, sender, store, sessions,
                new ConversationService.Settings(props.storefrontUrl(), props.limits().messagesPerMinute()));
    }
}

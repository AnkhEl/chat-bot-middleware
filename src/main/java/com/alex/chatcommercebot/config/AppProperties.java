package com.alex.chatcommercebot.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All settings live under the {@code app.*} prefix (see application.yml). The app refuses to start
 * when a required value is missing, so a misconfiguration shows up immediately.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String webhookSecret,
        @NotBlank String storefrontUrl,
        @NotNull @Valid Magento magento,
        @NotNull @Valid Infobip infobip,
        Limits limits) {

    public AppProperties {
        if (limits == null) {
            limits = new Limits(20);
        }
    }

    /** @param token Magento Integration access token (only the two Phone Verification permissions) */
    public record Magento(@NotBlank String baseUrl, @NotBlank String token, Duration timeout) {

        public Magento {
            if (timeout == null) {
                timeout = Duration.ofSeconds(10);
            }
        }
    }

    /** @param sender the registered WhatsApp sender number, digits only (no +) */
    public record Infobip(@NotBlank String baseUrl, @NotBlank String apiKey, @NotBlank String sender,
                          Duration timeout) {

        public Infobip {
            if (timeout == null) {
                timeout = Duration.ofSeconds(10);
            }
        }
    }

    public record Limits(int messagesPerMinute) {

        public Limits {
            if (messagesPerMinute <= 0) {
                messagesPerMinute = 20;
            }
        }
    }
}

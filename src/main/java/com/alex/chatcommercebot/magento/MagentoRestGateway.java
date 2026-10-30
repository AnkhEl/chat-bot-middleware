package com.alex.chatcommercebot.magento;

import java.util.Map;

import com.alex.chatcommercebot.config.AppProperties;
import com.alex.chatcommercebot.config.ClientFactory;
import com.alex.chatcommercebot.conversation.MagentoGateway;
import com.alex.chatcommercebot.conversation.MagentoUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Talks to the Vendor_PhoneVerification module:
 * POST /rest/V1/phone/verify and GET /rest/V1/phone/lookup.
 */
@Component
class MagentoRestGateway implements MagentoGateway {

    private final RestClient client;

    MagentoRestGateway(AppProperties props) {
        this.client = ClientFactory.magento(props.magento());
    }

    @Override
    public VerifyResult verify(String phone, String code) {
        VerifyResponse response = call(() -> client.post()
                .uri("/rest/V1/phone/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("phone", phone, "code", code))
                .retrieve()
                .body(VerifyResponse.class));

        return new VerifyResult(response.success(), response.firstName());
    }

    @Override
    public LookupResult lookup(String phone) {
        LookupResponse response = call(() -> client.get()
                .uri(uri -> uri.path("/rest/V1/phone/lookup").queryParam("phone", phone).build())
                .retrieve()
                .body(LookupResponse.class));

        return new LookupResult(response.found(), response.phoneVerified(), response.firstName());
    }

    /** Turns every transport / HTTP error into one exception, without leaking request or response bodies. */
    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            T body = request.get();
            if (body == null) {
                throw new MagentoUnavailableException("Magento returned an empty response");
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new MagentoUnavailableException("Magento answered HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // Includes connection refused, timeouts and TLS problems (for example an untrusted Warden CA).
            Throwable root = e.getMostSpecificCause();
            throw new MagentoUnavailableException(
                    "Could not reach Magento: " + root.getClass().getSimpleName() + ": " + root.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VerifyResponse(boolean success, @JsonProperty("first_name") String firstName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LookupResponse(boolean found,
                          @JsonProperty("phone_verified") boolean phoneVerified,
                          @JsonProperty("first_name") String firstName) {
    }
}

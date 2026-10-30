package com.alex.chatcommercebot.conversation;

/**
 * What the conversation needs from Magento. Implementations throw
 * {@link MagentoUnavailableException} when Magento cannot be reached or answers with an error,
 * and never for business outcomes such as a wrong code (those are in the result).
 */
public interface MagentoGateway {

    VerifyResult verify(String phone, String code);

    LookupResult lookup(String phone);

    record VerifyResult(boolean success, String firstName) {
    }

    record LookupResult(boolean found, boolean phoneVerified, String firstName) {

        public static LookupResult notFound() {
            return new LookupResult(false, false, null);
        }
    }
}

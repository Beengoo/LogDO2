package ua.beengoo.logdo2.api.service;

import ua.beengoo.logdo2.api.ports.OAuthPort;

/**
 * High-level OAuth operations. This interface intentionally mirrors the high-level
 * needs of the application while delegating low-level token management to ports.
 */
public interface OAuthService {
    /**
     * Build authorization URL for a state and redirect URI.
     */
    String buildAuthUrl(String state, String redirectUri);

    /**
     * Exchange an authorization code for tokens; returns the underlying port result.
     */
    OAuthPort.TokenSet exchangeCode(String code, String redirectUri);
}

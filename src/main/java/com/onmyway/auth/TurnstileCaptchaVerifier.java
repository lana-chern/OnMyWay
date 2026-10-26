package com.onmyway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TurnstileCaptchaVerifier {
    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private final RestClient restClient;
    private final String secret;

    public TurnstileCaptchaVerifier(@Value("${omw.auth.turnstile-secret:}") String secret) {
        this.restClient = RestClient.create();
        this.secret = secret == null ? "" : secret.trim();
        if (this.secret.isEmpty()) log.warn("Turnstile CAPTCHA is not configured; step-up verification is disabled until OMW_AUTH_TURNSTILE_SECRET is set");
    }

    public boolean isConfigured() { return !secret.isEmpty(); }

    public boolean verify(String token, String clientIp) {
        if (!isConfigured() || token == null || token.isBlank()) return false;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (clientIp != null && !clientIp.isBlank()) form.add("remoteip", clientIp);
        try {
            TurnstileResponse response = restClient.post().uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TurnstileResponse.class);
            return response != null && Boolean.TRUE.equals(response.success());
        } catch (RestClientException exception) {
            log.warn("Turnstile verification failed; rejecting step-up login", exception);
            return false;
        }
    }

    private record TurnstileResponse(Boolean success) { }
}
package com.onmyway.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the client IP without trusting forwarding headers from arbitrary clients.
 * Configure exact reverse-proxy IPs through OMW_AUTH_TRUSTED_PROXY_ADDRESSES.
 */
@Component
public class ClientIpAddressResolver {
    private final Set<String> trustedProxyAddresses;

    public ClientIpAddressResolver(
            @Value("${omw.auth.trusted-proxy-addresses:}") String trustedProxyAddresses) {
        this.trustedProxyAddresses = Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || !trustedProxyAddresses.contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        String clientAddress = forwardedFor.split(",", 2)[0].trim();
        return clientAddress.isEmpty() ? remoteAddress : clientAddress;
    }
}

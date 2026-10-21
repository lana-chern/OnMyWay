package com.onmyway.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpAddressResolverTest {

    @Test
    void ignoresForwardedHeaderFromUntrustedRemoteAddress() {
        ClientIpAddressResolver resolver = new ClientIpAddressResolver("10.0.0.10");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void usesFirstForwardedAddressOnlyWhenRemoteAddressIsTrustedProxy() {
        ClientIpAddressResolver resolver = new ClientIpAddressResolver("10.0.0.10, 10.0.0.11");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.7, 10.0.0.11");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void fallsBackToRemoteAddressWhenForwardedHeaderIsBlank() {
        ClientIpAddressResolver resolver = new ClientIpAddressResolver("10.0.0.10");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn(" ");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.10");
    }
}

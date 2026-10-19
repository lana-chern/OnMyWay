package com.onmyway.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock
    JwtService jwtService;
    @Mock
    OnMyWayUserDetailsService userDetailsService;
    @Mock
    FilterChain filterChain;
    @InjectMocks
    JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesThroughWithoutAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void passesThroughNonBearerAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void rejectsEmptyBearerToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService, userDetailsService, filterChain);
    }

    @Test
    void authenticatesValidBearerTokenUsingUserId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        OnMyWayUserDetails userDetails = userDetails(true);
        when(jwtService.extractUserId("jwt-token")).thenReturn(1842L);
        when(userDetailsService.loadUserById(1842L)).thenReturn(userDetails);
        filter.doFilter(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(userDetails);
        verify(jwtService).extractUserId("jwt-token");
        verify(userDetailsService).loadUserById(1842L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void acceptsBearerSchemeCaseInsensitively() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        OnMyWayUserDetails userDetails = userDetails(true);
        when(jwtService.extractUserId("jwt-token")).thenReturn(1842L);
        when(userDetailsService.loadUserById(1842L)).thenReturn(userDetails);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void propagatesUnexpectedUserLookupFailure() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractUserId("jwt-token")).thenReturn(1842L);
        when(userDetailsService.loadUserById(1842L))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verifyNoInteractions(filterChain);
    }

    @Test
    void rejectsJwtException() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractUserId("invalid-token")).thenThrow(new JwtException("Invalid signature"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService, filterChain);
    }

    @Test
    void rejectsInvalidBearerToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractUserId("invalid-token")).thenThrow(new IllegalArgumentException("Invalid token"));
        filter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService, filterChain);
    }

    @Test
    void rejectsTokenForMissingUser() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractUserId("jwt-token")).thenReturn(1842L);
        when(userDetailsService.loadUserById(1842L)).thenThrow(new UsernameNotFoundException("User not found"));
        filter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(filterChain);
    }

    @Test
    void rejectsTokenForDisabledUser() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.extractUserId("jwt-token")).thenReturn(1842L);
        when(userDetailsService.loadUserById(1842L)).thenReturn(userDetails(false));
        filter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService).loadUserById(1842L);
        verifyNoInteractions(filterChain);
    }

    private OnMyWayUserDetails userDetails(boolean enabled) {
        return new OnMyWayUserDetails(1842L, "updated-email@example.com", "encoded-password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), enabled);
    }
}

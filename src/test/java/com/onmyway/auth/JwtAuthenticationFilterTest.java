package com.onmyway.auth;

import com.onmyway.data.entities.User;
import com.onmyway.data.entities.UserStatus;
import com.onmyway.data.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock
    JwtService jwtService;

    @Mock
    UserDetailsService userDetailsService;

    @Mock
    UserRepository userRepository;

    @Mock
    UserDetails userDetails;

    @Mock
    FilterChain filterChain;

    @InjectMocks
    JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidBearerToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = new User();
        user.setEmail("test@example.com");
        user.setStatus(UserStatus.ACTIVE);

        when(jwtService.isValid("jwt-token")).thenReturn(true);
        when(jwtService.extractEmail("jwt-token")).thenReturn("test@example.com");
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(userDetails);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsInvalidBearerToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.isValid("invalid-token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userRepository, userDetailsService, filterChain);
    }
}

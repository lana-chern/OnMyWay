package com.onmyway.auth;

import com.onmyway.auth.api.CurrentUserResponse;
import com.onmyway.auth.api.LoginRequest;
import com.onmyway.auth.api.RegisterRequest;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import com.onmyway.data.entities.UserStatus;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    AuthenticationManager authenticationManager;

    @InjectMocks
    AuthService authService;

    @Test
    void registersUserWithNormalizedEmailAndUserRole() {
        RegisterRequest request = new RegisterRequest("  Test@Example.COM  ", "password123", "  Alice  ");
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        CurrentUserResponse response = authService.register(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.roles()).containsExactly(Role.USER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getDisplayName()).isEqualTo("Alice");
        assertThat(savedUser.getRoles()).containsExactly(Role.USER);
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void logsInWithValidCredentials() {
        LoginRequest request = new LoginRequest("  Test@Example.COM  ", "password123");
        User user = user("test@example.com", "encoded-password", "Alice", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(java.util.Optional.of(user));

        CurrentUserResponse response = authService.login(request);

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.roles()).containsExactly(Role.USER);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void rejectsInvalidCredentials() {
        LoginRequest request = new LoginRequest("test@example.com", "wrong-password");
        User user = user("test@example.com", "encoded-password", "Alice", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(java.util.Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(mock(AuthenticationException.class));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void rejectsInactiveUser() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        User user = user("test@example.com", "encoded-password", "Alice", UserStatus.BLOCKED);
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User account is not active");

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void rejectsUnknownEmail() {
        LoginRequest request = new LoginRequest("unknown@example.com", "password123");
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void rejectsBlankLoginEmail() {
        LoginRequest request = new LoginRequest("   ", "password123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is required");
    }

    @Test
    void rejectsBlankLoginPassword() {
        LoginRequest request = new LoginRequest("test@example.com", "   ");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password is required");
    }

    @Test
    void rejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("test@example.com", "password123", "Alice");
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> authService.register(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Registration request is required");
    }

    @Test
    void rejectsBlankEmail() {
        RegisterRequest request = new RegisterRequest("   ", "password123", "Alice");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is required");
    }

    @Test
    void rejectsShortPassword() {
        RegisterRequest request = new RegisterRequest("test@example.com", "1234567", "Alice");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must contain at least 8 characters");
    }

    @Test
    void rejectsBlankDisplayName() {
        RegisterRequest request = new RegisterRequest("test@example.com", "password123", "   ");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Display name is required and must be at most 100 characters");
    }

    @Test
    void rejectsTooLongDisplayName() {
        RegisterRequest request = new RegisterRequest(
                "test@example.com",
                "password123",
                "a".repeat(101)
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Display name is required and must be at most 100 characters");
    }

    private User user(String email, String passwordHash, String displayName, UserStatus status) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.getRoles().add(Role.USER);
        return user;
    }
}

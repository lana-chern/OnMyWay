package com.onmyway.auth;

import com.onmyway.auth.api.CurrentUserResponse;
import com.onmyway.auth.api.RegisterRequest;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
}

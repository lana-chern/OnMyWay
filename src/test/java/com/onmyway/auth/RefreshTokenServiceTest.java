package com.onmyway.auth;

import com.onmyway.data.entities.RefreshToken;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import com.onmyway.data.repositories.RefreshTokenRepository;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(
            refreshTokenRepository, userRepository, 60_000L
    );

    @Test
    void rejectsExpiredRefreshToken() {
        User user = activeUser();
        RefreshToken expiredToken = new RefreshToken(user, "stored-hash", Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> service.rotate("expired-token"))
                .isInstanceOf(InvalidAuthenticationException.class)
                .hasMessage("Invalid refresh token");

        verifyNoInteractions(userRepository);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setDisplayName("Test");
        user.getRoles().add(Role.USER);
        return user;
    }
}

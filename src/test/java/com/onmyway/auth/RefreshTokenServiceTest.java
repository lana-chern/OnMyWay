package com.onmyway.auth;

import com.onmyway.data.entities.RefreshToken;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import com.onmyway.data.repositories.RefreshTokenRepository;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        verify(refreshTokenRepository, never()).revokeActiveTokensByFamilyId(any(UUID.class), any(Instant.class));
    }

    @Test
    void replayingRevokedTokenRevokesAllActiveTokensInItsFamily() {
        User user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshToken replayedToken = new RefreshToken(
                user, "stored-hash", Instant.now().plusSeconds(60), familyId
        );
        replayedToken.revoke(Instant.now());
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(replayedToken));
        when(refreshTokenRepository.revokeActiveTokensByFamilyId(eq(familyId), any(Instant.class)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.rotate("replayed-token"))
                .isInstanceOf(InvalidAuthenticationException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository).revokeActiveTokensByFamilyId(eq(familyId), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verifyNoInteractions(userRepository);
    }

    @Test
    void cleanupContinuesUntilLastBatchIsSmallerThanBatchSize() {
        when(refreshTokenRepository.deleteExpiredTokensBatch(any(Instant.class), eq(500)))
                .thenReturn(500, 3);

        service.cleanupExpiredTokens();

        verify(refreshTokenRepository, times(2))
                .deleteExpiredTokensBatch(any(Instant.class), eq(500));
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

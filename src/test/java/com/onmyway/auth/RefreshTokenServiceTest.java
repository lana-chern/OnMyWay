package com.onmyway.auth;

import com.onmyway.data.entities.RefreshToken;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import com.onmyway.data.repositories.RefreshTokenRepository;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(refreshTokenRepository.findFamilyIdByTokenHash(anyString()))
                .thenReturn(Optional.of(expiredToken.getFamilyId()));
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
    void rotationPreservesTheTokenFamily() {
        User user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshToken currentToken = new RefreshToken(
                user, "stored-hash", Instant.now().plusSeconds(60), familyId
        );
        when(refreshTokenRepository.findFamilyIdByTokenHash(anyString()))
                .thenReturn(Optional.of(currentToken.getFamilyId()));
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(currentToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.rotate("current-token");

        org.mockito.ArgumentCaptor<RefreshToken> tokenCaptor =
                org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(tokenCaptor.capture());
        List<RefreshToken> savedTokens = tokenCaptor.getAllValues();

        assertThat(savedTokens.get(0).getRevokedAt()).isNotNull();
        assertThat(savedTokens).allSatisfy(token ->
                assertThat(token.getFamilyId()).isEqualTo(familyId));
        verify(refreshTokenRepository).lockFamily(familyId);
    }

    @Test
    void replayingRevokedTokenRevokesAllActiveTokensInItsFamily() {
        User user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshToken replayedToken = new RefreshToken(
                user, "stored-hash", Instant.now().plusSeconds(60), familyId
        );
        replayedToken.revoke(Instant.now());
        when(refreshTokenRepository.findFamilyIdByTokenHash(anyString()))
                .thenReturn(Optional.of(replayedToken.getFamilyId()));
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(replayedToken));
        when(refreshTokenRepository.revokeActiveTokensByFamilyId(eq(familyId), any(Instant.class)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.rotate("replayed-token"))
                .isInstanceOf(InvalidAuthenticationException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository).lockFamily(familyId);
        verify(refreshTokenRepository).revokeActiveTokensByFamilyId(eq(familyId), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verifyNoInteractions(userRepository);
    }

    @Test
    void logoutRevokesAllActiveTokensInTheRefreshTokenFamily() {
        UUID familyId = UUID.randomUUID();
        when(refreshTokenRepository.findFamilyIdByTokenHash(anyString()))
                .thenReturn(Optional.of(familyId));

        service.revoke("refresh-token");

        org.mockito.InOrder order = inOrder(refreshTokenRepository);
        order.verify(refreshTokenRepository).findFamilyIdByTokenHash(anyString());
        order.verify(refreshTokenRepository).lockFamily(familyId);
        order.verify(refreshTokenRepository)
                .revokeActiveTokensByFamilyId(eq(familyId), any(Instant.class));
        verify(refreshTokenRepository, never()).findByTokenHashForUpdate(anyString());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void logoutWithUnknownRefreshTokenDoesNothing() {
        when(refreshTokenRepository.findFamilyIdByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        service.revoke("unknown-token");

        verify(refreshTokenRepository).findFamilyIdByTokenHash(anyString());
        verify(refreshTokenRepository, never()).lockFamily(any(UUID.class));
        verify(refreshTokenRepository, never())
                .revokeActiveTokensByFamilyId(any(UUID.class), any(Instant.class));
    }

    @Test
    void logoutWithBlankRefreshTokenDoesNotQueryRepository() {
        service.revoke("  ");

        verifyNoInteractions(refreshTokenRepository);
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

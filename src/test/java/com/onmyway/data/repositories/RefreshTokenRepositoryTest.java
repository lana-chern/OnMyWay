package com.onmyway.data.repositories;

import com.onmyway.data.entities.RefreshToken;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void deletesOnlyExpiredTokensAndRetainsUnexpiredTokensIncludingRevokedOnes() {
        User user = userRepository.saveAndFlush(testUser("cleanup-scope@example.com"));
        Instant now = Instant.now();

        refreshTokenRepository.saveAndFlush(new RefreshToken(user, "expired-hash", now.minusSeconds(1)));
        refreshTokenRepository.saveAndFlush(new RefreshToken(user, "active-hash", now.plusSeconds(3600)));

        RefreshToken revokedButUnexpired = new RefreshToken(user, "revoked-hash", now.plusSeconds(3600));
        revokedButUnexpired.revoke(now.minusSeconds(10));
        refreshTokenRepository.saveAndFlush(revokedButUnexpired);

        int deleted = refreshTokenRepository.deleteExpiredTokensBatch(now, 10);

        assertThat(deleted).isEqualTo(1);
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getTokenHash)
                .containsExactlyInAnyOrder("active-hash", "revoked-hash");
    }

    @Test
    void deletesNoMoreThanRequestedBatchSize() {
        User user = userRepository.saveAndFlush(testUser("cleanup-batch@example.com"));
        Instant expiredAt = Instant.now().minusSeconds(60);

        refreshTokenRepository.saveAllAndFlush(List.of(
                new RefreshToken(user, "expired-hash-1", expiredAt),
                new RefreshToken(user, "expired-hash-2", expiredAt.minusSeconds(1)),
                new RefreshToken(user, "expired-hash-3", expiredAt.minusSeconds(2))
        ));

        assertThat(refreshTokenRepository.deleteExpiredTokensBatch(Instant.now(), 2)).isEqualTo(2);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        assertThat(refreshTokenRepository.deleteExpiredTokensBatch(Instant.now(), 2)).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void revokesOnlyActiveTokensFromTheRequestedFamily() {
        User user = userRepository.saveAndFlush(testUser("refresh-family@example.com"));
        UUID compromisedFamily = UUID.randomUUID();
        UUID independentFamily = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(600);

        RefreshToken oldToken = new RefreshToken(user, "a".repeat(64), expiry, compromisedFamily);
        oldToken.revoke(Instant.now().minusSeconds(1));
        RefreshToken activeDescendant = new RefreshToken(user, "b".repeat(64), expiry, compromisedFamily);
        RefreshToken independentSession = new RefreshToken(user, "c".repeat(64), expiry, independentFamily);
        refreshTokenRepository.saveAndFlush(oldToken);
        refreshTokenRepository.saveAndFlush(activeDescendant);
        refreshTokenRepository.saveAndFlush(independentSession);

        int revoked = refreshTokenRepository.revokeActiveTokensByFamilyId(compromisedFamily, Instant.now());
        refreshTokenRepository.flush();

        assertThat(revoked).isEqualTo(1);
        assertThat(refreshTokenRepository.findById(oldToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.findById(activeDescendant.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.findById(independentSession.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    private User testUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-password-hash");
        user.setDisplayName("Cleanup test user");
        user.getRoles().add(Role.USER);
        return user;
    }
}

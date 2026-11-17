package com.onmyway.auth;

import com.onmyway.data.entities.RefreshToken;
import com.onmyway.data.entities.User;
import com.onmyway.data.entities.UserStatus;
import com.onmyway.data.repositories.RefreshTokenRepository;
import com.onmyway.data.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int CLEANUP_BATCH_SIZE = 500;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final long expirationMs;
    private final Duration familyAbsoluteLifetime;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            @Value("${omw.jwt.refresh-expiration-ms:2592000000}") long expirationMs,
            @Value("${omw.jwt.refresh-family-absolute-expiration-ms:7776000000}") long familyAbsoluteExpirationMs
    ) {
        if (expirationMs <= 0) {
            throw new IllegalStateException("OMW_JWT_REFRESH_EXPIRATION_MS must be greater than 0");
        }
        if (familyAbsoluteExpirationMs <= 0) {
            throw new IllegalStateException("OMW_JWT_REFRESH_FAMILY_ABSOLUTE_EXPIRATION_MS must be greater than 0");
        }
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.expirationMs = expirationMs;
        this.familyAbsoluteLifetime = Duration.ofMillis(familyAbsoluteExpirationMs);
    }

    @Transactional
    public String issue(User user) {
        Instant now = Instant.now();
        Instant familyExpiresAt = now.plus(familyAbsoluteLifetime);
        Instant tokenExpiresAt = min(now.plusMillis(expirationMs), familyExpiresAt);
        String rawToken = generateRawToken();
        refreshTokenRepository.save(new RefreshToken(
                user, hash(rawToken), tokenExpiresAt, UUID.randomUUID(), familyExpiresAt
        ));
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidAuthenticationException.class)
    public RefreshGrant rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidAuthenticationException("Invalid refresh token");
        }

        Instant now = Instant.now();
        String tokenHash = hash(rawToken);
        UUID familyId = refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidAuthenticationException("Invalid refresh token"));

        // Lock the family before locking an individual token. This makes reuse detection
        // atomic with concurrent rotations, preventing a just-created descendant from
        // escaping the family-wide revocation.
        refreshTokenRepository.lockFamily(familyId);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidAuthenticationException("Invalid refresh token"));

        if (storedToken.isRevoked()) {
            // A previously rotated token is being replayed. Revoke its entire session family.
            // noRollbackFor ensures the family-wide revocation commits with the authentication error.
            refreshTokenRepository.revokeActiveTokensByFamilyId(storedToken.getFamilyId(), now);
            throw new InvalidAuthenticationException("Invalid refresh token");
        }
        if (!storedToken.isUsableAt(now)) {
            throw new InvalidAuthenticationException("Invalid refresh token");
        }

        User user = userRepository.findById(storedToken.getUser().getId())
                .orElseThrow(() -> new InvalidAuthenticationException("Invalid refresh token"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            // Invalidate every token issued for this session, not just the presented token.
            // noRollbackFor ensures the revocation commits with the authentication error.
            refreshTokenRepository.revokeActiveTokensByFamilyId(storedToken.getFamilyId(), now);
            throw new InvalidAuthenticationException("User account is not active");
        }
        if (storedToken.getTokenVersion() != user.getTokenVersion()) {
            // The account status changed after this refresh token was issued (for example,
            // it was blocked and then reactivated). Never revive the old session.
            refreshTokenRepository.revokeActiveTokensByFamilyId(storedToken.getFamilyId(), now);
            throw new InvalidAuthenticationException("Invalid refresh token");
        }

        storedToken.revoke(now);
        refreshTokenRepository.save(storedToken);
        String replacement = generateRawToken();
        Instant replacementExpiresAt = min(now.plusMillis(expirationMs), storedToken.getFamilyExpiresAt());
        refreshTokenRepository.save(new RefreshToken(
                user,
                hash(replacement),
                replacementExpiresAt,
                storedToken.getFamilyId(),
                storedToken.getFamilyExpiresAt()
        ));
        return new RefreshGrant(user, replacement);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = hash(rawToken);
        refreshTokenRepository.findFamilyIdByTokenHash(tokenHash).ifPresent(familyId -> {
            // Use the same lock order as rotate(): family first, then token-level work.
            // Revoking the whole family prevents a concurrent rotation from leaving a
            // replacement refresh token active after logout.
            refreshTokenRepository.lockFamily(familyId);
            refreshTokenRepository.revokeActiveTokensByFamilyId(familyId, Instant.now());
        });
    }

    /**
     * Deletes complete families only after their absolute lifetime has ended.
     * Keeping every token hash until then preserves replay detection for rotated tokens,
     * even when an individual token's idle expiration has passed.
     */
    @Scheduled(
            fixedDelayString = "${omw.auth.refresh-token-cleanup-interval-ms:86400000}",
            initialDelayString = "${omw.auth.refresh-token-cleanup-initial-delay-ms:60000}"
    )
    public void cleanupExpiredTokens() {
        int deleted;
        do {
            deleted = refreshTokenRepository.deleteExpiredTokensBatch(Instant.now(), CLEANUP_BATCH_SIZE);
        } while (deleted == CLEANUP_BATCH_SIZE);
    }

    private Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record RefreshGrant(User user, String refreshToken) {
    }
}

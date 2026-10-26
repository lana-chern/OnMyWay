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

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            @Value("${omw.jwt.refresh-expiration-ms:2592000000}") long expirationMs
    ) {
        if (expirationMs <= 0) {
            throw new IllegalStateException("OMW_JWT_REFRESH_EXPIRATION_MS must be greater than 0");
        }
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        refreshTokenRepository.save(new RefreshToken(
                user, hash(rawToken), Instant.now().plusMillis(expirationMs), UUID.randomUUID()
        ));
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidAuthenticationException.class)
    public RefreshGrant rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidAuthenticationException("Invalid refresh token");
        }

        Instant now = Instant.now();
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
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
            storedToken.revoke(now);
            throw new InvalidAuthenticationException("User account is not active");
        }

        storedToken.revoke(now);
        refreshTokenRepository.save(storedToken);
        String replacement = generateRawToken();
        refreshTokenRepository.save(new RefreshToken(
                user, hash(replacement), now.plusMillis(expirationMs), storedToken.getFamilyId()
        ));
        return new RefreshGrant(user, replacement);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(token -> {
                    token.revoke(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    /**
     * Deletes expired tokens in small transactions to limit lock duration and database load.
     * Revoked tokens are retained until their original expiry.
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

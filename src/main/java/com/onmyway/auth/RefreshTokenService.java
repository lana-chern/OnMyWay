package com.onmyway.auth;

import com.onmyway.data.entities.RefreshToken;
import com.onmyway.data.entities.User;
import com.onmyway.data.entities.UserStatus;
import com.onmyway.data.repositories.RefreshTokenRepository;
import com.onmyway.data.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

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
        refreshTokenRepository.save(new RefreshToken(user, hash(rawToken), Instant.now().plusMillis(expirationMs)));
        return rawToken;
    }

    @Transactional
    public RefreshGrant rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        Instant now = Instant.now();
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (!storedToken.isUsableAt(now)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        User user = userRepository.findById(storedToken.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            storedToken.revoke(now);
            throw new IllegalArgumentException("User account is not active");
        }

        storedToken.revoke(now);
        refreshTokenRepository.save(storedToken);
        String replacement = generateRawToken();
        refreshTokenRepository.save(new RefreshToken(user, hash(replacement), now.plusMillis(expirationMs)));
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

package com.onmyway.data.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens", uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"))
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "family_expires_at", nullable = false, updatable = false)
    private Instant familyExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this(user, tokenHash, expiresAt, UUID.randomUUID(), expiresAt);
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt, UUID familyId) {
        this(user, tokenHash, expiresAt, familyId, expiresAt);
    }

    public RefreshToken(
            User user,
            String tokenHash,
            Instant expiresAt,
            UUID familyId,
            Instant familyExpiresAt
    ) {
        if (familyExpiresAt == null || expiresAt == null || expiresAt.isAfter(familyExpiresAt)) {
            throw new IllegalArgumentException("Token expiration must not exceed family expiration");
        }
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.familyId = familyId;
        this.familyExpiresAt = familyExpiresAt;
        this.tokenVersion = user.getTokenVersion();
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now) && familyExpiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}

package com.onmyway.data.repositories;

import com.onmyway.data.entities.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Query("select token.familyId from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<UUID> findFamilyIdByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Serializes refresh-token operations within one family. The transaction-scoped lock
     * is released automatically when the surrounding service transaction completes.
     */
    @Query(value = "select pg_advisory_xact_lock(hashtext(cast(:familyId as text))) is null", nativeQuery = true)
    boolean lockFamily(@Param("familyId") UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.familyId = :familyId
              and token.revokedAt is null
            """)
    int revokeActiveTokensByFamilyId(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            delete from refresh_tokens
            where id in (
                select id
                from refresh_tokens
                where expires_at <= :now
                order by expires_at
                limit :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredTokensBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}

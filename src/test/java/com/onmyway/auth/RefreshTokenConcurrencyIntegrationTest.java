package com.onmyway.auth;

import com.onmyway.data.entities.User;
import com.onmyway.data.repositories.RefreshTokenRepository;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RefreshTokenConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("onmyway_auth_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from refresh_tokens");
        jdbcTemplate.update("delete from user_roles");
        jdbcTemplate.update("delete from users");
    }

    @Test
    void logoutRacingWithRotationLeavesNoActiveTokenInTheFamily() throws Exception {
        String rawToken = issueRefreshToken("logout-race@example.com");
        String tokenHash = hash(rawToken);
        var familyId = refreshTokenRepository.findFamilyIdByTokenHash(tokenHash).orElseThrow();

        runAtSameTime(
                () -> refreshTokenService.revoke(rawToken),
                () -> {
                    try {
                        refreshTokenService.rotate(rawToken);
                    } catch (InvalidAuthenticationException ignored) {
                        // Logout may acquire the family lock first and invalidate rotation.
                    }
                }
        );

        assertThat(countActiveTokens(familyId)).isZero();
    }

    @Test
    void concurrentRotationsOfTheSameTokenInvalidateTheWholeFamily() throws Exception {
        String rawToken = issueRefreshToken("rotation-race@example.com");
        String tokenHash = hash(rawToken);
        var familyId = refreshTokenRepository.findFamilyIdByTokenHash(tokenHash).orElseThrow();
        AtomicInteger successfulRotations = new AtomicInteger();
        AtomicInteger rejectedRotations = new AtomicInteger();

        runAtSameTime(
                () -> rotateAndCount(rawToken, successfulRotations, rejectedRotations),
                () -> rotateAndCount(rawToken, successfulRotations, rejectedRotations)
        );

        assertThat(successfulRotations).hasValue(1);
        assertThat(rejectedRotations).hasValue(1);
        // Reuse detection revokes the descendant created by the winning rotation.
        assertThat(countActiveTokens(familyId)).isZero();
    }

    private void rotateAndCount(
            String rawToken,
            AtomicInteger successfulRotations,
            AtomicInteger rejectedRotations
    ) {
        try {
            refreshTokenService.rotate(rawToken);
            successfulRotations.incrementAndGet();
        } catch (InvalidAuthenticationException expected) {
            rejectedRotations.incrementAndGet();
        }
    }

    private void runAtSameTime(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> firstFuture = executor.submit(() -> runAfterSignal(first, ready, start));
            Future<?> secondFuture = executor.submit(() -> runAfterSignal(second, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstFuture.get(20, TimeUnit.SECONDS);
            secondFuture.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void runAfterSignal(Runnable action, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test start");
            }
            action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test interrupted", exception);
        }
    }

    private String issueRefreshToken(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-this-test");
        user.setDisplayName("Concurrency test");
        userRepository.saveAndFlush(user);
        return refreshTokenService.issue(user);
    }

    private int countActiveTokens(java.util.UUID familyId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from refresh_tokens
                where family_id = ?
                  and revoked_at is null
                  and expires_at > ?
                """, Integer.class, familyId, Instant.now());
        return count == null ? 0 : count;
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

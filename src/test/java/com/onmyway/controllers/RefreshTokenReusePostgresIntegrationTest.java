package com.onmyway.controllers;

import com.onmyway.data.entities.RefreshToken;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import com.onmyway.data.repositories.PlaceRepository;
import com.onmyway.data.repositories.RefreshTokenRepository;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises refresh-token reuse detection against PostgreSQL, where the row-locking
 * behavior is the same as production. CI enables this test by providing the
 * MIGRATION_TEST_* database variables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
@TestPropertySource(properties = {
        "omw.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class RefreshTokenReusePostgresIntegrationTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("MIGRATION_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> requiredEnv("MIGRATION_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnv("MIGRATION_TEST_DB_PASSWORD"));
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        placeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentReplayRevokesEveryTokenInCompromisedFamilyButKeepsOtherSessionActive() throws Exception {
        registerUser("reuse-postgres@example.com");
        String firstLogin = login("reuse-postgres@example.com");
        String replayedToken = jsonPath(firstLogin, "$.refreshToken");
        UUID compromisedFamilyId = onlyActiveToken().getFamilyId();

        // A separate login must create a different family and must survive the replay.
        String independentLogin = login("reuse-postgres@example.com");
        String independentToken = jsonPath(independentLogin, "$.refreshToken");
        UUID independentFamilyId = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getFamilyId().equals(compromisedFamilyId) == false)
                .findFirst()
                .orElseThrow()
                .getFamilyId();
        assertThat(independentFamilyId).isNotEqualTo(compromisedFamilyId);

        String body = """
                {"refreshToken":"%s"}
                """.formatted(replayedToken);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent refresh");
                }
                return mockMvc.perform(post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(request);
            Future<Integer> second = executor.submit(request);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        } finally {
            executor.shutdownNow();
        }

        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).filteredOn(token -> token.getFamilyId().equals(compromisedFamilyId))
                .isNotEmpty()
                .allSatisfy(token -> assertThat(token.getRevokedAt()).as("compromised-family token %s", token.getId())
                        .isNotNull());
        assertThat(tokens).filteredOn(token -> token.getFamilyId().equals(independentFamilyId))
                .isNotEmpty()
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNull());

        // The unrelated session remains usable after the compromised family is revoked.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(independentToken)))
                .andExpect(status().isOk());
    }

    private void registerUser(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Postgres Integration"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String jsonPath(String json, String expression) {
        return com.jayway.jsonpath.JsonPath.read(json, expression);
    }

    private RefreshToken onlyActiveToken() {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getRevokedAt() == null)
                .toList();
        assertThat(activeTokens).hasSize(1);
        return activeTokens.getFirst();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is missing: " + name);
        }
        return value;
    }
}

package com.onmyway.controllers;

import com.onmyway.auth.JwtService;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import com.onmyway.data.entities.UserStatus;
import com.onmyway.data.repositories.PlaceRepository;
import com.onmyway.data.repositories.RefreshTokenRepository;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "omw.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class AuthControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    JwtService jwtService;

    @BeforeEach
    void cleanUsers() {
        placeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registersUserAndReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Test@Example.COM",
                                  "password": "password123",
                                  "displayName": "Alice"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasItem(Role.USER.name())));

        User savedUser = userRepository.findByEmailIgnoreCase("test@example.com").orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    void registersOrganizerAndReturnsOrganizerRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "organizer@example.com",
                                  "password": "password123",
                                  "displayName": "Organizer",
                                  "accountType": "ORGANIZER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", hasItem(Role.ORGANIZER.name())));

        User savedUser = userRepository.findByEmailIgnoreCase("organizer@example.com").orElseThrow();
        assertThat(savedUser.getRoles()).contains(Role.ORGANIZER);
        assertThat(savedUser.getRoles()).doesNotContain(Role.ADMIN, Role.MODERATOR);
    }

    @Test
    void registersOrganizerWhenAccountTypeIsLowercase() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "lowercase-organizer@example.com",
                                  "password": "password123",
                                  "displayName": "Lowercase Organizer",
                                  "accountType": "organizer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", hasItem(Role.ORGANIZER.name())));

        User savedUser = userRepository.findByEmailIgnoreCase("lowercase-organizer@example.com").orElseThrow();
        assertThat(savedUser.getRoles()).contains(Role.ORGANIZER);
        assertThat(savedUser.getRoles()).doesNotContain(Role.ADMIN, Role.MODERATOR);
    }

    @Test
    void registersUserWhenAccountTypeIsBlank() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "blank-account-type@example.com",
                                  "password": "password123",
                                  "displayName": "Default User",
                                  "accountType": ""
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", hasItem(Role.USER.name())));

        User savedUser = userRepository.findByEmailIgnoreCase("blank-account-type@example.com").orElseThrow();
        assertThat(savedUser.getRoles()).contains(Role.USER);
        assertThat(savedUser.getRoles()).doesNotContain(Role.ORGANIZER, Role.ADMIN, Role.MODERATOR);
    }

    @Test
    void rejectsPrivilegedRoleDuringPublicRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@example.com",
                                  "password": "password123",
                                  "displayName": "Admin",
                                  "accountType": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Account type must be USER or ORGANIZER"));

        assertThat(userRepository.findByEmailIgnoreCase("admin@example.com")).isEmpty();
    }

    @Test
    void rejectsModeratorRoleDuringPublicRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "moderator@example.com",
                                  "password": "password123",
                                  "displayName": "Moderator",
                                  "accountType": "MODERATOR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Account type must be USER or ORGANIZER"));

        assertThat(userRepository.findByEmailIgnoreCase("moderator@example.com")).isEmpty();
    }

    @Test
    void logsInWithValidCredentialsAndReturnsJwt() throws Exception {
        registerUser("test@example.com", "password123", "Alice");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "TEST@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Alice"))
                .andExpect(jsonPath("$.user.roles").isArray())
                .andExpect(jsonPath("$.user.roles", hasItem(Role.USER.name())));
    }

    @Test
    void allowsValidLoginAfterSuspiciousFailuresWhenCaptchaIsNotConfigured() throws Exception {
        registerUser("captcha-not-configured@example.com", "password123", "Alice");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> { request.setRemoteAddr("192.0.2.99"); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "captcha-not-configured@example.com",
                                      "password": "wrong-password"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr("192.0.2.99"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "captcha-not-configured@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void returnsCurrentUserWithValidJwt() throws Exception {
        registerUser("test@example.com", "password123", "Alice");
        String token = jwtService.generateToken(userRepository.findByEmailIgnoreCase("test@example.com").orElseThrow().getId());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasItem(Role.USER.name())));
    }

    @Test
    void rejectsCurrentUserWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCurrentUserWithInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCurrentUserForBlockedUser() throws Exception {
        User user = registerUser("test@example.com", "password123", "Alice");
        user.setStatus(UserStatus.BLOCKED);
        userRepository.saveAndFlush(user);
        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidLoginCredentials() throws Exception {
        registerUser("test@example.com", "password123", "Alice");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void progressivelyDelaysRepeatedLoginAttemptsForUnknownEmailWithoutHardLockout() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"rate-limit-unknown@example.com","password":"wrong-password"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"rate-limit-unknown@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }
    @Test
    void concurrentRegistrationWithSameEmailCreatesOnlyOneUser() throws Exception {
        String body = """
                {"email":"concurrent-register@example.com","password":"password123","displayName":"Concurrent"}
                """;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent registration");
                }
                return mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(request);
            Future<Integer> second = executor.submit(request);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 400);
            assertThat(userRepository.findAll().stream()
                    .filter(user -> user.getEmail().equals("concurrent-register@example.com")))
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsBlockedUserLogin() throws Exception {
        User user = registerUser("test@example.com", "password123", "Alice");
        user.setStatus(UserStatus.BLOCKED);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        User existingUser = new User();
        existingUser.setEmail("test@example.com");
        existingUser.setPasswordHash("encoded-password");
        existingUser.setDisplayName("Existing");
        existingUser.getRoles().add(Role.USER);
        userRepository.saveAndFlush(existingUser);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "TEST@example.com",
                                  "password": "password123",
                                  "displayName": "Alice"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @Test
    void rejectsInvalidRegistrationRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "password": "1234567",
                                  "displayName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Email is required"));
    }

    @Test
    void refreshRotatesTokenAndRejectsPreviouslyUsedToken() throws Exception {
        registerUser("refresh@example.com", "password123", "Refresh User");

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "refresh@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn().getResponse().getContentAsString();
        String originalRefreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.refreshToken");

        String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(originalRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.email").value("refresh@example.com"))
                .andReturn().getResponse().getContentAsString();
        String rotatedRefreshToken = com.jayway.jsonpath.JsonPath.read(refreshResponse, "$.refreshToken");
        org.assertj.core.api.Assertions.assertThat(rotatedRefreshToken).isNotEqualTo(originalRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(originalRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        registerUser("logout@example.com", "password123", "Logout User");
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "logout@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyOneConcurrentRefreshRequestCanRotateTheSameToken() throws Exception {
        registerUser("concurrent-refresh@example.com", "password123", "Concurrent Refresh");
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"concurrent-refresh@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.refreshToken");
        String body = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);

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

            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsRefreshRequestWithoutTokenAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Refresh token is required"));
    }

    @Test
    void refreshRejectsAndRevokesTokenWhenUserIsBlocked() throws Exception {
        User user = registerUser("blocked-refresh@example.com", "password123", "Blocked User");
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"blocked-refresh@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = com.jayway.jsonpath.JsonPath.read(loginResponse, "$.refreshToken");

        user.setStatus(UserStatus.BLOCKED);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.findAll()).filteredOn(token -> token.getUser().getId().equals(user.getId()))
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }

    private User registerUser(String email, String password, String displayName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "displayName": "%s"
                                }
                                """.formatted(email, password, displayName)))
                .andExpect(status().isCreated());

        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}

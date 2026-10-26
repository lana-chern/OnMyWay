package com.onmyway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "omw.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class SecurityAccessRulesTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void allowsPublicReadEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/cities"))
                .andExpect(status().isOk());

        // Missing cityId proves the request reached MVC validation rather than being rejected by security.
        mockMvc.perform(get("/api/places"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk());
    }

    @Test
    void requiresAuthenticationForCurrentUserAndWriteEndpoints() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/places"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/places/1/manage"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/moderation/places/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthenticationForUnlistedApiRoutesByDefault() throws Exception {
        mockMvc.perform(get("/api/not-configured"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/not-configured"))
                .andExpect(status().isUnauthorized());
    }
}

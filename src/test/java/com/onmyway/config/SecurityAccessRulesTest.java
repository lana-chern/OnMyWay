package com.onmyway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

        mockMvc.perform(get("/api/places"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing request parameter"))
                .andExpect(jsonPath("$.detail").value("Required request parameter 'cityId' is missing."));

        mockMvc.perform(get("/api/places").param("cityId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameter"))
                .andExpect(jsonPath("$.detail").value("Request parameter 'cityId' has an invalid value."));

        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsPublicDetailReadsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/cities/999999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/places/999999"))
                .andExpect(status().isNotFound());
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
    void requiresAuthenticationForAllPlaceMutationAndModerationRoutes() throws Exception {
        mockMvc.perform(put("/api/places/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/places/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/places/1/submit-for-moderation"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/moderation/places/1/publish"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/moderation/places/1/reject"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRegularUserCannotUseOrganizerOrModerationRoutes() throws Exception {
        var regularUser = user("regular-user@example.com").roles("USER");

        mockMvc.perform(post("/api/places").with(regularUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/places/1").with(regularUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/places/1").with(regularUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/places/1/submit-for-moderation").with(regularUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/places/1/manage").with(regularUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/moderation/places/pending").with(regularUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/moderation/places/1/publish").with(regularUser))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/moderation/places/1/reject").with(regularUser))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicReadMatchersDoNotMakeOtherMethodsPublic() throws Exception {
        mockMvc.perform(post("/api/cities"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/places/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/login"))
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

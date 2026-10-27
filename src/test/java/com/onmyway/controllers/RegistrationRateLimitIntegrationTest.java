package com.onmyway.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "omw.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "omw.auth.registration-max-attempts=2"
})
class RegistrationRateLimitIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void rejectsRegistrationOverHttpAfterClientIpLimitIsReached() throws Exception {
        String clientIp = "203.0.113.88";

        register("first-registration@example.com", clientIp)
                .andExpect(status().isCreated());
        register("second-registration@example.com", clientIp)
                .andExpect(status().isCreated());

        register("third-registration@example.com", clientIp)
                .andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String clientIp)
            throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .with(request -> { request.setRemoteAddr(clientIp); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "password123",
                          "displayName": "Rate Limit Test"
                        }
                        """.formatted(email)));
    }
}

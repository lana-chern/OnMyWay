package com.onmyway.controllers;

import com.onmyway.data.entities.User;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void cleanUsers() {
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
                .andExpect(jsonPath("$.roles[0]").value("USER"));

        User savedUser = userRepository.findByEmailIgnoreCase("test@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(savedUser.getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        User existingUser = new User();
        existingUser.setEmail("test@example.com");
        existingUser.setPasswordHash("encoded-password");
        existingUser.setDisplayName("Existing");
        existingUser.getRoles().add(com.onmyway.data.entities.Role.USER);
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
}

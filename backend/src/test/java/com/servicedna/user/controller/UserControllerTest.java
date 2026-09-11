package com.servicedna.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.domain.EmailChangeToken;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.auth.repository.EmailChangeTokenRepository;
import com.servicedna.auth.dto.LoginRequest;
import com.servicedna.auth.domain.EmailVerificationToken;
import com.servicedna.auth.repository.EmailVerificationTokenRepository;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private EmailChangeTokenRepository emailChangeTokenRepository;

    private String registerAndVerify(String email, String password) throws Exception {
        String res = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, password))))
                .andReturn().getResponse().getContentAsString();

        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token =
                emailVerificationTokenRepository.findByUserId(user.getId()).orElseThrow();
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", token.getToken()))
                .andExpect(status().isOk());

        return objectMapper.readTree(res).get("token").asText();
    }

    @Test
    void shouldChangePasswordAndRejectWrongCurrentPassword() throws Exception {
        String email = "changepass-" + UUID.randomUUID() + "@example.com";
        String token = registerAndVerify(email, "password123");

        mockMvc.perform(post("/api/v1/users/me/change-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrongpassword\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD"));

        mockMvc.perform(post("/api/v1/users/me/change-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, "newpassword456"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRequestAndConfirmEmailChange() throws Exception {
        String oldEmail = "oldemail-" + UUID.randomUUID() + "@example.com";
        String newEmail = "newemail-" + UUID.randomUUID() + "@example.com";
        String token = registerAndVerify(oldEmail, "password123");

        // Wrong password is rejected.
        mockMvc.perform(post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"wrongpassword\",\"newEmail\":\"" + newEmail + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD"));

        mockMvc.perform(post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password123\",\"newEmail\":\"" + newEmail + "\"}"))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(oldEmail).orElseThrow();
        EmailChangeToken changeToken = emailChangeTokenRepository.findByUserId(user.getId()).orElseThrow();

        // Invalid confirmation token is rejected.
        mockMvc.perform(get("/api/v1/users/me/change-email/confirm").param("token", "not-a-real-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        mockMvc.perform(get("/api/v1/users/me/change-email/confirm").param("token", changeToken.getToken()))
                .andExpect(status().isOk());

        // Old email no longer works (authentication fails before any lookup succeeds), new email does.
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(oldEmail, "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(newEmail, "password123"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectEmailChangeToAnAlreadyRegisteredEmail() throws Exception {
        String email1 = "taken1-" + UUID.randomUUID() + "@example.com";
        String email2 = "taken2-" + UUID.randomUUID() + "@example.com";
        String token1 = registerAndVerify(email1, "password123");
        registerAndVerify(email2, "password123");

        mockMvc.perform(post("/api/v1/users/me/change-email/request")
                .header("Authorization", "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password123\",\"newEmail\":\"" + email2 + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_IN_USE"));
    }
}

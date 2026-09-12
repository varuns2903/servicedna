package com.servicedna.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.domain.EmailVerificationToken;
import com.servicedna.auth.domain.PasswordResetToken;
import com.servicedna.auth.dto.LoginRequest;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.auth.repository.EmailVerificationTokenRepository;
import com.servicedna.auth.repository.PasswordResetTokenRepository;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private String verificationTokenFor(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token =
                emailVerificationTokenRepository.findByUserId(user.getId()).orElseThrow();
        return token.getToken();
    }

    @Test
    void shouldRegisterUserAndLogin() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("test@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.role").value("OWNER"));

        // A freshly registered user must verify their email before they can log in again.
        mockMvc.perform(get("/api/v1/auth/verify-email")
                .param("token", verificationTokenFor("test@example.com")))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");

        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(responseJson).get("token").asText();

        // Access protected route
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void shouldReturnCurrentUserWithoutPasswordHash() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("me-endpoint@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/auth/verify-email")
                .param("token", verificationTokenFor("me-endpoint@example.com")))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest("me-endpoint@example.com", "password123");

        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginJson).get("token").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me-endpoint@example.com"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldBlockLoginForUnverifiedEmail() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("unverified@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("unverified@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void shouldRejectInvalidVerificationToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", "not-a-real-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void shouldFailRegistrationWithDuplicateEmail() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("duplicate@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_EXISTS"));
    }

    @Test
    void shouldFailLoginWithWrongPassword() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("wrongpass@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("wrongpass@example.com", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldResendVerificationAndInvalidateOldToken() throws Exception {
        String email = "resend-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andExpect(status().isCreated());

        String oldToken = verificationTokenFor(email);

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        // The old token was invalidated by the resend.
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", oldToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));

        // The new token works.
        String newToken = verificationTokenFor(email);
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", newToken))
                .andExpect(status().isOk());

        // Resending for an already-verified account is a silent no-op (non-enumeration stance).
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        // Resending for an unknown email is also a silent no-op.
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"no-such-account@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRefreshTokenAndRotateOnUse() throws Exception {
        String email = "refresh@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", verificationTokenFor(email)))
                .andExpect(status().isOk());

        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginJson).get("refreshToken").asText();

        String refreshedJson = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = objectMapper.readTree(refreshedJson).get("token").asText();
        String newRefreshToken = objectMapper.readTree(refreshedJson).get("refreshToken").asText();

        // The new access token works.
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());

        // The old refresh token is single-use — it was rotated away by the refresh above.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        // Logout revokes the current refresh token too.
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefreshToken + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void shouldRejectUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden()); // Spring Security by default returns 403 when access is denied for an unauthenticated request without specific configuration mapping it to 401
    }

    @Test
    void forgotPasswordShouldReturnOkRegardlessOfWhetherEmailExists() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("forgot@example.com", "password123");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Same response whether the account exists or not, so a caller can't enumerate accounts.
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"forgot@example.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"no-such-account@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldResetPasswordAndRejectTokenReuse() throws Exception {
        String email = "reset@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        PasswordResetToken resetToken =
                passwordResetTokenRepository.findByUserId(user.getId()).orElseThrow();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + resetToken.getToken() + "\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isOk());

        // Old password no longer works.
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isUnauthorized());

        // New password works (need to verify email first, same as any account).
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", verificationTokenFor(email)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, "newpassword456"))))
                .andExpect(status().isOk());

        // The reset token cannot be used a second time.
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + resetToken.getToken() + "\",\"newPassword\":\"anotherpassword789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_USED"));
    }
}

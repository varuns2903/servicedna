package com.servicedna.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Client mistakes must come back as 4xx with an errorCode, never as a generic 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void malformedJsonReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ping")
                .header("X-API-Key", "any")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
  }

  @Test
  void unknownEnumValueReturns400NamingTheField() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ping")
                .header("X-API-Key", "any")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ON_FIRE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"))
        .andExpect(jsonPath("$.message").value("Invalid value 'ON_FIRE' for field 'status'."));
  }

  @Test
  void missingRequiredHeaderReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ping")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"HEALTHY\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
  }

  @Test
  void unsupportedMethodReturns405() throws Exception {
    mockMvc
        .perform(get("/api/v1/ping"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void invalidPathUuidReturns400() throws Exception {
    String res =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RegisterRequest(
                                "handler-" + UUID.randomUUID() + "@example.com", "password123"))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = objectMapper.readTree(res).get("token").asText();

    mockMvc
        .perform(
            get("/api/v1/organizations/not-a-uuid/services")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
  }
}

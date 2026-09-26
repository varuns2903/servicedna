package com.servicedna.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.auth.token.ApiToken;
import com.servicedna.auth.token.ApiTokenRepository;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiTokenTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ApiTokenRepository repository;

  private String session;
  private String orgId;

  @BeforeEach
  void setUp() throws Exception {
    session = json(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RegisterRequest("pat-" + UUID.randomUUID() + "@example.com", "password123")))))
        .get("token").asText();
    orgId = json(mockMvc.perform(as(session, post("/api/v1/organizations"))
        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Token Org"))))).get("id").asText();
  }

  @Test
  void aTokenActsAsItsUserUntilRevoked() throws Exception {
    JsonNode created = json(mockMvc.perform(as(session, post("/api/v1/users/me/tokens")).content("{\"name\":\"CI\",\"expiresInDays\":90}"))
        .andExpect(status().isCreated()));
    String token = created.get("token").asText();
    assertThat(token).startsWith("sdna_pat_");
    assertThat(created.get("prefix").asText()).isEqualTo(token.substring(0, 13));

    mockMvc.perform(as(token, get("/api/v1/organizations/" + orgId + "/services"))).andExpect(status().isOk());
    // The list never shows the token again, but shows when it was used.
    JsonNode listed = json(mockMvc.perform(as(session, get("/api/v1/users/me/tokens"))));
    assertThat(listed.get(0).get("token").isNull()).isTrue();
    assertThat(listed.get(0).get("lastUsedAt").isNull()).isFalse();

    // A token can't mint more tokens.
    mockMvc.perform(as(token, post("/api/v1/users/me/tokens")).content("{\"name\":\"more\"}"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SESSION_REQUIRED"));

    mockMvc.perform(as(session, delete("/api/v1/users/me/tokens/" + created.get("id").asText()))).andExpect(status().isNoContent());
    assertThat(mockMvc.perform(as(token, get("/api/v1/organizations/" + orgId + "/services"))).andReturn().getResponse().getStatus())
        .isIn(401, 403);
  }

  @Test
  void expiredAndUnknownTokensAreRejected() throws Exception {
    UUID userId = UUID.fromString(json(mockMvc.perform(as(session, get("/api/v1/users/me")))).get("id").asText());
    String raw = "sdna_pat_expiredexpiredexpiredexpiredexpired0";
    repository.save(new ApiToken(userId, "old", hashOf(raw), raw.substring(0, 13), OffsetDateTime.now().minusDays(1)));
    for (String t : new String[] {raw, "sdna_pat_nope"}) {
      assertThat(mockMvc.perform(as(t, get("/api/v1/organizations/" + orgId + "/services"))).andReturn().getResponse().getStatus())
          .isIn(401, 403);
    }
    mockMvc.perform(as(session, post("/api/v1/users/me/tokens")).content("{\"name\":\"\"}")).andExpect(status().isBadRequest());
  }

  private static String hashOf(String raw) throws Exception {
    return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private MockHttpServletRequestBuilder as(String bearer, MockHttpServletRequestBuilder request) {
    return request.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + bearer);
  }

  private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }
}

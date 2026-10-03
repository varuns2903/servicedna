package com.servicedna.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"AUTH_SIGNUP=invite-only", "AUTH_SIGNUP_DOMAINS=acme.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InviteOnlySignupTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void onlyAllowedDomainsAndInviteesCanSignUp() throws Exception {
    assertThat(json(mockMvc.perform(get("/api/v1/auth/sso-config")).andReturn()).get("signup").asText()).isEqualTo("invite-only");

    // An allowed domain signs up and creates an organization.
    MvcResult admin = register("admin-" + UUID.randomUUID() + "@acme.test");
    assertThat(admin.getResponse().getStatus()).isEqualTo(201);
    String token = json(admin).get("token").asText();
    String org = json(mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}")).andReturn()).get("id").asText();

    // A stranger can't.
    String guest = "guest-" + UUID.randomUUID() + "@partner.test";
    MvcResult refused = register(guest);
    assertThat(refused.getResponse().getStatus()).isEqualTo(403);
    assertThat(json(refused).get("errorCode").asText()).isEqualTo("SIGNUP_CLOSED");

    // Once invited, they can.
    int invited = mockMvc.perform(post("/api/v1/organizations/" + org + "/invites").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + guest + "\",\"role\":\"MEMBER\"}")).andReturn().getResponse().getStatus();
    assertThat(invited).isEqualTo(201);
    assertThat(register(guest.toUpperCase()).getResponse().getStatus()).isEqualTo(201);
  }

  private MvcResult register(String email) throws Exception {
    return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn();
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}

package com.servicedna.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.ingestion.dto.CreateIngestionKeyRequest;
import com.servicedna.ingestion.service.IngestionKeyService;
import com.servicedna.organization.domain.OrganizationMember;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.organization.repository.OrganizationMemberRepository;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IngestionKeyTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private IngestionKeyService ingestionKeyService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private OrganizationMemberRepository memberRepository;
  @Autowired private UserRepository userRepository;

  private String token;
  private String orgId;

  @BeforeEach
  void setUp() throws Exception {
    token = register();
    orgId =
        json(
                mockMvc.perform(
                    post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Keys Org")))))
            .get("id")
            .asText();
  }

  @Test
  void keyIsShownOnceAndAuthenticatesToItsOrganization() throws Exception {
    JsonNode created = createKey(token, "prod collector");
    String raw = created.get("key").asText();
    assertThat(raw).startsWith("sdna_ik_");
    assertThat(created.get("keyPrefix").asText()).isEqualTo(raw.substring(0, 14));

    mockMvc
        .perform(get(keysPath()).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$[0].name").value("prod collector"))
        .andExpect(jsonPath("$[0].key").doesNotExist());

    assertThat(ingestionKeyService.authenticate(raw)).contains(UUID.fromString(orgId));
    assertThat(ingestionKeyService.authenticate(raw + "x")).isEmpty();
    assertThat(ingestionKeyService.authenticate("not-a-key")).isEmpty();
    assertThat(ingestionKeyService.authenticate(null)).isEmpty();
  }

  @Test
  void revokedKeysStopAuthenticating() throws Exception {
    JsonNode created = createKey(token, "old key");

    mockMvc
        .perform(delete(keysPath() + "/" + created.get("id").asText()).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revokedAt").exists());

    assertThat(ingestionKeyService.authenticate(created.get("key").asText())).isEmpty();
  }

  @Test
  void membersCannotCreateKeys() throws Exception {
    String memberEmail = "keys-" + UUID.randomUUID() + "@example.com";
    String memberToken = register(memberEmail);
    var memberUser = userRepository.findByEmail(memberEmail).orElseThrow();
    memberRepository.save(
        new OrganizationMember(
            UUID.randomUUID(),
            organizationRepository.findById(UUID.fromString(orgId)).orElseThrow(),
            memberUser,
            OrganizationRole.MEMBER));

    mockMvc
        .perform(
            post(keysPath())
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateIngestionKeyRequest("nope"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
  }

  private String keysPath() {
    return "/api/v1/organizations/" + orgId + "/ingestion-keys";
  }

  private JsonNode createKey(String bearer, String name) throws Exception {
    return json(
        mockMvc
            .perform(
                post(keysPath())
                    .header("Authorization", "Bearer " + bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreateIngestionKeyRequest(name))))
            .andExpect(status().isCreated()));
  }

  private String register() throws Exception {
    return register("keys-" + UUID.randomUUID() + "@example.com");
  }

  private String register(String email) throws Exception {
    return json(
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RegisterRequest(email, "password123")))))
        .get("token")
        .asText();
  }

  private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }
}

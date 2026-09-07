package com.servicedna.organization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.dto.CreateInviteRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationInviteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private com.servicedna.organization.repository.OrganizationInviteRepository inviteRepository;

    private String user1Token;
    private String user2Token;
    private String user2Email;
    private String orgId;

    @BeforeEach
    void setUp() throws Exception {
        String user1Email = "inviteadmin-" + UUID.randomUUID() + "@example.com";
        user2Email = "invitee-" + UUID.randomUUID() + "@example.com";

        // Register User 1
        String res1 = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(user1Email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        user1Token = objectMapper.readTree(res1).get("token").asText();

        // Register User 2
        String res2 = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(user2Email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        user2Token = objectMapper.readTree(res2).get("token").asText();

        // User 1 creates Org
        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Invite Org"))))
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();
    }

    @Test
    void shouldCreateAndAcceptInvite() throws Exception {
        CreateInviteRequest req = new CreateInviteRequest(user2Email, OrganizationRole.MEMBER);

        // User 1 invites User 2
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/invites")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        String token = inviteRepository.findAll().get(0).getToken();

        // User 2 accepts
        mockMvc.perform(post("/api/v1/invites/" + token + "/accept")
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk());

        // Verify User 2 is member
        mockMvc.perform(get("/api/v1/organizations")
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orgId));
    }
}

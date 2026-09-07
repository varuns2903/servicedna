package com.servicedna.organization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.dto.AddMemberRequest;
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
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String user1Token;
    private String user2Token;
    private String user1Email;
    private String user2Email;

    @BeforeEach
    void setUp() throws Exception {
        user1Email = "user1-" + UUID.randomUUID() + "@example.com";
        user2Email = "user2-" + UUID.randomUUID() + "@example.com";

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
    }

    @Test
    void shouldCreateOrganizationAndListThem() throws Exception {
        CreateOrganizationRequest createRequest = new CreateOrganizationRequest("My Startup");

        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("My Startup"))
                .andReturn().getResponse().getContentAsString();

        String orgId = objectMapper.readTree(orgRes).get("id").asText();

        mockMvc.perform(get("/api/v1/organizations")
                .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orgId));
                
        // User 2 should not see User 1's organization
        mockMvc.perform(get("/api/v1/organizations")
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldAddMemberToOrganization() throws Exception {
        // User 1 creates org
        CreateOrganizationRequest createRequest = new CreateOrganizationRequest("Acme Corp");
        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        
        String orgId = objectMapper.readTree(orgRes).get("id").asText();

        // User 1 adds User 2 as MEMBER
        AddMemberRequest addRequest = new AddMemberRequest(user2Email, OrganizationRole.MEMBER);
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(user2Email))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        // User 2 should now see the organization
        mockMvc.perform(get("/api/v1/organizations")
                .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orgId));
    }
    
    @Test
    void viewerShouldNotBeAbleToAddMembers() throws Exception {
        // User 1 creates org
        CreateOrganizationRequest createRequest = new CreateOrganizationRequest("Viewer Test Corp");
        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        String orgId = objectMapper.readTree(orgRes).get("id").asText();

        // User 1 adds User 2 as VIEWER
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest(user2Email, OrganizationRole.VIEWER))))
                .andExpect(status().isCreated());
                
        // Register User 3
        String user3Email = "user3-" + UUID.randomUUID() + "@example.com";
        String res3 = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(user3Email, "password123"))))
                .andReturn().getResponse().getContentAsString();
                
        // User 2 (VIEWER) tries to add User 3
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                .header("Authorization", "Bearer " + user2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest(user3Email, OrganizationRole.MEMBER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}

package com.servicedna.oncall.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.domain.OrganizationRole;
import com.servicedna.organization.dto.AddMemberRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnCallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String ownerToken;
    private String memberToken;
    private String orgId;
    private String ownerMemberId;
    private String memberMemberId;

    @BeforeEach
    void setUp() throws Exception {
        String ownerEmail = "oncall-owner-" + UUID.randomUUID() + "@example.com";
        String memberEmail = "oncall-member-" + UUID.randomUUID() + "@example.com";

        ownerToken = registerAndGetToken(ownerEmail);
        memberToken = registerAndGetToken(memberEmail);

        String orgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("On-Call Org"))))
                .andReturn().getResponse().getContentAsString();
        orgId = objectMapper.readTree(orgRes).get("id").asText();

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest(memberEmail, OrganizationRole.MEMBER))));

        String membersRes = mockMvc.perform(get("/api/v1/organizations/" + orgId + "/members")
                .header("Authorization", "Bearer " + ownerToken))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode node : objectMapper.readTree(membersRes)) {
            if (node.get("role").asText().equals("OWNER")) {
                ownerMemberId = node.get("id").asText();
            } else {
                memberMemberId = node.get("id").asText();
            }
        }
    }

    private String registerAndGetToken(String email) throws Exception {
        String res = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }

    @Test
    void shouldReturnEmptyRotationByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(0))
                .andExpect(jsonPath("$.currentOnCall").doesNotExist());
    }

    @Test
    void memberCannotEditRotationButCanView() throws Exception {
        String body = "{\"rotationLengthDays\":7,\"startDate\":\"" + LocalDate.now() + "\","
                + "\"organizationMemberIds\":[\"" + ownerMemberId + "\"]}";

        mockMvc.perform(put("/api/v1/organizations/" + orgId + "/on-call")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call")
                .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldComputeCurrentOnCallFromRotationDayMath() throws Exception {
        // Rotation started exactly one full 7-day period ago with [owner, member] in that
        // order: day 7 into the rotation means period index 1, so "member" (index 1) should be
        // on call now — proving the day-math, not just that the config round-trips.
        String startDate = LocalDate.now().minusDays(7).toString();
        String body = "{\"rotationLengthDays\":7,\"startDate\":\"" + startDate + "\","
                + "\"organizationMemberIds\":[\"" + ownerMemberId + "\",\"" + memberMemberId + "\"]}";

        mockMvc.perform(put("/api/v1/organizations/" + orgId + "/on-call")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.currentOnCall.organizationMemberId").value(memberMemberId))
                .andExpect(jsonPath("$.currentShiftEndsOn").value(LocalDate.now().plusDays(7).toString()));

        // Reordering flips who's on call for the same elapsed time.
        String reordered = "{\"rotationLengthDays\":7,\"startDate\":\"" + startDate + "\","
                + "\"organizationMemberIds\":[\"" + memberMemberId + "\",\"" + ownerMemberId + "\"]}";
        mockMvc.perform(put("/api/v1/organizations/" + orgId + "/on-call")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reordered))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOnCall.organizationMemberId").value(ownerMemberId));
    }

    @Test
    void shouldRejectMemberIdFromAnotherOrganization() throws Exception {
        String outsiderToken = registerAndGetToken("oncall-outsider-" + UUID.randomUUID() + "@example.com");
        String otherOrgRes = mockMvc.perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("Other Org"))))
                .andReturn().getResponse().getContentAsString();
        String otherOrgId = objectMapper.readTree(otherOrgRes).get("id").asText();

        String outsiderMembersRes = mockMvc.perform(get("/api/v1/organizations/" + otherOrgId + "/members")
                .header("Authorization", "Bearer " + outsiderToken))
                .andReturn().getResponse().getContentAsString();
        String outsiderMemberId = objectMapper.readTree(outsiderMembersRes).get(0).get("id").asText();

        String body = "{\"rotationLengthDays\":7,\"startDate\":\"" + LocalDate.now() + "\","
                + "\"organizationMemberIds\":[\"" + outsiderMemberId + "\"]}";
        mockMvc.perform(put("/api/v1/organizations/" + orgId + "/on-call")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MEMBER"));
    }
}

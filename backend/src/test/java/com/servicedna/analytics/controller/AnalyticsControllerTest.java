package com.servicedna.analytics.controller;

import com.servicedna.ServiceDnaApplication;
import com.servicedna.auth.security.JwtService;
import com.servicedna.organization.domain.Organization;
import com.servicedna.organization.repository.OrganizationMemberRepository;
import com.servicedna.organization.repository.OrganizationRepository;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ServiceDnaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private Organization testOrg;
    private String authToken;

    @BeforeEach
    void setUp() {
        organizationMemberRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(new User(
                UUID.randomUUID(),
                "analytics" + UUID.randomUUID() + "@test.com",
                "hash",
                com.servicedna.user.domain.Role.OWNER
        ));

        testOrg = organizationRepository.save(new Organization(
                UUID.randomUUID(),
                "Analytics Org"
        ));

        organizationMemberRepository.save(new com.servicedna.organization.domain.OrganizationMember(
                UUID.randomUUID(),
                testOrg,
                testUser,
                com.servicedna.organization.domain.OrganizationRole.ADMIN
        ));

        authToken = jwtService.generateToken(testUser.getEmail(), testUser.getRole().name());
    }

    @Test
    void shouldGetSlaReport() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{orgId}/analytics/sla", testOrg.getId())
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(testOrg.getId().toString()))
                .andExpect(jsonPath("$.overallUptimePercentage").value(100.0));
    }
}

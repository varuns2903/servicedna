package com.servicedna.common.interceptor;

import com.servicedna.ServiceDnaApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ServiceDnaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = "rate-limit.max-requests=5")
class RateLimitInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear all rate limit keys before test
        redisTemplate.keys("rate_limit:*").forEach(redisTemplate::delete);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        redisTemplate.keys("rate_limit:*").forEach(redisTemplate::delete);
    }

    @Test
    void shouldLimitRequests() throws Exception {
        // Make 5 requests to an unauthenticated endpoint
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"test@test.com\",\"password\":\"password\"}"))
                    .andExpect(status().isUnauthorized()); // Because user doesn't exist
        }

        // The 101st request should be rate limited
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"test@test.com\",\"password\":\"password\"}"))
                .andExpect(status().isTooManyRequests());
    }
}

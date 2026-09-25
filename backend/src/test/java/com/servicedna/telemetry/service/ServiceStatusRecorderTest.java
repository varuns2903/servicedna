package com.servicedna.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.alert.service.AlertEventPublisher;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.organization.dto.CreateOrganizationRequest;
import com.servicedna.service.domain.ServiceStatus;
import com.servicedna.service.dto.CreateServiceRequest;
import com.servicedna.service.repository.ServiceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "health-check.status-confirmations=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceStatusRecorderTest {

  @Autowired private ServiceStatusRecorder recorder;

  @Autowired private ServiceRepository serviceRepository;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private AlertEventPublisher alertEventPublisher;

  private UUID serviceId;

  @BeforeEach
  void setUp() throws Exception {
    String token =
        readField(
            post("/api/v1/auth/register")
                .content(json(new RegisterRequest("recorder-" + UUID.randomUUID() + "@example.com", "password123"))),
            null,
            "token");
    String orgId =
        readField(
            post("/api/v1/organizations").content(json(new CreateOrganizationRequest("Recorder Org"))),
            token,
            "id");
    serviceId =
        UUID.fromString(
            readField(
                post("/api/v1/organizations/" + orgId + "/services")
                    .content(json(new CreateServiceRequest("recorder-svc", null, null, null, null))),
                token,
                "id"));
  }

  @Test
  void firstObservationAppliesImmediately() {
    assertThat(recorder.record(serviceId, ServiceStatus.HEALTHY, 10, null)).isTrue();
    assertThat(currentStatus()).isEqualTo(ServiceStatus.HEALTHY);
  }

  @Test
  void changeNeedsConsecutiveConfirmations() {
    recorder.record(serviceId, ServiceStatus.HEALTHY, 10, null);

    assertThat(recorder.record(serviceId, ServiceStatus.DEGRADED, 2500, null)).isFalse();
    assertThat(currentStatus()).isEqualTo(ServiceStatus.HEALTHY);

    assertThat(recorder.record(serviceId, ServiceStatus.DEGRADED, 2600, null)).isTrue();
    assertThat(currentStatus()).isEqualTo(ServiceStatus.DEGRADED);
  }

  @Test
  void disagreeingSourcesDoNotFlapTheStatus() {
    recorder.record(serviceId, ServiceStatus.HEALTHY, 10, null);

    for (int i = 0; i < 5; i++) {
      assertThat(recorder.record(serviceId, ServiceStatus.DEGRADED, 2100, null)).isFalse();
      assertThat(recorder.record(serviceId, ServiceStatus.HEALTHY, 1900, null)).isFalse();
    }
    assertThat(currentStatus()).isEqualTo(ServiceStatus.HEALTHY);
  }

  @Test
  void concurrentObservationsOfTheSameChangeAlertOnce() throws Exception {
    recorder.record(serviceId, ServiceStatus.HEALTHY, 10, null);
    recorder.record(serviceId, ServiceStatus.DOWN, null, null); // first of two confirmations

    int threads = 4;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      Callable<Boolean> observeDown =
          () -> {
            start.await();
            return recorder.record(serviceId, ServiceStatus.DOWN, null, null);
          };
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        results.add(pool.submit(observeDown));
      }
      start.countDown();
      long changed = 0;
      for (Future<Boolean> result : results) {
        if (result.get(30, TimeUnit.SECONDS)) {
          changed++;
        }
      }
      assertThat(changed).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    assertThat(currentStatus()).isEqualTo(ServiceStatus.DOWN);
    // HEALTHY (from UNKNOWN) and DOWN: exactly one event per real change.
    verify(alertEventPublisher, times(2)).publishStatusChangedEvent(any());
  }

  private ServiceStatus currentStatus() {
    return serviceRepository.findById(serviceId).orElseThrow().getStatus();
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private String readField(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      String token,
      String field)
      throws Exception {
    request.contentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    String body = mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get(field).asText();
  }
}

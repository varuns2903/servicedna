package com.servicedna.logs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.common.exception.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The log store (Grafana Loki): OTLP batches go in through its OTLP endpoint and come back out
 * through LogQL, always with the organization as tenant (X-Scope-OrgID) so orgs' logs never mix.
 * Disabled when LOG_STORE_URL is unset: batches are accepted and dropped, and searches say so.
 */
@Component
public class LogStore {

  private final String baseUrl;
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public LogStore(@Value("${log-store.url:}") String baseUrl, ObjectMapper json) {
    this.baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.replaceAll("/+$", "");
    this.json = json;
  }

  /** @throws LogStoreUnavailableException if the store can't take the batch right now */
  public void forward(UUID organizationId, byte[] otlpProtobuf) {
    if (baseUrl == null) {
      return;
    }
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/otlp/v1/logs"))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/x-protobuf")
        .header("X-Scope-OrgID", organizationId.toString())
        .POST(HttpRequest.BodyPublishers.ofByteArray(otlpProtobuf))
        .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        throw new LogStoreUnavailableException("log store returned HTTP " + response.statusCode() + ": " + response.body());
      }
    } catch (IOException e) {
      throw new LogStoreUnavailableException(e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LogStoreUnavailableException("interrupted");
    }
  }

  /** GET on Loki's HTTP API as the organization's tenant. */
  JsonNode get(UUID organizationId, String pathAndQuery) {
    if (baseUrl == null) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LOG_STORE_DISABLED", "Log storage isn't configured (LOG_STORE_URL).");
    }
    try {
      HttpResponse<String> res = client.send(
          HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
              .timeout(Duration.ofSeconds(20))
              .header("X-Scope-OrgID", organizationId.toString())
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() == 400) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", res.body().strip());
      }
      if (res.statusCode() >= 300) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "LOG_STORE_ERROR", "Log store returned HTTP " + res.statusCode());
      }
      return json.readTree(res.body());
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "LOG_STORE_ERROR", "Log store unavailable.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "LOG_STORE_ERROR", "Interrupted.");
    }
  }

  public static class LogStoreUnavailableException extends RuntimeException {
    public LogStoreUnavailableException(String message) {
      super(message);
    }
  }
}

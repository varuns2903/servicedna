package com.servicedna.ingestion.otlp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores span batches in the trace store (Grafana Tempo) by forwarding the raw OTLP payload to
 * its OTLP/HTTP receiver, with the organization as Tempo tenant so each org's traces stay
 * separate. Disabled when TRACE_STORE_OTLP_URL is unset.
 */
@Component
public class TraceStoreForwarder {

  private final String endpoint;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public TraceStoreForwarder(@Value("${trace-store.otlp-url:}") String baseUrl) {
    this.endpoint = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.replaceAll("/+$", "") + "/v1/traces";
  }

  public boolean isEnabled() {
    return endpoint != null;
  }

  /** @throws TraceStoreUnavailableException if the store can't accept the batch right now */
  public void forward(UUID organizationId, byte[] otlpProtobuf) {
    if (endpoint == null) {
      return;
    }
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-protobuf")
            .header("X-Scope-OrgID", organizationId.toString())
            .POST(HttpRequest.BodyPublishers.ofByteArray(otlpProtobuf))
            .build();
    try {
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 300) {
        throw new TraceStoreUnavailableException("trace store returned HTTP " + response.statusCode());
      }
    } catch (IOException e) {
      throw new TraceStoreUnavailableException(e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TraceStoreUnavailableException("interrupted");
    }
  }

  public static class TraceStoreUnavailableException extends RuntimeException {
    public TraceStoreUnavailableException(String message) {
      super(message);
    }
  }
}

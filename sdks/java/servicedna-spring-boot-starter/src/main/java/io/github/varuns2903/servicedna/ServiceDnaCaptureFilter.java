package io.github.varuns2903.servicedna;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Records request and response bodies on the server span, masked and size-capped: always for
 * ServiceDNA capture runs (baggage {@code sdna.capture=1}), and — with {@code captureOnError}
 * ({@code SERVICEDNA_CAPTURE_ON_ERROR=true}) — for requests that fail with a 5xx or an exception.
 * Ordered after OpenTelemetry's own filter, so the span and baggage are current. Responses are
 * written straight through; only a capped copy is kept.
 */
public class ServiceDnaCaptureFilter extends OncePerRequestFilter {

  private final boolean captureOnError;

  public ServiceDnaCaptureFilter() {
    this(false);
  }

  public ServiceDnaCaptureFilter(boolean captureOnError) {
    this.captureOnError = captureOnError;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean requested = ServiceDna.captureRequested();
    if (!requested && !captureOnError) {
      chain.doFilter(request, response);
      return;
    }
    ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, ServiceDna.MAX_CAPTURE_BYTES);
    TeeResponseWrapper res = new TeeResponseWrapper(response, ServiceDna.MAX_CAPTURE_BYTES);
    boolean failed = false;
    try {
      chain.doFilter(req, res);
    } catch (IOException | ServletException | RuntimeException e) {
      failed = true;
      throw e;
    } finally {
      res.flushWriter();
      if (requested || failed || res.getStatus() >= 500) {
        Span span = Span.current();
        byte[] in = req.getContentAsByteArray();
        byte[] out = res.copied();
        if (in.length > 0) {
          span.setAttribute("sdna.request.body", ServiceDna.redact(new String(in, StandardCharsets.UTF_8)));
        }
        if (out.length > 0) {
          span.setAttribute("sdna.response.body", ServiceDna.redact(new String(out, StandardCharsets.UTF_8)));
        }
        span.setAttribute(requested ? "sdna.captured" : "sdna.captured_on_error", true);
      }
    }
  }
}

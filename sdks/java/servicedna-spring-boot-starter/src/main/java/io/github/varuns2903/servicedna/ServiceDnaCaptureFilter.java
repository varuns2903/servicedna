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
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Records request and response bodies on the server span for ServiceDNA capture runs (baggage
 * {@code sdna.capture=1}), masked and size-capped. Ordered after OpenTelemetry's own filter, so the
 * span and baggage are current. Other requests pass straight through.
 */
public class ServiceDnaCaptureFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!ServiceDna.captureRequested()) {
      chain.doFilter(request, response);
      return;
    }
    ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, ServiceDna.MAX_CAPTURE_BYTES);
    ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
    try {
      chain.doFilter(req, res);
    } finally {
      Span span = Span.current();
      byte[] in = req.getContentAsByteArray();
      byte[] out = res.getContentAsByteArray();
      if (in.length > 0) {
        span.setAttribute("sdna.request.body", ServiceDna.redact(new String(in, StandardCharsets.UTF_8)));
      }
      if (out.length > 0) {
        span.setAttribute("sdna.response.body", ServiceDna.redact(new String(out, StandardCharsets.UTF_8)));
      }
      span.setAttribute("sdna.captured", true);
      res.copyBodyToResponse();
    }
  }
}

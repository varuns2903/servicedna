package io.github.varuns2903.servicedna;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

/** Drops server spans for health-check requests (ServiceDNA's probes and the heartbeat). */
final class HealthCheckDroppingSampler implements Sampler {

  private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");

  private final Sampler delegate;
  private final String healthPath;

  HealthCheckDroppingSampler(Sampler delegate, String healthPath) {
    this.delegate = delegate;
    this.healthPath = healthPath;
  }

  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {
    if (spanKind == SpanKind.SERVER) {
      String path = attributes.get(URL_PATH);
      if (path != null && (path.equals(healthPath) || path.startsWith(healthPath + "/"))) {
        return SamplingResult.drop();
      }
    }
    return delegate.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
  }

  @Override
  public String getDescription() {
    return "HealthCheckDropping{" + healthPath + "," + delegate.getDescription() + "}";
  }
}

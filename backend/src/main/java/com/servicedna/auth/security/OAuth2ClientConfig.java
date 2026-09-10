package com.servicedna.auth.security;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Builds the OAuth2/OIDC client registrations by hand instead of via
 * spring.security.oauth2.client.* properties, because a generic OIDC registration built from an
 * issuer-uri triggers a blocking discovery call (GET {issuer}/.well-known/openid-configuration)
 * at registration-build time. Letting Spring Boot's property-based autoconfiguration own that
 * would mean an unset/misconfigured OIDC_ISSUER_URI breaks application startup entirely, even
 * though SSO is meant to be optional. Building it here means the discovery call — and any
 * failure from it — only happens when an operator has actually set OIDC_ISSUER_URI.
 */
@Configuration
public class OAuth2ClientConfig {

  private static final Logger log = LoggerFactory.getLogger(OAuth2ClientConfig.class);

  @Bean
  public ClientRegistrationRepository clientRegistrationRepository(
      @Value("${GITHUB_CLIENT_ID:dummy-id}") String githubClientId,
      @Value("${GITHUB_CLIENT_SECRET:dummy-secret}") String githubClientSecret,
      @Value("${OIDC_CLIENT_ID:}") String oidcClientId,
      @Value("${OIDC_CLIENT_SECRET:}") String oidcClientSecret,
      @Value("${OIDC_ISSUER_URI:}") String oidcIssuerUri) {

    List<ClientRegistration> registrations = new ArrayList<>();
    registrations.add(
        CommonOAuth2Provider.GITHUB
            .getBuilder("github")
            .clientId(githubClientId)
            .clientSecret(githubClientSecret)
            .build());

    if (!oidcIssuerUri.isBlank()) {
      try {
        ClientRegistration oidcRegistration =
            ClientRegistrations.fromIssuerLocation(oidcIssuerUri)
                .registrationId("oidc")
                .clientId(oidcClientId)
                .clientSecret(oidcClientSecret)
                .scope("openid", "email", "profile")
                .build();
        registrations.add(oidcRegistration);
      } catch (Exception e) {
        log.warn(
            "OIDC_ISSUER_URI is set but discovery failed ({}); SSO login via a generic OIDC provider is disabled until this is fixed.",
            e.getMessage());
      }
    }

    return new InMemoryClientRegistrationRepository(registrations);
  }
}

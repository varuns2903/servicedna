package com.servicedna.auth.service;

import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.InviteStatus;
import com.servicedna.organization.repository.OrganizationInviteRepository;
import com.servicedna.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Who may create an account. {@code open} (the default) lets anyone sign up. {@code invite-only}
 * — for a ServiceDNA reachable from the internet — allows an email with a pending invitation, or
 * one in {@code AUTH_SIGNUP_DOMAINS}; and the very first account, so an install can be set up.
 * Applies to password sign-up and to first sign-in with GitHub or SSO alike.
 */
@Component
public class SignupPolicy {

  private final boolean open;
  private final Set<String> domains;
  private final UserRepository users;
  private final OrganizationInviteRepository invites;

  public SignupPolicy(
      @Value("${AUTH_SIGNUP:open}") String mode,
      @Value("${AUTH_SIGNUP_DOMAINS:}") String domains,
      UserRepository users,
      OrganizationInviteRepository invites) {
    String normalized = mode.trim().toLowerCase(Locale.ROOT);
    if (!normalized.equals("open") && !normalized.equals("invite-only")) {
      throw new IllegalStateException("AUTH_SIGNUP must be open or invite-only, not " + mode);
    }
    this.open = normalized.equals("open");
    this.domains = Arrays.stream(domains.split(","))
        .map(d -> d.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", ""))
        .filter(d -> !d.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
    this.users = users;
    this.invites = invites;
  }

  public boolean isOpen() {
    return open;
  }

  public String mode() {
    return open ? "open" : "invite-only";
  }

  /** Throws unless this email may create a new account. */
  public void check(String email) {
    if (!allowed(email)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "SIGNUP_CLOSED",
          "Sign-up on this ServiceDNA is by invitation. Ask an organization admin to invite " + email + ".");
    }
  }

  public boolean allowed(String email) {
    if (open || users.count() == 0) {
      return true;
    }
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    int at = normalized.lastIndexOf('@');
    if (at > 0 && domains.contains(normalized.substring(at + 1))) {
      return true;
    }
    return invites.existsByEmailIgnoreCaseAndStatusAndExpiresAtAfter(normalized, InviteStatus.PENDING, OffsetDateTime.now());
  }
}

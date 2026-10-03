package com.servicedna.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.servicedna.common.exception.ApiException;
import com.servicedna.organization.domain.InviteStatus;
import com.servicedna.organization.repository.OrganizationInviteRepository;
import com.servicedna.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

class SignupPolicyTest {

  private final UserRepository users = mock(UserRepository.class);
  private final OrganizationInviteRepository invites = mock(OrganizationInviteRepository.class);

  private SignupPolicy policy(String mode, String domains) {
    return new SignupPolicy(mode, domains, users, invites);
  }

  @Test
  void openLetsAnyoneSignUp() {
    when(users.count()).thenReturn(5L);
    assertThat(policy("open", "").allowed("anyone@example.com")).isTrue();
    assertThat(policy("open", "").mode()).isEqualTo("open");
  }

  @Test
  void inviteOnlyStillLetsTheFirstAccountSetUpTheInstall() {
    when(users.count()).thenReturn(0L);
    assertThat(policy("invite-only", "").allowed("admin@example.com")).isTrue();
  }

  @Test
  void inviteOnlyAllowsListedDomainsAndInvitees() {
    when(users.count()).thenReturn(3L);
    when(invites.existsByEmailIgnoreCaseAndStatusAndExpiresAtAfter(eq("asha@partner.io"), eq(InviteStatus.PENDING), any())).thenReturn(true);
    SignupPolicy policy = policy("Invite-Only", " acme.com , @acme.io");

    assertThat(policy.allowed("Dev@ACME.com")).isTrue();
    assertThat(policy.allowed("ops@acme.io")).isTrue();
    assertThat(policy.allowed("Asha@Partner.io")).isTrue(); // invited
    assertThat(policy.allowed("eve@acme.com.evil.io")).isFalse();
    assertThat(policy.allowed("eve@example.com")).isFalse();
    assertThatThrownBy(() -> policy.check("eve@example.com"))
        .isInstanceOf(ApiException.class).hasMessageContaining("by invitation");
  }

  @Test
  void anUnknownModeFailsAtStartup() {
    assertThatThrownBy(() -> policy("closed", "")).isInstanceOf(IllegalStateException.class);
  }
}

package com.servicedna.auth.dto;

/** What the sign-in page offers: SSO, and whether anyone may sign up ("open") or only invitees ("invite-only"). */
public record SsoConfigDto(boolean oidcEnabled, String signup) {}

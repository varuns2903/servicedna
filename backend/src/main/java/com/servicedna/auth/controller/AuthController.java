package com.servicedna.auth.controller;

import com.servicedna.auth.dto.AuthResponse;
import com.servicedna.auth.dto.ForgotPasswordRequest;
import com.servicedna.auth.dto.LoginRequest;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.auth.dto.ResetPasswordRequest;
import com.servicedna.auth.dto.SsoConfigDto;
import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.auth.service.AuthService;
import com.servicedna.user.domain.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final boolean oidcEnabled;

  public AuthController(
      AuthService authService, @Value("${OIDC_ISSUER_URI:}") String oidcIssuerUri) {
    this.authService = authService;
    this.oidcEnabled = !oidcIssuerUri.isBlank();
  }

  @GetMapping("/sso-config")
  public ResponseEntity<SsoConfigDto> getSsoConfig() {
    return ResponseEntity.ok(new SsoConfigDto(oidcEnabled));
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return new ResponseEntity<>(authService.register(request), HttpStatus.CREATED);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @GetMapping("/verify-email")
  public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
    authService.verifyEmail(token);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/resend-verification")
  public ResponseEntity<Void> resendVerificationEmail(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.resendVerificationEmail(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request.token(), request.newPassword());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/me")
  public ResponseEntity<User> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
    if (userDetails == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(userDetails.getUser());
  }
}

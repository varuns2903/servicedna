package com.servicedna.user.controller;

import com.servicedna.auth.dto.ChangePasswordRequest;
import com.servicedna.auth.dto.RequestEmailChangeRequest;
import com.servicedna.auth.dto.UserDto;
import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final AuthService authService;

  public UserController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping("/me")
  public ResponseEntity<UserDto> getCurrentUser(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        new UserDto(
            userDetails.getUser().getId(),
            userDetails.getUser().getEmail(),
            userDetails.getUser().getRole().name()));
  }

  @PostMapping("/me/change-password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(
        userDetails.getUser().getId(), request.currentPassword(), request.newPassword());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/me/change-email/request")
  public ResponseEntity<Void> requestEmailChange(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody RequestEmailChangeRequest request) {
    authService.requestEmailChange(
        userDetails.getUser().getId(), request.password(), request.newEmail());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/me/change-email/confirm")
  public ResponseEntity<Void> confirmEmailChange(@RequestParam String token) {
    authService.confirmEmailChange(token);
    return ResponseEntity.ok().build();
  }
}

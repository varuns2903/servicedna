package com.servicedna.user.controller;

import com.servicedna.auth.dto.ChangePasswordRequest;
import com.servicedna.auth.dto.NotificationPreferencesDto;
import com.servicedna.auth.dto.RequestEmailChangeRequest;
import com.servicedna.auth.dto.UserDto;
import com.servicedna.auth.security.CustomUserDetails;
import com.servicedna.auth.service.AuthService;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final AuthService authService;
  private final UserRepository userRepository;

  public UserController(AuthService authService, UserRepository userRepository) {
    this.authService = authService;
    this.userRepository = userRepository;
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

  @GetMapping("/me/notification-preferences")
  public ResponseEntity<NotificationPreferencesDto> getNotificationPreferences(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(
        new NotificationPreferencesDto(userDetails.getUser().isNotifyOnNewIncident()));
  }

  @PatchMapping("/me/notification-preferences")
  @Transactional
  public ResponseEntity<NotificationPreferencesDto> updateNotificationPreferences(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody NotificationPreferencesDto request) {
    User user = userDetails.getUser();
    user.setNotifyOnNewIncident(request.notifyOnNewIncident());
    userRepository.save(user);
    return ResponseEntity.ok(new NotificationPreferencesDto(user.isNotifyOnNewIncident()));
  }
}

package com.servicedna.auth.service;

import com.servicedna.auth.domain.EmailVerificationToken;
import com.servicedna.auth.domain.PasswordResetToken;
import com.servicedna.auth.dto.AuthResponse;
import com.servicedna.auth.dto.ForgotPasswordRequest;
import com.servicedna.auth.dto.LoginRequest;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.auth.dto.UserDto;
import com.servicedna.auth.repository.EmailVerificationTokenRepository;
import com.servicedna.auth.repository.PasswordResetTokenRepository;
import com.servicedna.auth.security.JwtService;
import com.servicedna.common.exception.ApiException;
import com.servicedna.common.mail.MailService;
import com.servicedna.user.domain.Role;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final MailService mailService;
  private final String frontendUrl;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuthenticationManager authenticationManager,
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      MailService mailService,
      @Value("${frontend.url}") String frontendUrl) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.authenticationManager = authenticationManager;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.mailService = mailService;
    this.frontendUrl = frontendUrl;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new ApiException(
          HttpStatus.CONFLICT, "USER_EXISTS", "User already exists with this email.");
    }

    User user =
        new User(
            UUID.randomUUID(),
            request.email(),
            passwordEncoder.encode(request.password()),
            Role
                .OWNER // Default role for now, Phase 3 will introduce Organizations and better Role
                       // assignment
            );
    user.setEmailVerified(false);

    user = userRepository.save(user);
    sendVerificationEmail(user);

    String jwtToken = jwtService.generateToken(user.getEmail(), user.getRole().name());

    return new AuthResponse(
        jwtToken, new UserDto(user.getId(), user.getEmail(), user.getRole().name()));
  }

  public AuthResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
    }

    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

    if (!user.isEmailVerified()) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "EMAIL_NOT_VERIFIED",
          "Please verify your email before signing in. Check your inbox for the verification link.");
    }

    String jwtToken = jwtService.generateToken(user.getEmail(), user.getRole().name());

    return new AuthResponse(
        jwtToken, new UserDto(user.getId(), user.getEmail(), user.getRole().name()));
  }

  @Transactional
  public void verifyEmail(String token) {
    EmailVerificationToken verificationToken =
        emailVerificationTokenRepository
            .findByToken(token)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INVALID_TOKEN", "Verification link is invalid."));

    if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "Verification link has expired.");
    }

    User user = verificationToken.getUser();
    user.setEmailVerified(true);
    userRepository.save(user);
    emailVerificationTokenRepository.delete(verificationToken);
  }

  @Transactional
  public void resendVerificationEmail(ForgotPasswordRequest request) {
    // Same non-enumeration stance as forgotPassword, plus a silent no-op for already-verified
    // accounts so this can't be used to spam an inbox.
    userRepository
        .findByEmail(request.email())
        .filter(user -> !user.isEmailVerified())
        .ifPresent(
            user -> {
              emailVerificationTokenRepository.findByUserId(user.getId())
                  .ifPresent(emailVerificationTokenRepository::delete);
              sendVerificationEmail(user);
            });
  }

  @Transactional
  public void forgotPassword(ForgotPasswordRequest request) {
    // Intentionally silent on unknown emails: responding differently would let a caller enumerate
    // registered accounts.
    userRepository
        .findByEmail(request.email())
        .ifPresent(
            user -> {
              PasswordResetToken resetToken =
                  new PasswordResetToken(
                      UUID.randomUUID(), user, generateToken(), Instant.now().plus(Duration.ofHours(1)));
              passwordResetTokenRepository.save(resetToken);

              String resetLink = frontendUrl + "/reset-password?token=" + resetToken.getToken();
              mailService.send(
                  user.getEmail(),
                  "Reset your ServiceDNA password",
                  "We received a request to reset your password. This link expires in 1 hour:\n\n"
                      + resetLink
                      + "\n\nIf you didn't request this, you can ignore this email.");
            });
  }

  @Transactional
  public void resetPassword(String token, String newPassword) {
    PasswordResetToken resetToken =
        passwordResetTokenRepository
            .findByToken(token)
            .orElseThrow(
                () ->
                    new ApiException(HttpStatus.NOT_FOUND, "INVALID_TOKEN", "Reset link is invalid."));

    if (resetToken.isUsed()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "TOKEN_USED", "This reset link has already been used.");
    }

    if (resetToken.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "Reset link has expired.");
    }

    User user = resetToken.getUser();
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    resetToken.setUsed(true);
    passwordResetTokenRepository.save(resetToken);
  }

  private void sendVerificationEmail(User user) {
    EmailVerificationToken verificationToken =
        new EmailVerificationToken(
            UUID.randomUUID(), user, generateToken(), Instant.now().plus(Duration.ofDays(1)));
    emailVerificationTokenRepository.save(verificationToken);

    String verifyLink = frontendUrl + "/verify-email?token=" + verificationToken.getToken();
    mailService.send(
        user.getEmail(),
        "Verify your ServiceDNA email",
        "Welcome to ServiceDNA! Confirm your email address to finish setting up your account:\n\n"
            + verifyLink
            + "\n\nThis link expires in 24 hours.");
  }

  private String generateToken() {
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}

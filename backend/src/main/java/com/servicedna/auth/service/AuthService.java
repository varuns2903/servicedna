package com.servicedna.auth.service;

import com.servicedna.auth.domain.EmailChangeToken;
import com.servicedna.auth.domain.EmailVerificationToken;
import com.servicedna.auth.domain.PasswordResetToken;
import com.servicedna.auth.domain.RefreshToken;
import com.servicedna.auth.dto.AuthResponse;
import com.servicedna.auth.dto.ForgotPasswordRequest;
import com.servicedna.auth.dto.LoginRequest;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.auth.dto.UserDto;
import com.servicedna.auth.repository.EmailChangeTokenRepository;
import com.servicedna.auth.repository.EmailVerificationTokenRepository;
import com.servicedna.auth.repository.PasswordResetTokenRepository;
import com.servicedna.auth.repository.RefreshTokenRepository;
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
  private final EmailChangeTokenRepository emailChangeTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final MailService mailService;
  private final String frontendUrl;
  private final long refreshExpirationMs;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuthenticationManager authenticationManager,
      EmailVerificationTokenRepository emailVerificationTokenRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      EmailChangeTokenRepository emailChangeTokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      MailService mailService,
      @Value("${frontend.url}") String frontendUrl,
      @Value("${JWT_REFRESH_EXPIRATION_MS:2592000000}") long refreshExpirationMs) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.authenticationManager = authenticationManager;
    this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.emailChangeTokenRepository = emailChangeTokenRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.mailService = mailService;
    this.frontendUrl = frontendUrl;
    this.refreshExpirationMs = refreshExpirationMs;
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

    return issueTokens(user);
  }

  @Transactional
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

    return issueTokens(user);
  }

  @Transactional
  public AuthResponse refreshAccessToken(String refreshToken) {
    RefreshToken existing =
        refreshTokenRepository
            .findByToken(refreshToken)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid."));

    if (existing.getExpiresAt().isBefore(Instant.now())) {
      refreshTokenRepository.delete(existing);
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Refresh token has expired. Please sign in again.");
    }

    // Rotate on every use: issueTokens below invalidates this (and any other) existing refresh
    // token for the user before minting a new one, so a stolen-and-replayed refresh token is
    // invalidated the moment the legitimate client refreshes next.
    return issueTokens(existing.getUser());
  }

  @Transactional
  public void logout(String refreshToken) {
    refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
  }

  /** Issues a fresh access + refresh token pair for an already-authenticated user. */
  @Transactional
  public AuthResponse issueTokens(User user) {
    String jwtToken = jwtService.generateToken(user.getEmail(), user.getRole().name());
    String refreshToken = issueRefreshToken(user);
    return new AuthResponse(
        jwtToken, refreshToken, new UserDto(user.getId(), user.getEmail(), user.getRole().name()));
  }

  private String issueRefreshToken(User user) {
    // One active refresh token per user: a new login/refresh supersedes any previous session's
    // refresh token, matching this app's existing single-session assumptions elsewhere.
    refreshTokenRepository.deleteByUserId(user.getId());
    RefreshToken refreshToken =
        new RefreshToken(
            UUID.randomUUID(),
            user,
            generateToken(),
            Instant.now().plusMillis(refreshExpirationMs));
    refreshTokenRepository.save(refreshToken);
    return refreshToken.getToken();
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

  @Transactional
  public void changePassword(UUID userId, String currentPassword, String newPassword) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Current password is incorrect.");
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }

  @Transactional
  public void requestEmailChange(UUID userId, String password, String newEmail) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password is incorrect.");
    }

    if (newEmail.equalsIgnoreCase(user.getEmail())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "SAME_EMAIL", "This is already your current email.");
    }

    if (userRepository.existsByEmail(newEmail)) {
      throw new ApiException(
          HttpStatus.CONFLICT, "EMAIL_IN_USE", "This email is already in use.");
    }

    emailChangeTokenRepository.findByUserId(user.getId()).ifPresent(emailChangeTokenRepository::delete);

    EmailChangeToken changeToken =
        new EmailChangeToken(
            UUID.randomUUID(), user, newEmail, generateToken(), Instant.now().plus(Duration.ofHours(1)));
    emailChangeTokenRepository.save(changeToken);

    String confirmLink = frontendUrl + "/confirm-email-change?token=" + changeToken.getToken();
    mailService.send(
        newEmail,
        "Confirm your new ServiceDNA email",
        "Confirm this email address is yours to finish changing your account email:\n\n"
            + confirmLink
            + "\n\nThis link expires in 1 hour. If you didn't request this, you can ignore this email.");
  }

  @Transactional
  public void confirmEmailChange(String token) {
    EmailChangeToken changeToken =
        emailChangeTokenRepository
            .findByToken(token)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "INVALID_TOKEN", "Confirmation link is invalid."));

    if (changeToken.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "Confirmation link has expired.");
    }

    if (userRepository.existsByEmail(changeToken.getNewEmail())) {
      emailChangeTokenRepository.delete(changeToken);
      throw new ApiException(
          HttpStatus.CONFLICT, "EMAIL_IN_USE", "This email is already in use.");
    }

    User user = changeToken.getUser();
    user.setEmail(changeToken.getNewEmail());
    userRepository.save(user);
    emailChangeTokenRepository.delete(changeToken);
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

package com.servicedna.auth.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.servicedna.auth.repository.RefreshTokenRepository;
import com.servicedna.user.domain.Role;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

  @Mock private JwtService jwtService;
  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private Authentication authentication;
  @Mock private OidcUser oidcUser;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private OAuth2SuccessHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new OAuth2SuccessHandler(
            jwtService, userRepository, refreshTokenRepository, "http://localhost:5173", 2592000000L);
    when(authentication.getPrincipal()).thenReturn(oidcUser);
    // DefaultRedirectStrategy passes the target URL through response.encodeRedirectURL(...)
    // before calling sendRedirect(...); a plain mock returns null unless stubbed to echo it back.
    // lenient(): only the two tests that reach the redirect actually invoke this.
    org.mockito.Mockito.lenient()
        .when(response.encodeRedirectURL(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void rejectsSignInToExistingAccountWithUnverifiedProviderEmail() {
    User existing = new User(UUID.randomUUID(), "victim@example.com", "hash", Role.MEMBER);
    when(oidcUser.getEmail()).thenReturn("victim@example.com");
    when(oidcUser.getEmailVerified()).thenReturn(false);
    when(userRepository.findByEmail("victim@example.com")).thenReturn(Optional.of(existing));

    assertThrows(
        ServletException.class,
        () -> handler.onAuthenticationSuccess(request, response, authentication));

    verify(jwtService, org.mockito.Mockito.never()).generateToken(anyString(), anyString());
  }

  @Test
  void allowsSignInToExistingAccountWithVerifiedProviderEmail() throws Exception {
    User existing = new User(UUID.randomUUID(), "owner@example.com", "hash", Role.MEMBER);
    when(oidcUser.getEmail()).thenReturn("owner@example.com");
    when(oidcUser.getEmailVerified()).thenReturn(true);
    when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(existing));
    when(jwtService.generateToken(anyString(), anyString())).thenReturn("jwt-token");

    handler.onAuthenticationSuccess(request, response, authentication);

    verify(response).sendRedirect(org.mockito.ArgumentMatchers.contains("token=jwt-token"));
  }

  @Test
  void allowsNewAccountCreationEvenWithUnverifiedProviderEmail() throws Exception {
    when(oidcUser.getEmail()).thenReturn("newuser@example.com");
    when(oidcUser.getEmailVerified()).thenReturn(false);
    when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(jwtService.generateToken(anyString(), anyString())).thenReturn("jwt-token");

    handler.onAuthenticationSuccess(request, response, authentication);

    verify(userRepository).save(any(User.class));
    verify(response).sendRedirect(org.mockito.ArgumentMatchers.contains("token=jwt-token"));
  }
}

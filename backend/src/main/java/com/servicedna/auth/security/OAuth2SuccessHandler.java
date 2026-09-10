package com.servicedna.auth.security;

import com.servicedna.user.domain.Role;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final String frontendUrl;

  public OAuth2SuccessHandler(
      JwtService jwtService,
      UserRepository userRepository,
      @Value("${frontend.url}") String frontendUrl) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
    this.frontendUrl = frontendUrl;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

    String email;
    boolean providerVerifiedEmail;

    if (oauth2User instanceof OidcUser oidcUser) {
      // A real OIDC provider (unlike GitHub, which isn't OIDC) authenticates as an OidcUser and
      // carries a standard email/email_verified claim pair from the ID token.
      email = oidcUser.getEmail();
      if (email == null) {
        throw new ServletException("OIDC provider did not return an email claim");
      }
      // Trust the claim when present; some providers omit email_verified entirely for
      // enterprise/managed accounts rather than omitting the email itself.
      providerVerifiedEmail = !Boolean.FALSE.equals(oidcUser.getEmailVerified());
    } else {
      // GitHub might provide email directly or login
      email = oauth2User.getAttribute("email");
      if (email == null) {
        String login = oauth2User.getAttribute("login");
        if (login != null) {
          email = login + "@github.com";
        } else {
          throw new ServletException("Could not resolve email from OAuth2 provider");
        }
      }
      providerVerifiedEmail = true;
    }

    // Check if user exists
    final String finalEmail = email;
    final boolean finalVerifiedEmail = providerVerifiedEmail;
    User user =
        userRepository
            .findByEmail(email)
            .orElseGet(
                () -> {
                  User newUser =
                      new User(
                          UUID.randomUUID(),
                          finalEmail,
                          null, // No password for OAuth users
                          Role.OWNER // Default to OWNER for demo purposes
                          );
                  newUser.setEmailVerified(finalVerifiedEmail);
                  return userRepository.save(newUser);
                });

    String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

    // Redirect to frontend with token
    String targetUrl =
        UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/redirect")
            .queryParam("token", token)
            .build()
            .toUriString();

    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}

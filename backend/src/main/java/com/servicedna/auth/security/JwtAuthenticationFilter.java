package com.servicedna.auth.security;

import com.servicedna.auth.token.ApiTokenService;
import com.servicedna.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** Request attribute set when the caller authenticated with a personal API token. */
  public static final String API_TOKEN_ATTRIBUTE = "servicedna.apiToken";

  private final JwtService jwtService;
  private final CustomUserDetailsService userDetailsService;
  private final ApiTokenService apiTokens;
  private final UserRepository users;

  public JwtAuthenticationFilter(
      JwtService jwtService, CustomUserDetailsService userDetailsService, ApiTokenService apiTokens, UserRepository users) {
    this.jwtService = jwtService;
    this.userDetailsService = userDetailsService;
    this.apiTokens = apiTokens;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    final String authHeader = request.getHeader("Authorization");
    final String jwt;
    final String userEmail;

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    jwt = authHeader.substring(7);
    if (jwt.startsWith(ApiTokenService.PREFIX)) {
      apiTokens.authenticate(jwt).flatMap(users::findById).ifPresent(user -> {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
        request.setAttribute(API_TOKEN_ATTRIBUTE, true);
      });
      filterChain.doFilter(request, response);
      return;
    }
    try {
      userEmail = jwtService.extractUsername(jwt);
      if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

        if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {
          UsernamePasswordAuthenticationToken authToken =
              new UsernamePasswordAuthenticationToken(
                  userDetails, null, userDetails.getAuthorities());
          authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authToken);
        }
      }
    } catch (Exception ex) {
      // Log error here or let it fail authentication silently
    }

    filterChain.doFilter(request, response);
  }
}

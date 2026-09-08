package com.servicedna.auth.security;

import com.servicedna.user.domain.Role;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public OAuth2SuccessHandler(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        
        // GitHub might provide email directly or login
        String email = oauth2User.getAttribute("email");
        if (email == null) {
            String login = oauth2User.getAttribute("login");
            if (login != null) {
                email = login + "@github.com";
            } else {
                throw new ServletException("Could not resolve email from OAuth2 provider");
            }
        }

        // Check if user exists
        final String finalEmail = email;
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User(
                    UUID.randomUUID(),
                    finalEmail,
                    null, // No password for OAuth users
                    Role.OWNER // Default to OWNER for demo purposes
            );
            return userRepository.save(newUser);
        });

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

        // Redirect to frontend with token
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect")
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

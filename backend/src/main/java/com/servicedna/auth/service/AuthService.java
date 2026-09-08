package com.servicedna.auth.service;

import com.servicedna.auth.dto.AuthResponse;
import com.servicedna.auth.dto.LoginRequest;
import com.servicedna.auth.dto.RegisterRequest;
import com.servicedna.auth.dto.UserDto;
import com.servicedna.auth.security.JwtService;
import com.servicedna.common.exception.ApiException;
import com.servicedna.user.domain.Role;
import com.servicedna.user.domain.User;
import com.servicedna.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuthenticationManager authenticationManager) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.authenticationManager = authenticationManager;
  }

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

    userRepository.save(user);

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

    String jwtToken = jwtService.generateToken(user.getEmail(), user.getRole().name());

    return new AuthResponse(
        jwtToken, new UserDto(user.getId(), user.getEmail(), user.getRole().name()));
  }
}

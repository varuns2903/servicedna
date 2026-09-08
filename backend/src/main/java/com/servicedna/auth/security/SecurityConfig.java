package com.servicedna.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthFilter;
  private final CustomUserDetailsService userDetailsService;
  private final OAuth2SuccessHandler oAuth2SuccessHandler;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthFilter,
      CustomUserDetailsService userDetailsService,
      OAuth2SuccessHandler oAuth2SuccessHandler) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.userDetailsService = userDetailsService;
    this.oAuth2SuccessHandler = oAuth2SuccessHandler;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/ping",
                        "/api/v1/webhooks/stripe",
                        "/ws/**",
                        "/api/v1/public/**")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2SuccessHandler))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                        org.springframework.http.HttpStatus.FORBIDDEN)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authenticationProvider(authenticationProvider())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setUserDetailsService(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}

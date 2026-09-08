package com.servicedna.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtProperties jwtProperties;
  private final SecretKey key;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  public String generateToken(String username, String role) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", role);
    return createToken(claims, username);
  }

  private String createToken(Map<String, Object> claims, String subject) {
    return Jwts.builder()
        .claims(claims)
        .subject(subject)
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMs()))
        .signWith(key)
        .compact();
  }

  public boolean isTokenValid(String token, String username) {
    final String extractedUsername = extractUsername(token);
    return (extractedUsername.equals(username) && !isTokenExpired(token));
  }

  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}

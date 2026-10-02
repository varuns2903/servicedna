package com.servicedna.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedna.common.exception.ApiException;
import io.jsonwebtoken.Jwts;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Talks to GitHub as the ServiceDNA GitHub App: a JWT signed with the App's private key, exchanged
 * for short-lived per-installation tokens (cached until shortly before they expire).
 */
@Component
public class GitHubAppClient {

  private final String apiUrl;
  private final String webUrl;
  private final String appId;
  private final String slug;
  private final String clientId;
  private final String clientSecret;
  private final String webhookSecret;
  private final PrivateKey privateKey;
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final Map<Long, Token> tokens = new ConcurrentHashMap<>();

  private record Token(String value, Instant expiresAt) {}

  public GitHubAppClient(
      @Value("${github.api-url:https://api.github.com}") String apiUrl,
      @Value("${github.web-url:https://github.com}") String webUrl,
      @Value("${github.app.id:}") String appId,
      @Value("${github.app.slug:}") String slug,
      @Value("${github.app.private-key:}") String privateKeyPem,
      @Value("${github.app.webhook-secret:}") String webhookSecret,
      @Value("${github.app.client-id:}") String clientId,
      @Value("${github.app.client-secret:}") String clientSecret,
      ObjectMapper json) {
    this.apiUrl = apiUrl.replaceAll("/+$", "");
    this.webUrl = webUrl.replaceAll("/+$", "");
    this.appId = appId;
    this.slug = slug;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.webhookSecret = webhookSecret;
    this.privateKey = privateKeyPem == null || privateKeyPem.isBlank() ? null : parsePrivateKey(privateKeyPem);
    this.json = json;
  }

  public boolean isConfigured() {
    return privateKey != null && !appId.isBlank() && !slug.isBlank() && !webhookSecret.isBlank() && !clientId.isBlank()
        && !clientSecret.isBlank();
  }

  public String slug() {
    return slug;
  }

  public String webhookSecret() {
    return webhookSecret;
  }

  public String installUrl(String state) {
    return webUrl + "/apps/" + slug + "/installations/new?state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
  }

  /** The token GitHub hands back after "Request user authorization during installation". */
  public String userToken(String code) {
    requireConfigured();
    String form = "client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret) + "&code=" + enc(code);
    JsonNode body = send(HttpRequest.newBuilder(URI.create(webUrl + "/login/oauth/access_token"))
        .header("Accept", "application/json")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form)));
    if (!body.hasNonNull("access_token")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "GITHUB_AUTHORIZATION_FAILED",
          "GitHub didn't confirm who installed the app: " + body.path("error_description").asText("no access token"));
    }
    return body.get("access_token").asText();
  }

  /** Installations of this app the user can access (their token proves they can administer it). */
  public List<Long> userInstallations(String userToken) {
    JsonNode body = send(request("/user/installations?per_page=100", "Bearer " + userToken).GET());
    List<Long> ids = new ArrayList<>();
    body.path("installations").forEach(i -> ids.add(i.path("id").asLong()));
    return ids;
  }

  public JsonNode installation(long installationId) {
    return send(request("/app/installations/" + installationId, "Bearer " + appJwt()).GET());
  }

  /** Repositories the installation was granted (all of them, page by page). */
  public List<JsonNode> repositories(long installationId) {
    List<JsonNode> repos = new ArrayList<>();
    for (int page = 1; page <= 10; page++) {
      JsonNode body = get(installationId, "/installation/repositories?per_page=100&page=" + page);
      body.path("repositories").forEach(repos::add);
      if (body.path("repositories").size() < 100) {
        break;
      }
    }
    return repos;
  }

  /** A file's text at a ref (branch or commit), or empty if it doesn't exist. */
  public Optional<String> file(long installationId, String repository, String path, String ref) {
    try {
      JsonNode content = get(installationId, "/repos/" + repository + "/contents/" + path + (ref == null ? "" : "?ref=" + enc(ref)));
      return Optional.of(new String(Base64.getMimeDecoder().decode(content.path("content").asText()), StandardCharsets.UTF_8));
    } catch (ApiException e) {
      if (e.getStatus() == HttpStatus.NOT_FOUND) {
        return Optional.empty();
      }
      throw e;
    }
  }

  /** Names of the files in a directory at a ref (none if it doesn't exist). */
  public List<String> directory(long installationId, String repository, String path, String ref) {
    List<String> names = new ArrayList<>();
    try {
      JsonNode entries = get(installationId, "/repos/" + repository + "/contents/" + path + "?ref=" + enc(ref));
      entries.forEach(e -> {
        if ("file".equals(e.path("type").asText())) {
          names.add(e.path("name").asText());
        }
      });
    } catch (ApiException e) {
      if (e.getStatus() != HttpStatus.NOT_FOUND) {
        throw e;
      }
    }
    return names;
  }

  public JsonNode get(long installationId, String path) {
    return send(request(path, "token " + installationToken(installationId)).GET());
  }

  public JsonNode post(long installationId, String path, Object body) {
    return send(request(path, "token " + installationToken(installationId)).POST(HttpRequest.BodyPublishers.ofString(write(body))));
  }

  public JsonNode patch(long installationId, String path, Object body) {
    return send(request(path, "token " + installationToken(installationId)).method("PATCH", HttpRequest.BodyPublishers.ofString(write(body))));
  }

  /** A token for the installation's repositories, reused until a minute before it expires. */
  public String installationToken(long installationId) {
    Token cached = tokens.get(installationId);
    if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
      return cached.value();
    }
    JsonNode body = send(request("/app/installations/" + installationId + "/access_tokens", "Bearer " + appJwt())
        .POST(HttpRequest.BodyPublishers.noBody()));
    Token token = new Token(body.path("token").asText(), Instant.parse(body.path("expires_at").asText()));
    tokens.put(installationId, token);
    return token.value();
  }

  String appJwt() {
    requireConfigured();
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(appId)
        .issuedAt(Date.from(now.minusSeconds(60))) // allow for clock drift, as GitHub recommends
        .expiration(Date.from(now.plusSeconds(540)))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  private void requireConfigured() {
    if (!isConfigured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_APP_NOT_CONFIGURED",
          "The GitHub App isn't set up on this ServiceDNA (GITHUB_APP_* settings).");
    }
  }

  private HttpRequest.Builder request(String path, String authorization) {
    return HttpRequest.newBuilder(URI.create(apiUrl + path))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Authorization", authorization)
        .header("Content-Type", "application/json");
  }

  private JsonNode send(HttpRequest.Builder request) {
    try {
      HttpResponse<String> res = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      int status = res.statusCode();
      if (status >= 200 && status < 300) {
        return res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
      }
      String message = "GitHub returned HTTP " + status;
      try {
        message += ": " + json.readTree(res.body()).path("message").asText("");
      } catch (IOException ignored) {
        // not JSON
      }
      throw status == 404
          ? new ApiException(HttpStatus.NOT_FOUND, "GITHUB_NOT_FOUND", message)
          : new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", message);
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", "GitHub unreachable.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_ERROR", "Interrupted.");
    }
  }

  private String write(Object body) {
    try {
      return json.writeValueAsString(body);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /** GitHub hands out PKCS#1 PEM ("RSA PRIVATE KEY"); Java reads PKCS#8, so wrap it. */
  static PrivateKey parsePrivateKey(String pem) {
    String text = pem.replace("\\n", "\n");
    boolean pkcs1 = text.contains("BEGIN RSA PRIVATE KEY");
    String base64 = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    try {
      byte[] der = Base64.getDecoder().decode(base64);
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? pkcs1ToPkcs8(der) : der));
    } catch (Exception e) {
      throw new IllegalStateException("GITHUB_APP_PRIVATE_KEY isn't a readable RSA private key", e);
    }
  }

  private static byte[] pkcs1ToPkcs8(byte[] pkcs1) throws IOException {
    byte[] algorithm = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
    byte[] version = {0x02, 0x01, 0x00};
    ByteArrayOutputStream octets = new ByteArrayOutputStream();
    octets.write(0x04);
    writeLength(octets, pkcs1.length);
    octets.write(pkcs1);
    ByteArrayOutputStream inner = new ByteArrayOutputStream();
    inner.write(version);
    inner.write(algorithm);
    inner.write(octets.toByteArray());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(0x30);
    writeLength(out, inner.size());
    out.write(inner.toByteArray());
    return out.toByteArray();
  }

  private static void writeLength(ByteArrayOutputStream out, int length) {
    if (length < 0x80) {
      out.write(length);
    } else if (length < 0x100) {
      out.write(0x81);
      out.write(length);
    } else if (length < 0x10000) {
      out.write(0x82);
      out.write(length >> 8);
      out.write(length & 0xff);
    } else {
      out.write(0x83);
      out.write(length >> 16);
      out.write((length >> 8) & 0xff);
      out.write(length & 0xff);
    }
  }
}

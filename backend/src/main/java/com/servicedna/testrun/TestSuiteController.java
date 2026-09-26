package com.servicedna.testrun;

import com.servicedna.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
public class TestSuiteController {

  private final TestSuiteService service;

  public TestSuiteController(TestSuiteService service) {
    this.service = service;
  }

  @GetMapping("/test-collections")
  public ResponseEntity<List<TestRunDto.Collection>> collections(@PathVariable UUID orgId, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.listCollections(orgId, user.getUser().getId()));
  }

  @PostMapping("/test-collections")
  public ResponseEntity<TestRunDto.Collection> create(
      @PathVariable UUID orgId, @Valid @RequestBody TestRunDto.SaveCollection request, @AuthenticationPrincipal CustomUserDetails user) {
    return new ResponseEntity<>(service.saveCollection(orgId, null, request, user.getUser().getId()), HttpStatus.CREATED);
  }

  @PutMapping("/test-collections/{collectionId}")
  public ResponseEntity<TestRunDto.Collection> update(
      @PathVariable UUID orgId, @PathVariable UUID collectionId, @Valid @RequestBody TestRunDto.SaveCollection request,
      @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.saveCollection(orgId, collectionId, request, user.getUser().getId()));
  }

  @DeleteMapping("/test-collections/{collectionId}")
  public ResponseEntity<Void> delete(@PathVariable UUID orgId, @PathVariable UUID collectionId, @AuthenticationPrincipal CustomUserDetails user) {
    service.deleteCollection(orgId, collectionId, user.getUser().getId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test-suites")
  public ResponseEntity<TestRunDto.Suite> start(
      @PathVariable UUID orgId, @Valid @RequestBody TestRunDto.StartSuite request, @AuthenticationPrincipal CustomUserDetails user) {
    return new ResponseEntity<>(service.start(orgId, request, user.getUser().getId()), HttpStatus.CREATED);
  }

  @GetMapping("/test-suites")
  public ResponseEntity<List<TestRunDto.Suite>> suites(
      @PathVariable UUID orgId, @RequestParam(defaultValue = "20") int limit, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.list(orgId, limit, user.getUser().getId()));
  }

  @GetMapping("/test-suites/{suiteId}")
  public ResponseEntity<TestRunDto.Suite> suite(@PathVariable UUID orgId, @PathVariable UUID suiteId, @AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok(service.get(orgId, suiteId, user.getUser().getId()));
  }
}

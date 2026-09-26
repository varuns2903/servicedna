package com.servicedna.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.servicedna.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(
      ApiException ex, HttpServletRequest request) {
    ErrorResponse errorResponse =
        new ErrorResponse(
            OffsetDateTime.now(),
            ex.getStatus().value(),
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI(),
            null // requestId can be injected from MDC or filter later
            );
    return new ResponseEntity<>(errorResponse, ex.getStatus());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

    ErrorResponse errorResponse =
        new ErrorResponse(
            OffsetDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_ERROR",
            message,
            request.getRequestURI(),
            null);
    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
  }

  /** Malformed JSON, or a value that doesn't fit its field (e.g. an unknown enum constant). */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    String message = "Request body is malformed or has fields of the wrong type.";
    if (ex.getCause() instanceof InvalidFormatException invalid && !invalid.getPath().isEmpty()) {
      String field =
          invalid.getPath().stream()
              .map(JsonMappingException.Reference::getFieldName)
              .filter(name -> name != null)
              .collect(Collectors.joining("."));
      message = "Invalid value '" + invalid.getValue() + "' for field '" + field + "'.";
    }
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", message, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_PARAMETER",
        "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.",
        request);
  }

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(
      Exception ex, HttpServletRequest request) {
    // Spring MVC's own client errors (missing header or parameter, unsupported method or media
    // type, unknown route) carry their intended status; without this they'd all surface as 500s.
    if (ex instanceof org.springframework.web.ErrorResponse springError) {
      HttpStatusCode status = springError.getStatusCode();
      String code =
          status instanceof HttpStatus known ? known.name() : "HTTP_" + status.value();
      String detail = springError.getBody().getDetail();
      return error(status, code, detail != null ? detail : ex.getMessage(), request);
    }

    log.error("Unexpected error occurred while processing request: {}", request.getRequestURI().replaceAll("[\r\n]", "_"), ex);
    ErrorResponse errorResponse =
        new ErrorResponse(
            OffsetDateTime.now(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred.", // Don't expose stack traces
            request.getRequestURI(),
            null);
    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ErrorResponse> error(
      HttpStatusCode status, String errorCode, String message, HttpServletRequest request) {
    ErrorResponse errorResponse =
        new ErrorResponse(
            OffsetDateTime.now(), status.value(), errorCode, message, request.getRequestURI(), null);
    return new ResponseEntity<>(errorResponse, status);
  }
}

package com.orbitekk.shagriha.common;

import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex) { return error(ex.status(), ex.getMessage(), Map.of()); }
    @ExceptionHandler({IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(RuntimeException ex) { return error(HttpStatus.BAD_REQUEST, ex.getMessage(), Map.of()); }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> unauthorized() { return error(HttpStatus.UNAUTHORIZED, "Invalid credentials", Map.of()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String,String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String message, Map<String,String> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fields));
    }
    record ApiError(Instant timestamp, int status, String error, String message, Map<String,String> fields) {}
}

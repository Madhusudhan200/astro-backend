package com.kpjyotish.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Error with an HTTP status, rendered as {"error": "..."} like the old API. */
public class ApiException extends RuntimeException {
    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    @RestControllerAdvice
    public static class Handler {
        @ExceptionHandler(ApiException.class)
        public ResponseEntity<Map<String, String>> api(ApiException e) {
            return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
        }

        @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
        public ResponseEntity<Map<String, String>> notFound(Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
        }
    }
}

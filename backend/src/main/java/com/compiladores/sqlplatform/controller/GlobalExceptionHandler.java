package com.compiladores.sqlplatform.controller;

import com.compiladores.sqlplatform.model.ValidationIssue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("valid", false);
        body.put("message", "Request invalido.");
        body.put("errors", errors.stream()
                .map(error -> ValidationIssue.error("SYSTEM", error, 1, 1, "request"))
                .toList());
        body.put("warnings", List.of());
        body.put("tokens", List.of());
        body.put("ast", null);
        body.put("semanticResult", null);
        body.put("output", null);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleServiceConfigurationError(IllegalStateException exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("valid", false);
        body.put("message", "No se pudo completar la validacion.");
        body.put("errors", List.of(ValidationIssue.error("SYSTEM", exception.getMessage(), 1, 1, "backend")));
        body.put("warnings", List.of());
        body.put("tokens", List.of());
        body.put("ast", null);
        body.put("semanticResult", null);
        body.put("output", null);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedError(Exception exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("valid", false);
        body.put("message", "Error interno del backend.");
        body.put("errors", List.of(ValidationIssue.error("SYSTEM", "Ocurrio un error inesperado al validar la consulta.", 1, 1, "backend")));
        body.put("warnings", List.of());
        body.put("tokens", List.of());
        body.put("ast", null);
        body.put("semanticResult", null);
        body.put("output", null);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

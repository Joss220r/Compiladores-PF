package com.compiladores.sqlplatform.controller;

import com.compiladores.sqlplatform.dto.QueryValidationRequest;
import com.compiladores.sqlplatform.dto.QueryValidationResponse;
import com.compiladores.sqlplatform.service.QueryValidationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QueryValidationController {

    private final QueryValidationService queryValidationService;

    public QueryValidationController(QueryValidationService queryValidationService) {
        this.queryValidationService = queryValidationService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SQL Platform backend is running");
    }

    @PostMapping("/validate")
    public ResponseEntity<QueryValidationResponse> validateQuery(
            @Valid @RequestBody QueryValidationRequest request
    ) {
        return ResponseEntity.ok(queryValidationService.validate(request));
    }
}

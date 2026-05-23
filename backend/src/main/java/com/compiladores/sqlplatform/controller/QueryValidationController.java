package com.compiladores.sqlplatform.controller;

import com.compiladores.sqlplatform.dto.DeleteHistoryResponse;
import com.compiladores.sqlplatform.dto.HistoryListResponse;
import com.compiladores.sqlplatform.dto.HistoryStatsResponse;
import com.compiladores.sqlplatform.dto.QueryValidationRequest;
import com.compiladores.sqlplatform.dto.QueryValidationResponse;
import com.compiladores.sqlplatform.service.QueryHistoryService;
import com.compiladores.sqlplatform.service.QueryValidationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QueryValidationController {

    private final QueryValidationService queryValidationService;
    private final QueryHistoryService queryHistoryService;

    public QueryValidationController(QueryValidationService queryValidationService, QueryHistoryService queryHistoryService) {
        this.queryValidationService = queryValidationService;
        this.queryHistoryService = queryHistoryService;
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

    @GetMapping("/history")
    public ResponseEntity<HistoryListResponse> history(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String engine,
            @RequestParam(required = false) Boolean success
    ) {
        return ResponseEntity.ok(new HistoryListResponse(true, queryHistoryService.findHistory(limit, engine, success)));
    }

    @GetMapping("/history/stats")
    public ResponseEntity<HistoryStatsResponse> historyStats() {
        return ResponseEntity.ok(queryHistoryService.getStats());
    }

    @DeleteMapping("/history")
    public ResponseEntity<DeleteHistoryResponse> clearHistory() {
        queryHistoryService.clearHistory();
        return ResponseEntity.ok(new DeleteHistoryResponse(true, "Historial limpiado correctamente."));
    }
}

package com.compiladores.sqlplatform.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class HistoryItemResponse {

    private UUID id;
    private String engine;
    private String originalQuery;
    private boolean valid;
    private int errorCount;
    private int warningCount;
    private LocalDateTime createdAt;

    public HistoryItemResponse(UUID id, String engine, String originalQuery, boolean valid, int errorCount, int warningCount, LocalDateTime createdAt) {
        this.id = id;
        this.engine = engine;
        this.originalQuery = originalQuery;
        this.valid = valid;
        this.errorCount = errorCount;
        this.warningCount = warningCount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEngine() {
        return engine;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public boolean isValid() {
        return valid;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

package com.compiladores.sqlplatform.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class QueryHistory {

    private UUID id;
    private String engine;
    private String originalQuery;
    private boolean success;
    private int errorCount;
    private int warningCount;
    private String errorsJson;
    private String warningsJson;
    private String suggestionsJson;
    private LocalDateTime createdAt;

    public QueryHistory(
            UUID id,
            String engine,
            String originalQuery,
            boolean success,
            int errorCount,
            int warningCount,
            String errorsJson,
            String warningsJson,
            String suggestionsJson,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.engine = engine;
        this.originalQuery = originalQuery;
        this.success = success;
        this.errorCount = errorCount;
        this.warningCount = warningCount;
        this.errorsJson = errorsJson;
        this.warningsJson = warningsJson;
        this.suggestionsJson = suggestionsJson;
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

    public boolean isSuccess() {
        return success;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public String getErrorsJson() {
        return errorsJson;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public String getSuggestionsJson() {
        return suggestionsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

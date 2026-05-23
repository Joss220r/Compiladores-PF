package com.compiladores.sqlplatform.dto;

import java.util.List;

public class HistoryStatsResponse {

    private boolean success;
    private long total;
    private long valid;
    private long invalid;
    private long warningTotal;
    private String mostUsedEngine;
    private List<EngineStatsResponse> byEngine;
    private List<TopErrorResponse> topErrors;

    public HistoryStatsResponse(
            boolean success,
            long total,
            long valid,
            long invalid,
            long warningTotal,
            String mostUsedEngine,
            List<EngineStatsResponse> byEngine,
            List<TopErrorResponse> topErrors
    ) {
        this.success = success;
        this.total = total;
        this.valid = valid;
        this.invalid = invalid;
        this.warningTotal = warningTotal;
        this.mostUsedEngine = mostUsedEngine;
        this.byEngine = byEngine;
        this.topErrors = topErrors;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getTotal() {
        return total;
    }

    public long getValid() {
        return valid;
    }

    public long getInvalid() {
        return invalid;
    }

    public long getWarningTotal() {
        return warningTotal;
    }

    public String getMostUsedEngine() {
        return mostUsedEngine;
    }

    public List<EngineStatsResponse> getByEngine() {
        return byEngine;
    }

    public List<TopErrorResponse> getTopErrors() {
        return topErrors;
    }
}

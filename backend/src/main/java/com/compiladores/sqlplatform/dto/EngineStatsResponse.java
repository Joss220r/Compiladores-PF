package com.compiladores.sqlplatform.dto;

public class EngineStatsResponse {

    private String engine;
    private long total;
    private long valid;
    private long invalid;

    public EngineStatsResponse(String engine, long total, long valid, long invalid) {
        this.engine = engine;
        this.total = total;
        this.valid = valid;
        this.invalid = invalid;
    }

    public String getEngine() {
        return engine;
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
}

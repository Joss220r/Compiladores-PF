package com.compiladores.sqlplatform.dto;

public class TopErrorResponse {

    private String message;
    private long count;

    public TopErrorResponse(String message, long count) {
        this.message = message;
        this.count = count;
    }

    public String getMessage() {
        return message;
    }

    public long getCount() {
        return count;
    }
}

package com.compiladores.sqlplatform.dto;

public class DeleteHistoryResponse {

    private boolean success;
    private String message;

    public DeleteHistoryResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}

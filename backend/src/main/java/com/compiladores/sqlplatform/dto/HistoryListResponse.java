package com.compiladores.sqlplatform.dto;

import java.util.List;

public class HistoryListResponse {

    private boolean success;
    private List<HistoryItemResponse> items;

    public HistoryListResponse(boolean success, List<HistoryItemResponse> items) {
        this.success = success;
        this.items = items;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<HistoryItemResponse> getItems() {
        return items;
    }
}

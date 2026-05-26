package com.compiladores.sqlplatform.dto;

public class LoginResponse {

    private boolean success;
    private String message;
    private String username;
    private String displayName;
    private String role;

    public LoginResponse(boolean success, String message, String username, String displayName, String role) {
        this.success = success;
        this.message = message;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }
}

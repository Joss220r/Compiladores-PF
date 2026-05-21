package com.compiladores.sqlplatform.model;

public class ValidationIssue {

    private String phase;
    private String severity;
    private String message;
    private int line;
    private int column;
    private String fragment;

    public ValidationIssue(String phase, String severity, String message, int line, int column, String fragment) {
        this.phase = phase;
        this.severity = severity;
        this.message = message;
        this.line = line;
        this.column = column;
        this.fragment = fragment;
    }

    public static ValidationIssue error(String phase, String message, int line, int column, String fragment) {
        return new ValidationIssue(phase, "ERROR", message, line, column, fragment);
    }

    public static ValidationIssue warning(String phase, String message, int line, int column, String fragment) {
        return new ValidationIssue(phase, "WARNING", message, line, column, fragment);
    }

    public String getPhase() {
        return phase;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getFragment() {
        return fragment;
    }
}

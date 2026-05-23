package com.compiladores.sqlplatform.model;

public class CorrectionSuggestion {

    private String title;
    private String explanation;
    private String fixedQuery;
    private double confidence;
    private String sourcePhase;

    public CorrectionSuggestion(
            String title,
            String explanation,
            String fixedQuery,
            double confidence,
            String sourcePhase
    ) {
        this.title = title;
        this.explanation = explanation;
        this.fixedQuery = fixedQuery;
        this.confidence = confidence;
        this.sourcePhase = sourcePhase;
    }

    public String getTitle() {
        return title;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getFixedQuery() {
        return fixedQuery;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getSourcePhase() {
        return sourcePhase;
    }
}

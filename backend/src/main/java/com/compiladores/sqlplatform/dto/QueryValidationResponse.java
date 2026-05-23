package com.compiladores.sqlplatform.dto;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.CorrectionSuggestion;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.SemanticResult;
import com.compiladores.sqlplatform.model.TokenInfo;
import com.compiladores.sqlplatform.model.ValidationIssue;
import java.util.List;

public class QueryValidationResponse {

    private boolean success;
    private boolean valid;
    private DatabaseEngine engine;
    private String message;
    private List<ValidationIssue> errors;
    private List<ValidationIssue> warnings;
    private List<TokenInfo> tokens;
    private AstNode ast;
    private SemanticResult semanticResult;
    private Object output;
    private List<CorrectionSuggestion> suggestions;

    public QueryValidationResponse(
            boolean success,
            DatabaseEngine engine,
            String message,
            List<ValidationIssue> errors,
            List<ValidationIssue> warnings,
            List<TokenInfo> tokens,
            AstNode ast,
            SemanticResult semanticResult,
            Object output,
            List<CorrectionSuggestion> suggestions
    ) {
        this.success = success;
        this.valid = success;
        this.engine = engine;
        this.message = message;
        this.errors = errors;
        this.warnings = warnings;
        this.tokens = tokens;
        this.ast = ast;
        this.semanticResult = semanticResult;
        this.output = output;
        this.suggestions = suggestions;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isValid() {
        return valid;
    }

    public DatabaseEngine getEngine() {
        return engine;
    }

    public String getMessage() {
        return message;
    }

    public List<ValidationIssue> getErrors() {
        return errors;
    }

    public List<ValidationIssue> getWarnings() {
        return warnings;
    }

    public List<TokenInfo> getTokens() {
        return tokens;
    }

    public AstNode getAst() {
        return ast;
    }

    public SemanticResult getSemanticResult() {
        return semanticResult;
    }

    public Object getOutput() {
        return output;
    }

    public List<CorrectionSuggestion> getSuggestions() {
        return suggestions;
    }
}

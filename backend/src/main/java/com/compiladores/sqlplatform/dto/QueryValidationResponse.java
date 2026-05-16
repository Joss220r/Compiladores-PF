package com.compiladores.sqlplatform.dto;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.SemanticResult;
import com.compiladores.sqlplatform.model.TokenInfo;
import java.util.List;

public class QueryValidationResponse {

    private boolean valid;
    private DatabaseEngine engine;
    private String message;
    private List<String> errors;
    private List<TokenInfo> tokens;
    private AstNode ast;
    private SemanticResult semanticResult;

    public QueryValidationResponse(
            boolean valid,
            DatabaseEngine engine,
            String message,
            List<String> errors,
            List<TokenInfo> tokens,
            AstNode ast,
            SemanticResult semanticResult
    ) {
        this.valid = valid;
        this.engine = engine;
        this.message = message;
        this.errors = errors;
        this.tokens = tokens;
        this.ast = ast;
        this.semanticResult = semanticResult;
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

    public List<String> getErrors() {
        return errors;
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
}

package com.compiladores.sqlplatform.model;

import java.util.List;
import java.util.Map;

public class SemanticResult {

    private boolean valid;
    private List<String> warnings;
    private Map<String, Object> symbols;

    public SemanticResult(boolean valid, List<String> warnings, Map<String, Object> symbols) {
        this.valid = valid;
        this.warnings = warnings;
        this.symbols = symbols;
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Map<String, Object> getSymbols() {
        return symbols;
    }
}

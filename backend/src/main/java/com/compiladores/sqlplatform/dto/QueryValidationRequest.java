package com.compiladores.sqlplatform.dto;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class QueryValidationRequest {

    @NotBlank(message = "La query no puede estar vacia.")
    private String query;

    @NotNull(message = "El motor de base de datos es obligatorio.")
    private DatabaseEngine engine;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public DatabaseEngine getEngine() {
        return engine;
    }

    public void setEngine(DatabaseEngine engine) {
        this.engine = engine;
    }
}

package com.compiladores.sqlplatform.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.compiladores.sqlplatform.dto.QueryValidationRequest;
import com.compiladores.sqlplatform.dto.QueryValidationResponse;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.service.compiler.BasicLexerAdapter;
import com.compiladores.sqlplatform.service.compiler.BasicSqlParserAdapter;
import com.compiladores.sqlplatform.service.semantic.InMemoryCatalogService;
import com.compiladores.sqlplatform.service.semantic.SemanticAnalyzerAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QueryValidationHistoryFailureTest {

    @Test
    void validateQueryStillRespondsWhenHistoryFails() {
        QueryHistoryService failingHistory = new QueryHistoryService(
                new ObjectMapper(),
                "",
                "5432",
                "",
                "",
                "",
                "require"
        ) {
            @Override
            public void saveAnalysis(String engine, String originalQuery, QueryValidationResponse response) {
                throw new RuntimeException("DB down");
            }
        };

        QueryValidationService service = new QueryValidationService(
                new BasicLexerAdapter(),
                new BasicSqlParserAdapter(),
                new SemanticAnalyzerAdapter(new InMemoryCatalogService()),
                new DialectValidationService(),
                new CorrectionSuggestionService(),
                failingHistory
        );
        QueryValidationRequest request = new QueryValidationRequest();
        request.setEngine(DatabaseEngine.MYSQL);
        request.setQuery("SELECT * FROM usuarios;");

        QueryValidationResponse response = service.validate(request);

        assertTrue(response.isSuccess());
    }
}

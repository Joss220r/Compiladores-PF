package com.compiladores.sqlplatform.service;

import com.compiladores.sqlplatform.dto.QueryValidationRequest;
import com.compiladores.sqlplatform.dto.QueryValidationResponse;
import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.SemanticResult;
import com.compiladores.sqlplatform.model.TokenInfo;
import com.compiladores.sqlplatform.service.compiler.LexerPort;
import com.compiladores.sqlplatform.service.compiler.ParserPort;
import com.compiladores.sqlplatform.service.compiler.SemanticAnalyzerPort;
import com.compiladores.sqlplatform.service.semantic.SemanticSymbols;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QueryValidationService {

    private final LexerPort lexer;
    private final ParserPort parser;
    private final SemanticAnalyzerPort semanticAnalyzer;

    public QueryValidationService(
            LexerPort lexer,
            ParserPort parser,
            SemanticAnalyzerPort semanticAnalyzer
    ) {
        this.lexer = lexer;
        this.parser = parser;
        this.semanticAnalyzer = semanticAnalyzer;
    }

    public QueryValidationResponse validate(QueryValidationRequest request) {
        List<String> errors = new ArrayList<>();
        String normalizedQuery = request.getQuery().trim();

        List<TokenInfo> tokens = lexer.tokenize(normalizedQuery, request.getEngine());
        AstNode ast = parser.parse(tokens, normalizedQuery, request.getEngine());
        SemanticResult semanticResult = semanticAnalyzer.analyze(ast, request.getEngine());
        errors.addAll(extractSemanticErrors(semanticResult));

        if (normalizedQuery.isBlank()) {
            errors.add("La query no puede estar vacia.");
        }

        boolean valid = errors.isEmpty() && semanticResult.isValid();
        String message = valid
                ? "Query validada por Lexer, Parser y Analisis Semantico."
                : "La query contiene errores.";

        return new QueryValidationResponse(
                valid,
                request.getEngine(),
                message,
                errors,
                tokens,
                ast,
                semanticResult
        );
    }

    private List<String> extractSemanticErrors(SemanticResult semanticResult) {
        if (semanticResult.getSymbols() == null) {
            return List.of();
        }

        Object semanticErrors = semanticResult.getSymbols().get(SemanticSymbols.ERRORS);
        if (semanticErrors instanceof List<?> errors) {
            return errors.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        return List.of();
    }
}

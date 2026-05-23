package com.compiladores.sqlplatform.service;

import com.compiladores.sqlplatform.dto.QueryValidationRequest;
import com.compiladores.sqlplatform.dto.QueryValidationResponse;
import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.CorrectionSuggestion;
import com.compiladores.sqlplatform.model.SemanticResult;
import com.compiladores.sqlplatform.model.TokenInfo;
import com.compiladores.sqlplatform.model.ValidationIssue;
import com.compiladores.sqlplatform.service.compiler.LexerPort;
import com.compiladores.sqlplatform.service.compiler.ParserPort;
import com.compiladores.sqlplatform.service.compiler.SemanticAnalyzerPort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QueryValidationService {

    private final LexerPort lexer;
    private final ParserPort parser;
    private final SemanticAnalyzerPort semanticAnalyzer;
    private final DialectValidationService dialectValidationService;
    private final CorrectionSuggestionService correctionSuggestionService;

    public QueryValidationService(
            LexerPort lexer,
            ParserPort parser,
            SemanticAnalyzerPort semanticAnalyzer,
            DialectValidationService dialectValidationService,
            CorrectionSuggestionService correctionSuggestionService
    ) {
        this.lexer = lexer;
        this.parser = parser;
        this.semanticAnalyzer = semanticAnalyzer;
        this.dialectValidationService = dialectValidationService;
        this.correctionSuggestionService = correctionSuggestionService;
    }

    public QueryValidationResponse validate(QueryValidationRequest request) {
        List<ValidationIssue> issues = new ArrayList<>();
        String normalizedQuery = request.getQuery().trim();

        List<TokenInfo> tokens = lexer.tokenize(normalizedQuery, request.getEngine());
        issues.addAll(lexer.getIssues());
        issues.addAll(dialectValidationService.validate(normalizedQuery, request.getEngine()));

        boolean hasBlockingDialectError = issues.stream()
                .anyMatch(issue -> "DIALECT".equals(issue.getPhase()) && "ERROR".equals(issue.getSeverity()));
        if (hasBlockingDialectError) {
            return buildResponse(request, issues, tokens, null, null);
        }

        AstNode ast = parser.parse(tokens, normalizedQuery, request.getEngine());
        issues.addAll(parser.getIssues());
        SemanticResult semanticResult = null;

        semanticResult = semanticAnalyzer.analyze(ast, request.getEngine());
        issues.addAll(semanticAnalyzer.getIssues());

        if (normalizedQuery.isBlank()) {
            issues.add(ValidationIssue.error("PARSER", "La query no puede estar vacia.", 1, 1, ""));
        }

        return buildResponse(request, issues, tokens, ast, semanticResult);
    }

    private QueryValidationResponse buildResponse(
            QueryValidationRequest request,
            List<ValidationIssue> issues,
            List<TokenInfo> tokens,
            AstNode ast,
            SemanticResult semanticResult
    ) {
        List<ValidationIssue> errors = issues.stream()
                .filter(issue -> "ERROR".equals(issue.getSeverity()))
                .toList();
        List<ValidationIssue> warnings = issues.stream()
                .filter(issue -> "WARNING".equals(issue.getSeverity()))
                .toList();
        List<CorrectionSuggestion> suggestions = correctionSuggestionService.generate(
                request.getQuery(),
                request.getEngine(),
                issues
        );
        boolean valid = errors.isEmpty();
        String message = valid
                ? "Query validada por Lexer, Parser y Analisis Semantico."
                : "La query contiene errores.";

        return new QueryValidationResponse(
                valid,
                request.getEngine(),
                message,
                errors,
                warnings,
                tokens,
                ast,
                semanticResult,
                null,
                suggestions
        );
    }
}

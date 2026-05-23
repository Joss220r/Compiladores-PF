package com.compiladores.sqlplatform.service;

import com.compiladores.sqlplatform.model.CorrectionSuggestion;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CorrectionSuggestionService {

    private static final Pattern SELECT_LIMIT_PATTERN = Pattern.compile(
            "(?is)^\\s*SELECT\\s+(?<columns>.+?)\\s+FROM\\s+(?<from>.+?)\\s+LIMIT\\s+(?<limit>\\d+)\\s*;?\\s*$"
    );
    private static final Pattern SELECT_TOP_PATTERN = Pattern.compile(
            "(?is)^\\s*SELECT\\s+TOP\\s+(?<limit>\\d+)\\s+(?<columns>.+?)\\s+FROM\\s+(?<from>.+?)\\s*;?\\s*$"
    );
    private static final Pattern REDIS_SET_KEY_ONLY_PATTERN = Pattern.compile("(?is)^\\s*SET\\s+(?<key>\\S+)\\s*$");
    private static final Pattern WHERE_EMPTY_PATTERN = Pattern.compile("(?is)\\bWHERE\\s*;?\\s*$");

    public List<CorrectionSuggestion> generate(String query, DatabaseEngine engine, List<ValidationIssue> issues) {
        if (issues == null || issues.stream().noneMatch(issue -> "ERROR".equals(issue.getSeverity()))) {
            return List.of();
        }

        String original = query == null ? "" : query.trim();
        List<CorrectionSuggestion> suggestions = new ArrayList<>();

        addSqlDialectSuggestions(original, engine, issues, suggestions);
        addMongoSuggestions(original, engine, issues, suggestions);
        addRedisSuggestions(original, engine, issues, suggestions);
        addCommonSyntaxSuggestions(original, issues, suggestions);

        return suggestions;
    }

    private void addSqlDialectSuggestions(
            String query,
            DatabaseEngine engine,
            List<ValidationIssue> issues,
            List<CorrectionSuggestion> suggestions
    ) {
        if (engine == DatabaseEngine.SQL_SERVER && containsMessage(issues, "LIMIT no es valido")) {
            Matcher matcher = SELECT_LIMIT_PATTERN.matcher(query);
            if (matcher.matches()) {
                String fixed = "SELECT TOP " + matcher.group("limit") + " " + matcher.group("columns").trim()
                        + " FROM " + matcher.group("from").trim() + ";";
                suggestions.add(suggestion(
                        "Cambiar LIMIT por TOP",
                        "SQL Server no usa LIMIT en SELECT. Usa TOP despues de SELECT.",
                        fixed,
                        0.95,
                        "DIALECT"
                ));
            }
        }

        if ((engine == DatabaseEngine.MYSQL || engine == DatabaseEngine.POSTGRESQL)
                && containsMessage(issues, "TOP no es valido")) {
            Matcher matcher = SELECT_TOP_PATTERN.matcher(query);
            if (matcher.matches()) {
                String fixed = "SELECT " + matcher.group("columns").trim()
                        + " FROM " + matcher.group("from").trim() + " LIMIT " + matcher.group("limit") + ";";
                suggestions.add(suggestion(
                        "Cambiar TOP por LIMIT",
                        engine.name() + " usa LIMIT para limitar resultados.",
                        fixed,
                        0.95,
                        "DIALECT"
                ));
            }
        }

        if (engine == DatabaseEngine.POSTGRESQL && containsMessage(issues, "AUTO_INCREMENT")) {
            suggestions.add(suggestion(
                    "Usar SERIAL en PostgreSQL",
                    "PostgreSQL no usa AUTO_INCREMENT. Para esta validacion puedes usar SERIAL.",
                    query.replaceAll("(?i)\\bINT\\s+AUTO_INCREMENT\\b", "SERIAL")
                            .replaceAll("(?i)\\bINTEGER\\s+AUTO_INCREMENT\\b", "SERIAL"),
                    0.86,
                    "DIALECT"
            ));
        }

        if (engine == DatabaseEngine.MYSQL && containsMessage(issues, "SERIAL")) {
            suggestions.add(suggestion(
                    "Usar AUTO_INCREMENT en MySQL",
                    "MySQL usa INT AUTO_INCREMENT para campos enteros autoincrementales.",
                    query.replaceAll("(?i)\\bSERIAL\\b", "INT AUTO_INCREMENT"),
                    0.88,
                    "DIALECT"
            ));
        }
    }

    private void addMongoSuggestions(
            String query,
            DatabaseEngine engine,
            List<ValidationIssue> issues,
            List<CorrectionSuggestion> suggestions
    ) {
        if (engine != DatabaseEngine.MONGODB || !containsMessage(issues, "$set")) {
            return;
        }

        suggestions.add(suggestion(
                "Usar operador $set",
                "MongoDB requiere operadores de actualizacion con $. Cambia set por $set.",
                query.replaceAll("(?i)([\\{,]\\s*)set\\s*:", "$1\\$set:"),
                0.92,
                "SEMANTIC"
        ));
    }

    private void addRedisSuggestions(
            String query,
            DatabaseEngine engine,
            List<ValidationIssue> issues,
            List<CorrectionSuggestion> suggestions
    ) {
        if (engine != DatabaseEngine.REDIS || !containsMessage(issues, "SET requiere")) {
            return;
        }

        Matcher matcher = REDIS_SET_KEY_ONLY_PATTERN.matcher(query);
        if (matcher.matches()) {
            suggestions.add(suggestion(
                    "Agregar valor al SET",
                    "SET necesita una key y un value. Agrega el valor que quieres guardar.",
                    "SET " + matcher.group("key") + " \"valor\"",
                    0.9,
                    "PARSER"
            ));
        }
    }

    private void addCommonSyntaxSuggestions(String query, List<ValidationIssue> issues, List<CorrectionSuggestion> suggestions) {
        if (containsMessage(issues, "WHERE debe incluir una condicion") || containsMessage(issues, "WHERE vacio")) {
            Matcher matcher = WHERE_EMPTY_PATTERN.matcher(query);
            if (matcher.find()) {
                suggestions.add(suggestion(
                        "Completar condicion WHERE",
                        "WHERE necesita una condicion para filtrar registros.",
                        matcher.replaceFirst("WHERE id = 1;"),
                        0.82,
                        sourcePhase(issues, "WHERE").orElse("PARSER")
                ));
            }
        }

        for (ValidationIssue issue : issues) {
            if (!"ERROR".equals(issue.getSeverity())) {
                continue;
            }

            String message = issue.getMessage() == null ? "" : issue.getMessage();
            if (message.contains("Comilla ' sin cerrar")) {
                suggestions.add(closeWith(query, "'", "Cerrar comilla simple", issue.getPhase()));
            } else if (message.contains("Comilla \" sin cerrar")) {
                suggestions.add(closeWith(query, "\"", "Cerrar comilla doble", issue.getPhase()));
            } else if (message.contains("Falta cerrar '('")) {
                suggestions.add(closeWith(query, ")", "Cerrar parentesis", issue.getPhase()));
            } else if (message.contains("Falta cerrar '{'")) {
                suggestions.add(closeWith(query, "}", "Cerrar llave", issue.getPhase()));
            } else if (message.contains("Falta cerrar '['") || message.contains("Corchete '[' sin cerrar")) {
                suggestions.add(closeWith(query, "]", "Cerrar corchete", issue.getPhase()));
            }
        }
    }

    private CorrectionSuggestion closeWith(String query, String closing, String title, String sourcePhase) {
        String suffix = query.endsWith(";") ? closing : closing + (looksSql(query) ? ";" : "");
        return suggestion(
                title,
                "La consulta tiene un simbolo de apertura sin cierre. Agrega " + closing + " al final.",
                query + suffix,
                0.72,
                sourcePhase
        );
    }

    private boolean containsMessage(List<ValidationIssue> issues, String fragment) {
        String normalizedFragment = fragment.toLowerCase(Locale.ROOT);
        return issues.stream()
                .map(ValidationIssue::getMessage)
                .filter(message -> message != null)
                .map(message -> message.toLowerCase(Locale.ROOT))
                .anyMatch(message -> message.contains(normalizedFragment));
    }

    private Optional<String> sourcePhase(List<ValidationIssue> issues, String fragment) {
        String normalizedFragment = fragment.toLowerCase(Locale.ROOT);
        return issues.stream()
                .filter(issue -> issue.getMessage() != null)
                .filter(issue -> issue.getMessage().toLowerCase(Locale.ROOT).contains(normalizedFragment))
                .map(ValidationIssue::getPhase)
                .findFirst();
    }

    private boolean looksSql(String query) {
        return query.toUpperCase(Locale.ROOT).matches("^(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP)\\b.*");
    }

    private CorrectionSuggestion suggestion(
            String title,
            String explanation,
            String fixedQuery,
            double confidence,
            String sourcePhase
    ) {
        return new CorrectionSuggestion(title, explanation, fixedQuery, confidence, sourcePhase);
    }
}

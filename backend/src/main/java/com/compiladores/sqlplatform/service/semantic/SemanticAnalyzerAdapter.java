package com.compiladores.sqlplatform.service.semantic;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.SemanticResult;
import com.compiladores.sqlplatform.service.compiler.SemanticAnalyzerPort;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SemanticAnalyzerAdapter implements SemanticAnalyzerPort {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "(?is)^\\s*SELECT\\s+(?<columns>.+?)\\s+FROM\\s+(?<table>[a-zA-Z_][\\w]*)\\s*(?<tail>.*)$"
    );
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "(?is).*\\bWHERE\\s+(?<left>[a-zA-Z_][\\w]*)\\s*(?<op>=|!=|<>|>=|<=|>|<)\\s*(?<right>[^;]+).*"
    );
    private static final Set<DatabaseEngine> RELATIONAL_ENGINES = Set.of(
            DatabaseEngine.SQL,
            DatabaseEngine.POSTGRESQL,
            DatabaseEngine.MYSQL,
            DatabaseEngine.SQL_SERVER
    );

    private final CatalogService catalogService;

    public SemanticAnalyzerAdapter(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public SemanticResult analyze(AstNode ast, DatabaseEngine engine) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> symbols = new LinkedHashMap<>();

        if (ast == null) {
            errors.add("No se recibio AST para el analisis semantico.");
            return result(errors, warnings, symbols);
        }

        QueryShape query = QueryShape.from(ast);
        symbols.put("engine", engine.name());
        symbols.put("rootNode", ast.getType());
        symbols.put("semanticStatus", "INITIAL_SEMANTIC_ANALYZER");

        if (query.operation().isEmpty()) {
            warnings.add("No se pudo identificar la operacion principal desde el AST actual.");
            return result(errors, warnings, symbols);
        }

        symbols.put("operation", query.operation().get());
        validateEngineSupport(query.operation().get(), engine, errors);

        if ("SELECT".equals(query.operation().get())) {
            validateSelect(query, engine, errors, warnings, symbols);
        } else {
            warnings.add("La validacion semantica detallada de " + query.operation().get()
                    + " todavia no esta implementada.");
        }

        return result(errors, warnings, symbols);
    }

    private void validateEngineSupport(String operation, DatabaseEngine engine, List<String> errors) {
        if (RELATIONAL_ENGINES.contains(engine)) {
            if (!Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP").contains(operation)) {
                errors.add("La operacion " + operation + " no esta soportada por el motor " + engine.name() + ".");
            }
            return;
        }

        if (engine == DatabaseEngine.MONGODB && !"FIND".equals(operation)) {
            errors.add("La operacion " + operation + " no esta soportada por el motor MONGODB.");
            return;
        }

        if (engine == DatabaseEngine.REDIS && !Set.of("GET", "SET", "DEL", "HGET", "HSET", "LPUSH", "RPUSH")
                .contains(operation)) {
            errors.add("La operacion " + operation + " no esta soportada por el motor REDIS.");
            return;
        }

        if (engine == DatabaseEngine.NOSQL && Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP")
                .contains(operation)) {
            errors.add("La operacion " + operation + " no esta soportada por el motor NOSQL generico.");
        }
    }

    private void validateSelect(
            QueryShape query,
            DatabaseEngine engine,
            List<String> errors,
            List<String> warnings,
            Map<String, Object> symbols
    ) {
        if (!RELATIONAL_ENGINES.contains(engine)) {
            return;
        }

        if (query.tableName().isEmpty()) {
            warnings.add("No se pudo identificar la tabla desde el AST actual.");
            return;
        }

        String tableName = query.tableName().get();
        symbols.put("table", tableName);
        Optional<TableDefinition> table = catalogService.findTable(engine, tableName);
        if (table.isEmpty()) {
            errors.add("La tabla '" + tableName + "' no existe en el catalogo para " + engine.name() + ".");
            return;
        }

        List<String> selectedColumns = query.columns();
        symbols.put("columns", selectedColumns);
        if (!selectedColumns.contains("*")) {
            for (String column : selectedColumns) {
                if (table.get().findColumn(column).isEmpty()) {
                    errors.add("La columna '" + column + "' no existe en la tabla '" + tableName + "'.");
                }
            }
        }

        query.where().ifPresent(where -> validateWhere(where, table.get(), tableName, errors, symbols));
    }

    private void validateWhere(
            WhereCondition where,
            TableDefinition table,
            String tableName,
            List<String> errors,
            Map<String, Object> symbols
    ) {
        Optional<ColumnDefinition> leftColumn = table.findColumn(where.left());
        if (leftColumn.isEmpty()) {
            errors.add("La columna '" + where.left() + "' no existe en la tabla '" + tableName + "'.");
            return;
        }

        String rightType = inferLiteralType(where.right(), table);
        symbols.put("where", Map.of(
                "column", where.left(),
                "operator", where.operator(),
                "rightType", rightType
        ));

        if (!typesAreCompatible(leftColumn.get().dataType(), rightType, where.operator())) {
            errors.add("Tipos incompatibles en WHERE: columna '" + where.left() + "' es "
                    + leftColumn.get().dataType() + " pero se compara con " + rightType + ".");
        }
    }

    private String inferLiteralType(String value, TableDefinition table) {
        String trimmed = value.trim();
        if (trimmed.matches("'[^']*'|\"[^\"]*\"")) {
            return "VARCHAR";
        }
        if (trimmed.matches("-?\\d+")) {
            return "INTEGER";
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            return "DECIMAL";
        }
        return table.findColumn(trimmed)
                .map(ColumnDefinition::dataType)
                .orElse("UNKNOWN");
    }

    private boolean typesAreCompatible(String left, String right, String operator) {
        if ("UNKNOWN".equals(right)) {
            return true;
        }
        if (left.equals(right)) {
            return true;
        }
        if (isNumeric(left) && isNumeric(right)) {
            return true;
        }
        return Set.of("=", "!=", "<>").contains(operator) && "VARCHAR".equals(left) && "VARCHAR".equals(right);
    }

    private boolean isNumeric(String dataType) {
        return Set.of("INTEGER", "DECIMAL", "FLOAT", "DOUBLE", "NUMERIC").contains(dataType);
    }

    private SemanticResult result(List<String> errors, List<String> warnings, Map<String, Object> symbols) {
        symbols.put(SemanticSymbols.ERRORS, List.copyOf(errors));
        return new SemanticResult(errors.isEmpty(), List.copyOf(warnings), Map.copyOf(symbols));
    }

    private record QueryShape(
            Optional<String> operation,
            Optional<String> tableName,
            List<String> columns,
            Optional<WhereCondition> where
    ) {

        static QueryShape from(AstNode ast) {
            String rawQuery = String.valueOf(ast.getValue() == null ? "" : ast.getValue());
            String normalized = stripLineComments(rawQuery).trim();
            String operation = attributeString(ast, "operation")
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .orElseGet(() -> firstWord(normalized));
            Optional<String> tableFromAttributes = attributeString(ast, "tableName");
            List<String> columnsFromAttributes = attributeStringList(ast, "columns");

            if ("SELECT".equals(operation)) {
                Matcher select = SELECT_PATTERN.matcher(normalized);
                if (select.matches()) {
                    String rawColumns = select.group("columns");
                    String table = select.group("table");
                    String tail = select.group("tail");
                    return new QueryShape(
                            Optional.of(operation),
                            tableFromAttributes.or(() -> Optional.of(table)),
                            columnsFromAttributes.isEmpty() ? parseColumns(rawColumns) : columnsFromAttributes,
                            parseWhere(tail)
                    );
                }
            }

            return new QueryShape(
                    Optional.ofNullable(operation.isBlank() ? null : operation),
                    tableFromAttributes,
                    columnsFromAttributes,
                    Optional.empty()
            );
        }

        private static Optional<String> attributeString(AstNode ast, String key) {
            if (ast.getAttributes() == null) {
                return Optional.empty();
            }

            Object value = ast.getAttributes().get(key);
            if (value instanceof String text && !text.isBlank()) {
                return Optional.of(text.trim());
            }

            return Optional.empty();
        }

        private static List<String> attributeStringList(AstNode ast, String key) {
            if (ast.getAttributes() == null) {
                return List.of();
            }

            Object value = ast.getAttributes().get(key);
            if (value instanceof List<?> list) {
                return list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(String::trim)
                        .filter(column -> !column.isBlank())
                        .toList();
            }

            return List.of();
        }

        private static String stripLineComments(String query) {
            return Arrays.stream(query.split("\\R"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .reduce("", (left, right) -> left + " " + right);
        }

        private static String firstWord(String query) {
            Matcher matcher = Pattern.compile("^\\s*([a-zA-Z_][\\w]*)").matcher(query);
            return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
        }

        private static List<String> parseColumns(String rawColumns) {
            if ("*".equals(rawColumns.trim())) {
                return List.of("*");
            }

            return Arrays.stream(rawColumns.split(","))
                    .map(column -> column.trim().replaceAll("[;]$", ""))
                    .filter(column -> !column.isBlank())
                    .toList();
        }

        private static Optional<WhereCondition> parseWhere(String tail) {
            Matcher where = WHERE_PATTERN.matcher(tail);
            if (!where.matches()) {
                return Optional.empty();
            }

            return Optional.of(new WhereCondition(
                    where.group("left").trim(),
                    where.group("op").trim(),
                    where.group("right").trim().replaceAll("[;]$", "")
            ));
        }
    }

    private record WhereCondition(String left, String operator, String right) {
    }
}

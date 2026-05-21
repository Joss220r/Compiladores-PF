package com.compiladores.sqlplatform.service.semantic;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.SemanticResult;
import com.compiladores.sqlplatform.model.ValidationIssue;
import com.compiladores.sqlplatform.service.compiler.SemanticAnalyzerPort;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "(?is)^\\s*INSERT\\s+INTO\\s+(?<table>[a-zA-Z_][\\w]*)\\s*(\\((?<columns>[^)]*)\\))?\\s+VALUES\\s*\\((?<values>.*)\\)\\s*;?\\s*$"
    );
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "(?is)^\\s*UPDATE\\s+(?<table>[a-zA-Z_][\\w]*)\\s+SET\\s+(?<assignments>.*?)(\\s+WHERE\\s+(?<where>.*))?;?\\s*$"
    );
    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "(?is)^\\s*DELETE\\s+FROM\\s+(?<table>[a-zA-Z_][\\w]*)(\\s+WHERE\\s+(?<where>.*))?;?\\s*$"
    );
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?is)^\\s*CREATE\\s+TABLE\\s+(?<table>[a-zA-Z_][\\w]*)\\s*\\((?<columns>.*)\\)\\s*;?\\s*$"
    );
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile(
            "(?is)^\\s*DROP\\s+TABLE\\s+(?<table>[a-zA-Z_][\\w]*)\\s*;?\\s*$"
    );
    private static final Set<DatabaseEngine> RELATIONAL_ENGINES = Set.of(
            DatabaseEngine.SQL,
            DatabaseEngine.POSTGRESQL,
            DatabaseEngine.MYSQL,
            DatabaseEngine.SQL_SERVER
    );

    private final CatalogService catalogService;
    private final ThreadLocal<List<ValidationIssue>> issues = ThreadLocal.withInitial(List::of);

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
            setIssues(errors, warnings);
            return result(errors, warnings, symbols);
        }

        QueryShape query = QueryShape.from(ast);
        symbols.put("engine", engine.name());
        symbols.put("rootNode", ast.getType());
        symbols.put("semanticStatus", "INITIAL_SEMANTIC_ANALYZER");

        if (query.operation().isEmpty()) {
            warnings.add("No se pudo identificar la operacion principal desde el AST actual.");
            setIssues(errors, warnings);
            return result(errors, warnings, symbols);
        }

        symbols.put("operation", query.operation().get());
        if (RELATIONAL_ENGINES.contains(engine) || engine == DatabaseEngine.NOSQL) {
            validateEngineSupport(query.operation().get(), engine, errors);
        }

        if ("SELECT".equals(query.operation().get())) {
            validateSelect(query, engine, errors, warnings, symbols);
        } else if (engine == DatabaseEngine.MONGODB) {
            validateMongo(ast, errors, warnings, symbols);
        } else if (engine == DatabaseEngine.REDIS) {
            validateRedis(ast, errors, warnings, symbols);
        } else if ("INSERT".equals(query.operation().get())) {
            validateInsert(query.rawQuery(), errors, warnings, symbols);
        } else if ("UPDATE".equals(query.operation().get())) {
            validateUpdate(query.rawQuery(), errors, warnings, symbols);
        } else if ("DELETE".equals(query.operation().get())) {
            validateDelete(query.rawQuery(), errors, warnings, symbols);
        } else if ("CREATE".equals(query.operation().get())) {
            validateCreateTable(query.rawQuery(), engine, errors, warnings, symbols);
        } else if ("DROP".equals(query.operation().get())) {
            validateDropTable(query.rawQuery(), warnings, symbols);
        } else {
            warnings.add("La validacion semantica detallada de " + query.operation().get()
                    + " todavia no esta implementada.");
        }

        setIssues(errors, warnings);
        return result(errors, warnings, symbols);
    }

    @Override
    public List<ValidationIssue> getIssues() {
        return issues.get();
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

    private void validateMongo(AstNode ast, List<String> errors, List<String> warnings, Map<String, Object> symbols) {
        String collection = stringAttribute(ast, "collection").orElse("");
        String operation = stringAttribute(ast, "operation").orElse("");
        List<String> arguments = listAttribute(findChild(ast, "Operation"), "arguments");

        symbols.put("collection", collection);
        symbols.put("mongoOperation", operation);

        if (collection.isBlank()) {
            errors.add("MongoDB requiere nombre de coleccion.");
        }
        if (operation.isBlank()) {
            errors.add("MongoDB requiere operacion.");
            return;
        }

        switch (operation) {
            case "find", "deleteOne", "deleteMany" -> {
                if (arguments.size() != 1 || !looksLikeObject(arguments.get(0))) {
                    errors.add(operation + " debe recibir un filtro en forma de objeto.");
                }
            }
            case "insertOne" -> {
                if (arguments.size() != 1 || !looksLikeObject(arguments.get(0))) {
                    errors.add("insertOne debe recibir un documento en forma de objeto.");
                }
            }
            case "insertMany" -> {
                if (arguments.size() != 1 || !arguments.get(0).trim().startsWith("[")) {
                    errors.add("insertMany debe recibir un arreglo de documentos.");
                }
            }
            case "updateOne", "updateMany" -> {
                if (arguments.size() != 2) {
                    errors.add(operation + " debe recibir filtro y objeto de actualizacion.");
                    return;
                }
                if (!looksLikeObject(arguments.get(0))) {
                    errors.add(operation + " debe recibir un filtro en forma de objeto.");
                }
                if (!looksLikeObject(arguments.get(1))) {
                    errors.add(operation + " debe recibir una actualizacion en forma de objeto.");
                }
                if (arguments.get(1).matches("(?is).*\\bset\\s*:.*") && !arguments.get(1).contains("$set")) {
                    errors.add("En MongoDB usa $set, no set, dentro de la actualizacion.");
                }
                if (!containsAny(arguments.get(1), "$set", "$gt", "$lt", "$gte", "$lte", "$in")) {
                    warnings.add(operation + " no usa operadores MongoDB reconocidos como $set.");
                }
            }
            default -> errors.add("Operacion MongoDB no soportada: " + operation + ".");
        }
    }

    private void validateRedis(AstNode ast, List<String> errors, List<String> warnings, Map<String, Object> symbols) {
        String command = stringAttribute(ast, "operation").orElse("").toUpperCase(Locale.ROOT);
        List<String> arguments = childValues(findChild(ast, "Arguments"));
        symbols.put("redisCommand", command);
        symbols.put("redisArguments", arguments);

        switch (command) {
            case "SET" -> requireArgumentCount(command, arguments, 2, errors);
            case "GET", "DEL", "TTL" -> requireArgumentCount(command, arguments, 1, errors);
            case "EXPIRE" -> {
                requireArgumentCount(command, arguments, 2, errors);
                if (arguments.size() >= 2 && !arguments.get(1).matches("\\d+")) {
                    errors.add("EXPIRE requiere segundos numericos.");
                }
            }
            case "HSET" -> requireArgumentCount(command, arguments, 3, errors);
            case "HGET" -> requireArgumentCount(command, arguments, 2, errors);
            case "HGETALL", "LPOP", "RPOP", "SMEMBERS" -> requireArgumentCount(command, arguments, 1, errors);
            case "LPUSH", "RPUSH", "SADD" -> requireArgumentCount(command, arguments, 2, errors);
            default -> errors.add("Comando Redis no soportado: " + command + ".");
        }
    }

    private void requireArgumentCount(String command, List<String> arguments, int expected, List<String> errors) {
        if (arguments.size() < expected) {
            errors.add("El comando " + command + " requiere al menos " + expected + " argumento(s).");
        }
    }

    private void validateInsert(String query, List<String> errors, List<String> warnings, Map<String, Object> symbols) {
        Matcher insert = INSERT_PATTERN.matcher(query);
        if (!insert.matches()) {
            errors.add("INSERT no cumple la forma INSERT INTO tabla (columnas) VALUES (valores).");
            return;
        }

        List<String> columns = parseList(insert.group("columns"));
        List<String> values = parseList(insert.group("values"));
        symbols.put("insertColumns", columns);
        symbols.put("insertValues", values);

        if (!columns.isEmpty() && columns.size() != values.size()) {
            errors.add("INSERT tiene " + columns.size() + " columna(s) pero " + values.size() + " valor(es).");
        }
        if (values.stream().anyMatch(String::isBlank)) {
            errors.add("INSERT contiene valores vacios.");
        }
        addDuplicateErrors(columns, "INSERT contiene columna repetida: ", errors);
        if (columns.isEmpty()) {
            warnings.add("INSERT no especifica columnas; se validara segun el orden fisico de la tabla.");
        }
    }

    private void validateUpdate(String query, List<String> errors, List<String> warnings, Map<String, Object> symbols) {
        Matcher update = UPDATE_PATTERN.matcher(query);
        if (!update.matches()) {
            errors.add("UPDATE no cumple la forma UPDATE tabla SET campo = valor.");
            return;
        }

        String assignmentsText = update.group("assignments");
        List<String> assignments = parseList(assignmentsText);
        symbols.put("assignments", assignments);

        if (assignments.isEmpty()) {
            errors.add("SET no puede estar vacio.");
        }

        List<String> assignedColumns = new ArrayList<>();
        for (String assignment : assignments) {
            String[] parts = assignment.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                errors.add("Asignacion incompleta en UPDATE: " + assignment + ".");
            } else {
                assignedColumns.add(parts[0].trim());
            }
        }
        addDuplicateErrors(assignedColumns, "UPDATE contiene campo repetido en SET: ", errors);

        if (update.group("where") == null || update.group("where").isBlank()) {
            warnings.add("UPDATE sin WHERE puede afectar multiples registros.");
        }
    }

    private void validateDelete(String query, List<String> errors, List<String> warnings, Map<String, Object> symbols) {
        Matcher delete = DELETE_PATTERN.matcher(query);
        if (!delete.matches()) {
            errors.add("DELETE debe usar la forma DELETE FROM tabla.");
            return;
        }
        symbols.put("table", delete.group("table"));
        if (delete.group("where") == null || delete.group("where").isBlank()) {
            warnings.add("DELETE sin WHERE puede eliminar multiples registros.");
        }
    }

    private void validateCreateTable(
            String query,
            DatabaseEngine engine,
            List<String> errors,
            List<String> warnings,
            Map<String, Object> symbols
    ) {
        Matcher create = CREATE_TABLE_PATTERN.matcher(query);
        if (!create.matches()) {
            errors.add("CREATE TABLE debe incluir nombre de tabla y columnas entre parentesis.");
            return;
        }

        List<String> definitions = parseList(create.group("columns"));
        symbols.put("createColumns", definitions);
        if (definitions.isEmpty()) {
            errors.add("CREATE TABLE debe incluir al menos una columna.");
            return;
        }

        Set<String> seenColumns = new HashSet<>();
        int primaryKeys = 0;
        for (String definition : definitions) {
            String[] parts = definition.trim().split("\\s+");
            if (parts.length < 2) {
                errors.add("Columna incompleta en CREATE TABLE: " + definition + ".");
                continue;
            }
            String columnName = parts[0].toLowerCase(Locale.ROOT);
            String dataType = parts[1].toUpperCase(Locale.ROOT);
            if (!seenColumns.add(columnName)) {
                errors.add("CREATE TABLE contiene columna repetida: " + parts[0] + ".");
            }
            if (!isTypeAllowed(dataType, engine)) {
                errors.add("Tipo de dato no valido para " + engine.name() + ": " + dataType + ".");
            }
            if (definition.toUpperCase(Locale.ROOT).contains("PRIMARY KEY")) {
                primaryKeys++;
            }
        }
        if (primaryKeys > 1) {
            errors.add("CREATE TABLE contiene mas de una PRIMARY KEY.");
        }
    }

    private void validateDropTable(String query, List<String> warnings, Map<String, Object> symbols) {
        Matcher drop = DROP_TABLE_PATTERN.matcher(query);
        if (drop.matches()) {
            symbols.put("table", drop.group("table"));
        }
        warnings.add("DROP TABLE es una operacion destructiva.");
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

    private boolean isTypeAllowed(String dataType, DatabaseEngine engine) {
        Set<String> common = Set.of("INT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "DATE", "TIMESTAMP", "BOOLEAN", "BOOL", "TEXT", "VARCHAR", "CHAR");
        if (common.contains(dataType)) {
            return true;
        }
        if (engine == DatabaseEngine.MYSQL) {
            return Set.of("AUTO_INCREMENT", "TINYINT", "DATETIME", "ENUM").contains(dataType);
        }
        if (engine == DatabaseEngine.POSTGRESQL) {
            return Set.of("SERIAL", "BIGSERIAL", "JSON", "JSONB").contains(dataType);
        }
        if (engine == DatabaseEngine.SQL_SERVER) {
            return Set.of("IDENTITY", "NVARCHAR", "BIT", "DATETIME2").contains(dataType);
        }
        return false;
    }

    private Optional<AstNode> findChild(AstNode ast, String type) {
        if (ast == null || ast.getChildren() == null) {
            return Optional.empty();
        }
        return ast.getChildren().stream()
                .filter(child -> type.equals(child.getType()))
                .findFirst();
    }

    private Optional<String> stringAttribute(AstNode ast, String key) {
        if (ast == null || ast.getAttributes() == null) {
            return Optional.empty();
        }
        Object value = ast.getAttributes().get(key);
        return value instanceof String text ? Optional.of(text) : Optional.empty();
    }

    private List<String> listAttribute(Optional<AstNode> ast, String key) {
        if (ast.isEmpty() || ast.get().getAttributes() == null) {
            return List.of();
        }
        Object value = ast.get().getAttributes().get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private List<String> childValues(Optional<AstNode> ast) {
        if (ast.isEmpty() || ast.get().getChildren() == null) {
            return List.of();
        }
        return ast.get().getChildren().stream()
                .map(AstNode::getValue)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private boolean looksLikeObject(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private boolean containsAny(String value, String... options) {
        for (String option : options) {
            if (value.contains(option)) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .toList();
    }

    private void addDuplicateErrors(List<String> values, String messagePrefix, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                errors.add(messagePrefix + value + ".");
            }
        }
    }

    private void setIssues(List<String> errors, List<String> warnings) {
        List<ValidationIssue> nextIssues = new ArrayList<>();
        errors.forEach(error -> nextIssues.add(ValidationIssue.error("SEMANTIC", error, 1, 1, "")));
        warnings.forEach(warning -> nextIssues.add(ValidationIssue.warning("SEMANTIC", warning, 1, 1, "")));
        issues.set(List.copyOf(nextIssues));
    }

    private SemanticResult result(List<String> errors, List<String> warnings, Map<String, Object> symbols) {
        symbols.put(SemanticSymbols.ERRORS, List.copyOf(errors));
        return new SemanticResult(errors.isEmpty(), List.copyOf(warnings), Map.copyOf(symbols));
    }

    private record QueryShape(
            Optional<String> operation,
            String rawQuery,
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
                            normalized,
                            tableFromAttributes.or(() -> Optional.of(table)),
                            columnsFromAttributes.isEmpty() ? parseColumns(rawColumns) : columnsFromAttributes,
                            parseWhere(tail)
                    );
                }
            }

            return new QueryShape(
                    Optional.ofNullable(operation.isBlank() ? null : operation),
                    normalized,
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

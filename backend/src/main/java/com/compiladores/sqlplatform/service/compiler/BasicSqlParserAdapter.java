package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import com.compiladores.sqlplatform.model.ValidationIssue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BasicSqlParserAdapter implements ParserPort {

    private static final String PARSER_READY = "BASIC_SQL_PARSER";
    private static final String PARSER_ERROR = "BASIC_SQL_PARSER_WITH_ERRORS";
    private static final Pattern MONGO_PATTERN = Pattern.compile(
            "(?is)^\\s*db\\.([a-zA-Z_][\\w]*)\\.([a-zA-Z_][\\w]*)\\s*\\((.*)\\)\\s*;?\\s*$"
    );
    private static final Set<String> MONGO_OPERATIONS = Set.of(
            "find", "insertOne", "insertMany", "updateOne", "updateMany", "deleteOne", "deleteMany"
    );
    private static final Map<String, Integer> REDIS_MIN_ARGS = Map.ofEntries(
            Map.entry("SET", 2),
            Map.entry("GET", 1),
            Map.entry("DEL", 1),
            Map.entry("EXISTS", 1),
            Map.entry("EXPIRE", 2),
            Map.entry("TTL", 1),
            Map.entry("HSET", 3),
            Map.entry("HGET", 2),
            Map.entry("HGETALL", 1),
            Map.entry("LPUSH", 2),
            Map.entry("RPUSH", 2),
            Map.entry("LPOP", 1),
            Map.entry("RPOP", 1),
            Map.entry("SADD", 2),
            Map.entry("SMEMBERS", 1)
    );

    private final ThreadLocal<List<ValidationIssue>> issues = ThreadLocal.withInitial(List::of);

    @Override
    public AstNode parse(List<TokenInfo> tokens, String query, DatabaseEngine engine) {
        issues.set(List.of());
        if (engine == DatabaseEngine.MONGODB) {
            return parseMongo(query);
        }
        if (engine == DatabaseEngine.REDIS) {
            return parseRedis(tokens, query);
        }

        ParserState state = new ParserState(expand(tokens));
        List<String> errors = new ArrayList<>();
        AstNode statement = parseStatement(state, errors);

        if (!state.isAtEnd()) {
            errors.add("Token inesperado '" + state.peek().lexeme() + "' al final de la sentencia.");
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("engine", engine.name());
        attributes.put("parserStatus", errors.isEmpty() ? PARSER_READY : PARSER_ERROR);
        attributes.put("errors", errors);

        List<AstNode> children = new ArrayList<>();
        if (statement != null) {
            children.add(statement);
            attributes.put("statementType", statement.getType());
        }

        List<ValidationIssue> parserIssues = errors.stream()
                .map(error -> issue(error, state.currentLine(), state.currentColumn(), state.currentFragment()))
                .toList();
        issues.set(parserIssues);

        for (String error : errors) {
            children.add(node("SyntaxError", error, Map.of(), List.of()));
        }

        return node("Query", query, attributes, children);
    }

    @Override
    public List<ValidationIssue> getIssues() {
        return issues.get();
    }

    private AstNode parseMongo(String query) {
        List<ValidationIssue> parserIssues = new ArrayList<>();
        Matcher matcher = MONGO_PATTERN.matcher(query == null ? "" : query);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("engine", DatabaseEngine.MONGODB.name());

        if (!matcher.matches()) {
            parserIssues.add(issue(
                    "MongoDB debe usar la forma db.coleccion.operacion(...).",
                    1,
                    1,
                    firstFragment(query)
            ));
            attributes.put("parserStatus", PARSER_ERROR);
            attributes.put("errors", messages(parserIssues));
            issues.set(parserIssues);
            return node("MongoQuery", query, attributes, syntaxErrorChildren(parserIssues));
        }

        String collection = matcher.group(1);
        String operation = matcher.group(2);
        String rawArguments = matcher.group(3).trim();
        List<String> arguments = splitTopLevelArguments(rawArguments);

        attributes.put("collection", collection);
        attributes.put("operation", operation);
        attributes.put("argumentCount", arguments.size());

        if (!MONGO_OPERATIONS.contains(operation)) {
            parserIssues.add(issue("Operacion MongoDB no soportada: " + operation + ".", 1, query.indexOf(operation) + 1, operation));
        }
        if ((operation.equals("updateOne") || operation.equals("updateMany")) && arguments.size() != 2) {
            parserIssues.add(issue(operation + " requiere filtro y actualización.", 1, query.indexOf(operation) + 1, operation));
        }
        if (operation.equals("find") && arguments.size() > 1) {
            parserIssues.add(issue(operation + " recibe como maximo un filtro.", 1, query.indexOf(operation) + 1, operation));
        }
        if ((operation.equals("insertOne") || operation.equals("deleteOne")
                || operation.equals("deleteMany")) && arguments.size() != 1) {
            parserIssues.add(issue(operation + " requiere exactamente un argumento.", 1, query.indexOf(operation) + 1, operation));
        }
        if (operation.equals("insertMany") && (arguments.size() != 1 || !rawArguments.stripLeading().startsWith("["))) {
            parserIssues.add(issue("insertMany requiere un arreglo de documentos.", 1, query.indexOf(operation) + 1, operation));
        }
        attributes.put("parserStatus", parserIssues.isEmpty() ? "BASIC_MONGODB_PARSER" : PARSER_ERROR);
        attributes.put("errors", messages(parserIssues));
        issues.set(parserIssues);

        return node("MongoQuery", query, attributes, List.of(
                node("Collection", collection, Map.of(), List.of()),
                node("Operation", operation, Map.of("arguments", arguments), List.of())
        ));
    }

    private AstNode parseRedis(List<TokenInfo> tokens, String query) {
        List<ValidationIssue> parserIssues = new ArrayList<>();
        List<TokenInfo> meaningfulTokens = tokens.stream()
                .filter(token -> !"COMMENT".equals(token.getType()))
                .toList();
        List<String> words = splitRedisWords(query);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("engine", DatabaseEngine.REDIS.name());

        if (meaningfulTokens.isEmpty() || words.isEmpty()) {
            parserIssues.add(issue("Redis requiere un comando.", 1, 1, ""));
            attributes.put("parserStatus", PARSER_ERROR);
            attributes.put("errors", messages(parserIssues));
            issues.set(parserIssues);
            return node("RedisCommand", query, attributes, syntaxErrorChildren(parserIssues));
        }

        TokenInfo commandToken = meaningfulTokens.get(0);
        String command = words.get(0).toUpperCase(Locale.ROOT);
        List<String> arguments = words.subList(1, words.size());
        attributes.put("operation", command);
        attributes.put("argumentCount", arguments.size());

        Integer minArgs = REDIS_MIN_ARGS.get(command);
        if (minArgs == null) {
            parserIssues.add(issue("Comando Redis no soportado: " + command + ".", commandToken.getLine(), commandToken.getColumn(), commandToken.getLexeme()));
        } else if (arguments.size() < minArgs) {
            parserIssues.add(issue(redisArgumentMessage(command, minArgs),
                    commandToken.getLine(), commandToken.getColumn(), commandToken.getLexeme()));
        }
        if ("EXPIRE".equals(command) && arguments.size() >= 2 && !arguments.get(1).matches("\\d+")) {
            parserIssues.add(issue("EXPIRE requiere segundos numéricos.", 1, query.indexOf(arguments.get(1)) + 1, arguments.get(1)));
        }

        attributes.put("parserStatus", parserIssues.isEmpty() ? "BASIC_REDIS_PARSER" : PARSER_ERROR);
        attributes.put("errors", messages(parserIssues));
        issues.set(parserIssues);

        return node("RedisCommand", query, attributes, List.of(
                node("Command", command, tokenAttributes(new SqlToken(commandToken.getType(), commandToken.getLexeme(), commandToken.getLine(), commandToken.getColumn())), List.of()),
                node("Arguments", null, Map.of("count", arguments.size()), arguments.stream()
                        .map(argument -> tokenNode("Argument", new SqlToken("ARGUMENT", argument, 1, query.indexOf(argument) + 1)))
                        .toList())
        ));
    }

    private String redisArgumentMessage(String command, int minArgs) {
        return switch (command) {
            case "SET" -> "El comando SET requiere key y value.";
            case "GET" -> "El comando GET requiere key.";
            case "DEL" -> "El comando DEL requiere key.";
            case "EXISTS" -> "El comando EXISTS requiere key.";
            case "EXPIRE" -> "El comando EXPIRE requiere key y seconds.";
            case "TTL" -> "El comando TTL requiere key.";
            case "HSET" -> "El comando HSET requiere key, field y value.";
            case "HGET" -> "El comando HGET requiere key y field.";
            case "HGETALL" -> "El comando HGETALL requiere key.";
            case "LPUSH" -> "El comando LPUSH requiere key y value.";
            case "RPUSH" -> "El comando RPUSH requiere key y value.";
            case "LPOP" -> "El comando LPOP requiere key.";
            case "RPOP" -> "El comando RPOP requiere key.";
            case "SADD" -> "El comando SADD requiere key y member.";
            case "SMEMBERS" -> "El comando SMEMBERS requiere key.";
            default -> "El comando " + command + " requiere al menos " + minArgs + " argumento(s).";
        };
    }

    private AstNode parseStatement(ParserState state, List<String> errors) {
        if (state.isAtEnd()) {
            errors.add("No se encontraron tokens para analizar.");
            return null;
        }

        if (state.matchKeyword("SELECT")) {
            return parseSelect(state, errors);
        }
        if (state.matchKeyword("WITH")) {
            return parseWith(state, errors);
        }
        if (state.matchKeyword("INSERT")) {
            return parseInsert(state, errors);
        }
        if (state.matchKeyword("UPDATE")) {
            return parseUpdate(state, errors);
        }
        if (state.matchKeyword("DELETE")) {
            return parseDelete(state, errors);
        }
        if (state.matchKeyword("CREATE")) {
            return parseCreateTable(state, errors);
        }
        if (state.matchKeyword("DROP")) {
            return parseDropTable(state, errors);
        }

        errors.add("Se esperaba SELECT, INSERT, UPDATE, DELETE, CREATE, DROP o WITH y se encontro '" + state.peek().lexeme() + "'.");
        return null;
    }

    private AstNode parseWith(ParserState state, List<String> errors) {
        List<AstNode> ctes = new ArrayList<>();

        while (!state.isAtEnd()) {
            SqlToken cteName = consumeIdentifier(state, "nombre de CTE despues de WITH", errors);
            if (state.matchKeyword("AS")) {
                if (state.matchSymbol("(")) {
                    ctes.add(node("Cte", valueOf(cteName), tokenAttributes(cteName),
                            List.of(parseSubqueryAfterOpeningParenthesis(state, "WITH", errors))));
                } else {
                    errors.add("La CTE " + valueOf(cteName) + " debe usar AS (SELECT ...).");
                }
            } else {
                errors.add("La CTE " + valueOf(cteName) + " debe incluir AS.");
            }

            if (state.matchSymbol(",")) {
                continue;
            }
            break;
        }

        AstNode mainStatement = null;
        if (state.matchKeyword("SELECT")) {
            mainStatement = parseSelect(state, errors);
        } else {
            errors.add("WITH debe terminar con una consulta SELECT principal.");
        }

        List<AstNode> children = new ArrayList<>();
        children.add(node("CteList", null, Map.of("count", ctes.size()), ctes));
        if (mainStatement != null) {
            children.add(mainStatement);
        }
        return node("WithStatement", "WITH", Map.of(), children);
    }

    private AstNode parseSelect(ParserState state, List<String> errors) {
        List<AstNode> children = new ArrayList<>();
        children.add(parseProjectionList(state, errors));

        if (state.matchKeyword("FROM")) {
            children.add(parseFromClause(state, errors));
            children.addAll(parseJoinClauses(state, errors));
        } else {
            errors.add("La sentencia SELECT debe incluir FROM.");
        }

        parseOptionalWhere(state, errors, children);
        parseOptionalSelectClauses(state, errors, children);
        consumeOptionalSemicolon(state);

        return node("SelectStatement", "SELECT", Map.of(), children);
    }

    private List<AstNode> parseJoinClauses(ParserState state, List<String> errors) {
        List<AstNode> joins = new ArrayList<>();
        while (isJoinStart(state)) {
            List<AstNode> children = new ArrayList<>();
            List<String> joinType = new ArrayList<>();
            while (!state.isAtEnd() && !state.checkKeyword("JOIN")) {
                joinType.add(state.advance().lexeme());
            }
            if (!state.matchKeyword("JOIN")) {
                errors.add("JOIN debe indicar la tabla a unir.");
                break;
            }
            children.add(parseFromClause(state, errors));
            if (state.matchKeyword("ON")) {
                children.add(parseExpressionUntil(state, "JoinCondition",
                        Set.of("WHERE", "GROUP", "HAVING", "ORDER", "LIMIT", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS")));
            } else if (state.matchKeyword("USING")) {
                if (state.matchSymbol("(")) {
                    children.add(parseDelimitedList(state, "UsingClause", "Column", ")", errors));
                } else {
                    errors.add("USING debe incluir columnas entre parentesis.");
                }
            } else {
                errors.add("JOIN debe incluir ON o USING con la condicion de union.");
            }
            joins.add(node("JoinClause", String.join(" ", joinType).trim(), Map.of(), children));
        }
        return joins;
    }

    private boolean isJoinStart(ParserState state) {
        return state.checkKeyword("JOIN")
                || state.checkKeyword("INNER")
                || state.checkKeyword("LEFT")
                || state.checkKeyword("RIGHT")
                || state.checkKeyword("FULL")
                || state.checkKeyword("CROSS")
                || state.checkKeyword("NATURAL");
    }

    private AstNode parseFromClause(ParserState state, List<String> errors) {
        if (state.matchSymbol("(")) {
            AstNode subquery = parseSubqueryAfterOpeningParenthesis(state, "FROM", errors);
            List<AstNode> children = new ArrayList<>();
            children.add(subquery);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("source", "subquery");

            state.matchKeyword("AS");
            if (!state.isAtEnd() && !state.peek().isClauseBoundary() && !state.checkKeyword("WHERE")) {
                SqlToken alias = state.advance();
                attributes.put("alias", alias.lexeme());
                children.add(tokenNode("Alias", alias));
            } else {
                errors.add("La subconsulta en FROM debe tener un alias.");
            }

            return node("FromClause", "SUBQUERY", attributes, children);
        }

        SqlToken table = consumeIdentifier(state, "nombre de tabla despues de FROM", errors);
        Map<String, Object> attributes = new LinkedHashMap<>(tokenAttributes(table));
        List<AstNode> children = new ArrayList<>();
        state.matchKeyword("AS");
        if (!state.isAtEnd() && !state.peek().isClauseBoundary() && !state.checkKeyword("WHERE")) {
            SqlToken alias = state.advance();
            attributes.put("alias", alias.lexeme());
            children.add(tokenNode("Alias", alias));
        }
        return node("FromClause", valueOf(table), attributes, children);
    }

    private AstNode parseProjectionList(ParserState state, List<String> errors) {
        List<AstNode> projections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        SqlToken start = null;
        int depth = 0;

        while (!state.isAtEnd() && !(depth == 0 && state.checkKeyword("FROM"))) {
            SqlToken token = state.advance();
            if (start == null) {
                start = token;
            }
            if (token.isSymbol("(")) {
                depth++;
            } else if (token.isSymbol(")") && depth > 0) {
                depth--;
            }

            if (depth == 0 && ",".equals(token.lexeme())) {
                addProjection(projections, current, start);
                current.setLength(0);
                start = null;
                continue;
            }

            appendLexeme(current, token.lexeme());
        }
        addProjection(projections, current, start);

        if (projections.isEmpty()) {
            errors.add("La sentencia SELECT debe indicar al menos una columna o '*'.");
        }

        return node("ProjectionList", null, Map.of("count", projections.size()), projections);
    }

    private void addProjection(List<AstNode> projections, StringBuilder current, SqlToken start) {
        String value = current.toString().trim();
        if (!value.isBlank()) {
            projections.add(node("Projection", value, tokenAttributes(start), List.of()));
        }
    }

    private AstNode parseInsert(ParserState state, List<String> errors) {
        List<AstNode> children = new ArrayList<>();
        state.matchKeyword("INTO");

        SqlToken table = consumeIdentifier(state, "nombre de tabla en INSERT", errors);
        children.add(node("Table", valueOf(table), tokenAttributes(table), List.of()));

        if (state.matchSymbol("(")) {
            children.add(parseDelimitedList(state, "ColumnList", "Column", ")", errors));
        }

        if (!state.matchKeyword("VALUES")) {
            errors.add("La sentencia INSERT debe incluir VALUES.");
        } else if (state.matchSymbol("(")) {
            children.add(parseDelimitedList(state, "ValueList", "Value", ")", errors));
        } else {
            errors.add("VALUES debe estar seguido por una lista entre parentesis.");
        }

        consumeOptionalSemicolon(state);
        return node("InsertStatement", "INSERT", Map.of(), children);
    }

    private AstNode parseUpdate(ParserState state, List<String> errors) {
        List<AstNode> children = new ArrayList<>();
        SqlToken table = consumeIdentifier(state, "nombre de tabla en UPDATE", errors);
        children.add(node("Table", valueOf(table), tokenAttributes(table), List.of()));

        if (!state.matchKeyword("SET")) {
            errors.add("La sentencia UPDATE debe incluir SET.");
        } else {
            children.add(parseAssignments(state, errors));
        }

        parseOptionalWhere(state, errors, children);
        consumeOptionalSemicolon(state);
        return node("UpdateStatement", "UPDATE", Map.of(), children);
    }

    private AstNode parseDelete(ParserState state, List<String> errors) {
        List<AstNode> children = new ArrayList<>();
        state.matchKeyword("FROM");

        SqlToken table = consumeIdentifier(state, "nombre de tabla en DELETE", errors);
        children.add(node("Table", valueOf(table), tokenAttributes(table), List.of()));

        parseOptionalWhere(state, errors, children);
        consumeOptionalSemicolon(state);
        return node("DeleteStatement", "DELETE", Map.of(), children);
    }

    private AstNode parseCreateTable(ParserState state, List<String> errors) {
        List<AstNode> children = new ArrayList<>();
        if (!state.matchKeyword("TABLE")) {
            errors.add("CREATE debe estar seguido por TABLE.");
        }

        SqlToken table = consumeIdentifier(state, "nombre de tabla en CREATE TABLE", errors);
        children.add(node("Table", valueOf(table), tokenAttributes(table), List.of()));

        if (state.matchSymbol("(")) {
            children.add(parseDelimitedList(state, "ColumnDefinitionList", "ColumnDefinition", ")", errors));
        } else {
            errors.add("CREATE TABLE debe incluir columnas entre parentesis.");
        }

        consumeOptionalSemicolon(state);
        return node("CreateTableStatement", "CREATE TABLE", Map.of(), children);
    }

    private AstNode parseDropTable(ParserState state, List<String> errors) {
        List<AstNode> children = new ArrayList<>();
        if (!state.matchKeyword("TABLE")) {
            errors.add("DROP debe estar seguido por TABLE.");
        }

        SqlToken table = consumeIdentifier(state, "nombre de tabla en DROP TABLE", errors);
        children.add(node("Table", valueOf(table), tokenAttributes(table), List.of()));
        consumeOptionalSemicolon(state);
        return node("DropTableStatement", "DROP TABLE", Map.of(), children);
    }

    private AstNode parseAssignments(ParserState state, List<String> errors) {
        List<AstNode> assignments = new ArrayList<>();

        while (!state.isAtEnd() && !state.checkKeyword("WHERE") && !state.checkSymbol(";")) {
            if (state.matchSymbol(",")) {
                continue;
            }

            SqlToken column = consumeIdentifier(state, "columna en asignacion SET", errors);
            if (!state.matchOperator("=")) {
                errors.add("La asignacion SET debe usar '=' despues de " + valueOf(column) + ".");
            }
            SqlToken value = consumeValue(state, "valor en asignacion SET", errors);

            assignments.add(node(
                    "Assignment",
                    valueOf(column),
                    Map.of("operator", "=", "value", valueOf(value)),
                    List.of(tokenNode("Column", column), tokenNode("Value", value))
            ));
        }

        if (assignments.isEmpty()) {
            errors.add("La clausula SET debe incluir al menos una asignacion.");
        }

        return node("SetClause", null, Map.of("count", assignments.size()), assignments);
    }

    private void parseOptionalWhere(ParserState state, List<String> errors, List<AstNode> children) {
        if (state.matchKeyword("WHERE")) {
            children.add(parseWhere(state, errors));
        }
    }

    private AstNode parseWhere(ParserState state, List<String> errors) {
        List<AstNode> conditions = new ArrayList<>();
        List<String> connectors = new ArrayList<>();

        while (!state.isAtEnd() && !state.checkSymbol(";")) {
            if (state.checkKeyword("EXISTS")) {
                conditions.add(parseExistsCondition(state, errors));
            } else {
                conditions.add(parseCondition(state, errors));
            }

            if (state.matchKeyword("AND")) {
                connectors.add("AND");
            } else if (state.matchKeyword("OR")) {
                connectors.add("OR");
            } else {
                break;
            }
        }

        if (conditions.isEmpty()) {
            errors.add("La clausula WHERE debe incluir una condicion.");
        }

        return node("WhereClause", null, Map.of("connectors", connectors, "count", conditions.size()), conditions);
    }

    private AstNode parseCondition(ParserState state, List<String> errors) {
        SqlToken left = consumeValue(state, "operando izquierdo en WHERE", errors);

        if (state.matchKeyword("IN")) {
            List<AstNode> children = new ArrayList<>();
            children.add(tokenNode("LeftOperand", left));
            children.add(node("Operator", "IN", Map.of(), List.of()));
            children.add(parseInRightOperand(state, errors));
            return node(
                    "Condition",
                    valueOf(left),
                    Map.of("operator", "IN", "right", "SUBQUERY_OR_LIST"),
                    children
            );
        }

        SqlToken operator = consumeOperator(state, errors);
        if (state.matchKeyword("ALL") || state.matchKeyword("ANY")) {
            SqlToken quantifier = state.previous();
            if (state.matchSymbol("(") && state.checkKeyword("SELECT")) {
                AstNode subquery = parseSubqueryAfterOpeningParenthesis(state, quantifier.lexeme(), errors);
                return node(
                        "Condition",
                        valueOf(left),
                        Map.of("operator", valueOf(operator), "quantifier", quantifier.lexeme(), "right", "SUBQUERY"),
                        List.of(tokenNode("LeftOperand", left), tokenNode("Operator", operator), tokenNode("Quantifier", quantifier), subquery)
                );
            }
            errors.add(quantifier.lexeme() + " requiere una subconsulta entre parentesis.");
        }
        if (state.matchSymbol("(") && state.checkKeyword("SELECT")) {
            AstNode subquery = parseSubqueryAfterOpeningParenthesis(state, valueOf(operator), errors);
            return node(
                    "Condition",
                    valueOf(left),
                    Map.of("operator", valueOf(operator), "right", "SUBQUERY"),
                    List.of(tokenNode("LeftOperand", left), tokenNode("Operator", operator), subquery)
            );
        }

        SqlToken right = consumeValue(state, "operando derecho en WHERE", errors);

        return node(
                "Condition",
                valueOf(left),
                Map.of("operator", valueOf(operator), "right", valueOf(right)),
                List.of(tokenNode("LeftOperand", left), tokenNode("Operator", operator), tokenNode("RightOperand", right))
        );
    }

    private AstNode parseExistsCondition(ParserState state, List<String> errors) {
        SqlToken exists = state.advance();
        List<AstNode> children = new ArrayList<>();
        children.add(tokenNode("Operator", exists));

        if (!state.matchSymbol("(")) {
            errors.add("EXISTS requiere una subconsulta entre parentesis.");
            return node("ExistsCondition", "EXISTS", Map.of(), children);
        }

        children.add(parseSubqueryAfterOpeningParenthesis(state, "EXISTS", errors));
        return node("ExistsCondition", "EXISTS", Map.of("operator", "EXISTS"), children);
    }

    private AstNode parseInRightOperand(ParserState state, List<String> errors) {
        if (!state.matchSymbol("(")) {
            errors.add("IN requiere una subconsulta o lista entre parentesis.");
            return node("InOperand", null, Map.of("kind", "missing"), List.of());
        }

        if (state.checkKeyword("SELECT")) {
            return node("InOperand", null, Map.of("kind", "subquery"),
                    List.of(parseSubqueryAfterOpeningParenthesis(state, "IN", errors)));
        }

        List<AstNode> values = new ArrayList<>();
        while (!state.isAtEnd() && !state.checkSymbol(")")) {
            if (state.matchSymbol(",")) {
                continue;
            }
            values.add(tokenNode("Value", state.advance()));
        }

        if (!state.matchSymbol(")")) {
            errors.add("Se esperaba ')' para cerrar la lista de IN.");
        }
        if (values.isEmpty()) {
            errors.add("IN requiere al menos un valor o una subconsulta.");
        }

        return node("InOperand", null, Map.of("kind", "list", "count", values.size()), values);
    }

    private void parseOptionalSelectClauses(ParserState state, List<String> errors, List<AstNode> children) {
        while (!state.isAtEnd() && !state.checkSymbol(";") && !state.checkSymbol(")")) {
            if (state.matchKeyword("GROUP")) {
                if (!state.matchKeyword("BY")) {
                    errors.add("GROUP debe estar seguido por BY.");
                }
                children.add(parseExpressionUntil(state, "GroupByClause", Set.of("HAVING", "ORDER", "LIMIT")));
                continue;
            }
            if (state.matchKeyword("HAVING")) {
                children.add(parseExpressionUntil(state, "HavingClause", Set.of("ORDER", "LIMIT")));
                continue;
            }
            if (state.matchKeyword("ORDER")) {
                if (!state.matchKeyword("BY")) {
                    errors.add("ORDER debe estar seguido por BY.");
                }
                children.add(parseExpressionUntil(state, "OrderByClause", Set.of("LIMIT")));
                continue;
            }
            if (state.matchKeyword("LIMIT")) {
                children.add(parseExpressionUntil(state, "LimitClause", Set.of()));
                continue;
            }
            break;
        }
    }

    private AstNode parseExpressionUntil(ParserState state, String type, Set<String> boundaryKeywords) {
        List<AstNode> tokens = new ArrayList<>();
        int depth = 0;
        while (!state.isAtEnd() && !state.checkSymbol(";")) {
            if (depth == 0 && state.checkSymbol(")")) {
                break;
            }
            if (depth == 0 && boundaryKeywords.stream().anyMatch(state::checkKeyword)) {
                break;
            }
            SqlToken token = state.advance();
            if (token.isSymbol("(")) {
                depth++;
            } else if (token.isSymbol(")") && depth > 0) {
                depth--;
            }
            tokens.add(tokenNode("ExpressionToken", token));
        }
        return node(type, joinLexemesFromNodes(tokens), Map.of("count", tokens.size()), tokens);
    }

    private AstNode parseSubqueryAfterOpeningParenthesis(ParserState state, String context, List<String> errors) {
        SqlToken start = state.isAtEnd() ? SqlToken.missing("subconsulta") : state.peek();
        List<SqlToken> subqueryTokens = collectUntilMatchingParenthesis(state, context, errors);
        List<AstNode> children = new ArrayList<>();
        List<String> nestedErrors = new ArrayList<>();

        if (subqueryTokens.isEmpty()) {
            errors.add("La subconsulta de " + context + " no puede estar vacia.");
            return node("Subquery", "", Map.of("context", context), children);
        }

        ParserState nestedState = new ParserState(subqueryTokens);
        if (!nestedState.matchKeyword("SELECT")) {
            errors.add("La subconsulta de " + context + " debe iniciar con SELECT.");
            return node("Subquery", joinLexemes(subqueryTokens), Map.of("context", context), children);
        }

        children.add(parseSelect(nestedState, nestedErrors));
        if (!nestedState.isAtEnd()) {
            nestedErrors.add("Token inesperado '" + nestedState.peek().lexeme() + "' dentro de la subconsulta.");
        }
        errors.addAll(nestedErrors);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("context", context);
        attributes.put("line", start.line());
        attributes.put("column", start.column());
        attributes.put("errors", List.copyOf(nestedErrors));

        return node("Subquery", joinLexemes(subqueryTokens), attributes, children);
    }

    private List<SqlToken> collectUntilMatchingParenthesis(ParserState state, String context, List<String> errors) {
        List<SqlToken> collected = new ArrayList<>();
        int depth = 1;

        while (!state.isAtEnd()) {
            SqlToken token = state.advance();
            if (token.isSymbol("(")) {
                depth++;
                collected.add(token);
                continue;
            }
            if (token.isSymbol(")")) {
                depth--;
                if (depth == 0) {
                    return collected;
                }
                collected.add(token);
                continue;
            }
            collected.add(token);
        }

        errors.add("Falta cerrar la subconsulta de " + context + " con ')'.");
        return collected;
    }

    private AstNode parseDelimitedList(
            ParserState state,
            String listType,
            String itemType,
            String closingSymbol,
            List<String> errors
    ) {
        List<AstNode> items = new ArrayList<>();

        while (!state.isAtEnd() && !state.checkSymbol(closingSymbol)) {
            if (state.matchSymbol(",")) {
                continue;
            }

            SqlToken item = state.advance();
            items.add(tokenNode(itemType, item));
        }

        if (!state.matchSymbol(closingSymbol)) {
            errors.add("Se esperaba '" + closingSymbol + "' para cerrar " + listType + ".");
        }

        if (items.isEmpty()) {
            errors.add(listType + " debe incluir al menos un elemento.");
        }

        return node(listType, null, Map.of("count", items.size()), items);
    }

    private SqlToken consumeIdentifier(ParserState state, String expected, List<String> errors) {
        if (!state.isAtEnd() && !state.peek().isControlToken()) {
            return state.advance();
        }

        errors.add("Se esperaba " + expected + ".");
        return SqlToken.missing(expected);
    }

    private SqlToken consumeValue(ParserState state, String expected, List<String> errors) {
        if (!state.isAtEnd() && !state.peek().isClauseBoundary()) {
            return consumeQualifiedValue(state);
        }

        errors.add("Se esperaba " + expected + ".");
        return SqlToken.missing(expected);
    }

    private SqlToken consumeQualifiedValue(ParserState state) {
        SqlToken first = state.advance();
        if (!state.isAtEnd() && state.checkSymbol(".") && state.hasNext()) {
            state.advance();
            SqlToken second = state.advance();
            return new SqlToken(first.type(), first.lexeme() + "." + second.lexeme(), first.line(), first.column());
        }
        return first;
    }

    private SqlToken consumeOperator(ParserState state, List<String> errors) {
        if (!state.isAtEnd() && state.peek().isOperator()) {
            return state.advance();
        }

        String found = state.isAtEnd() ? "fin de la query" : "'" + state.peek().lexeme() + "'";
        errors.add("Se esperaba operador de comparacion en WHERE y se encontro " + found + ".");
        return SqlToken.missing("operador");
    }

    private void consumeOptionalSemicolon(ParserState state) {
        state.matchSymbol(";");
    }

    private AstNode tokenNode(String type, SqlToken token) {
        return node(type, valueOf(token), tokenAttributes(token), List.of());
    }

    private static AstNode node(String type, String value, Map<String, Object> attributes, List<AstNode> children) {
        return new AstNode(type, value, attributes, children);
    }

    private static ValidationIssue issue(String message, int line, int column, String fragment) {
        return ValidationIssue.error("PARSER", message, line <= 0 ? 1 : line, column <= 0 ? 1 : column, fragment);
    }

    private static List<String> messages(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::getMessage).toList();
    }

    private static List<AstNode> syntaxErrorChildren(List<ValidationIssue> issues) {
        return issues.stream()
                .map(issue -> node("SyntaxError", issue.getMessage(), Map.of(
                        "line", issue.getLine(),
                        "column", issue.getColumn(),
                        "fragment", issue.getFragment()
                ), List.of()))
                .toList();
    }

    private static String firstFragment(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return query.trim().split("\\s+")[0];
    }

    private static List<String> splitTopLevelArguments(String rawArguments) {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;

        for (int index = 0; index < rawArguments.length(); index++) {
            char character = rawArguments.charAt(index);
            if (quote != 0) {
                current.append(character);
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                current.append(character);
                continue;
            }
            if (character == '{' || character == '[' || character == '(') {
                depth++;
            } else if (character == '}' || character == ']' || character == ')') {
                depth--;
            } else if (character == ',' && depth == 0) {
                arguments.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }

        if (!current.toString().trim().isEmpty()) {
            arguments.add(current.toString().trim());
        }
        return arguments;
    }

    private static List<String> splitRedisWords(String query) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;

        for (int index = 0; index < query.length(); index++) {
            char character = query.charAt(index);
            if (quote != 0) {
                current.append(character);
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                current.append(character);
                continue;
            }
            if (Character.isWhitespace(character)) {
                if (!current.isEmpty()) {
                    words.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(character);
        }

        if (!current.isEmpty()) {
            words.add(current.toString());
        }
        return words;
    }

    private static String valueOf(SqlToken token) {
        return token == null ? null : token.lexeme();
    }

    private static String joinLexemes(List<SqlToken> tokens) {
        StringBuilder joined = new StringBuilder();
        for (SqlToken token : tokens) {
            appendLexeme(joined, token.lexeme());
        }
        return joined.toString();
    }

    private static String joinLexemesFromNodes(List<AstNode> nodes) {
        StringBuilder joined = new StringBuilder();
        for (AstNode node : nodes) {
            appendLexeme(joined, node.getValue());
        }
        return joined.toString().trim();
    }

    private static void appendLexeme(StringBuilder joined, String lexeme) {
        if (lexeme == null || lexeme.isBlank()) {
            return;
        }
        if (!joined.isEmpty() && !Set.of(",", ")", ";", ".").contains(lexeme)
                && !"(".equals(lastCharacter(joined))
                && !".".equals(lastCharacter(joined))) {
            joined.append(' ');
        }
        joined.append(lexeme);
    }

    private static String lastCharacter(StringBuilder joined) {
        if (joined.isEmpty()) {
            return "";
        }
        return String.valueOf(joined.charAt(joined.length() - 1));
    }

    private static Map<String, Object> tokenAttributes(SqlToken token) {
        if (token == null) {
            return Map.of();
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("tokenType", token.type());
        attributes.put("line", token.line());
        attributes.put("column", token.column());
        return attributes;
    }

    private static List<SqlToken> expand(List<TokenInfo> tokens) {
        List<SqlToken> expanded = new ArrayList<>();

        for (TokenInfo token : tokens) {
            if ("COMMENT".equals(token.getType())) {
                continue;
            }
            expanded.addAll(splitToken(token));
        }

        return expanded;
    }

    private static List<SqlToken> splitToken(TokenInfo token) {
        List<SqlToken> result = new ArrayList<>();
        String lexeme = token.getLexeme();
        StringBuilder current = new StringBuilder();
        int currentColumn = token.getColumn();

        for (int index = 0; index < lexeme.length(); index++) {
            char character = lexeme.charAt(index);

            if (Character.isWhitespace(character)) {
                flushToken(result, current, token, currentColumn);
                currentColumn = token.getColumn() + index + 1;
                continue;
            }

            if (isSingleQuotedStringStart(character)) {
                flushToken(result, current, token, currentColumn);
                int literalStart = index;
                StringBuilder literal = new StringBuilder();
                literal.append(character);
                index++;
                while (index < lexeme.length()) {
                    char literalCharacter = lexeme.charAt(index);
                    literal.append(literalCharacter);
                    if (literalCharacter == '\'') {
                        break;
                    }
                    index++;
                }
                result.add(new SqlToken("STRING", literal.toString(), token.getLine(), token.getColumn() + literalStart));
                currentColumn = token.getColumn() + index + 1;
                continue;
            }

            String twoCharacterOperator = index + 1 < lexeme.length()
                    ? lexeme.substring(index, index + 2)
                    : "";
            if (isOperator(twoCharacterOperator)) {
                flushToken(result, current, token, currentColumn);
                result.add(new SqlToken("OPERATOR", twoCharacterOperator, token.getLine(), token.getColumn() + index));
                index++;
                currentColumn = token.getColumn() + index + 1;
                continue;
            }

            String single = String.valueOf(character);
            if (isSymbol(single) || isOperator(single)) {
                flushToken(result, current, token, currentColumn);
                result.add(new SqlToken(isOperator(single) ? "OPERATOR" : "SYMBOL", single, token.getLine(), token.getColumn() + index));
                currentColumn = token.getColumn() + index + 1;
                continue;
            }

            if (current.isEmpty()) {
                currentColumn = token.getColumn() + index;
            }
            current.append(character);
        }

        flushToken(result, current, token, currentColumn);
        return result;
    }

    private static void flushToken(List<SqlToken> result, StringBuilder current, TokenInfo source, int column) {
        if (current.isEmpty()) {
            return;
        }

        String lexeme = current.toString();
        result.add(new SqlToken(classify(lexeme, source.getType()), lexeme, source.getLine(), column));
        current.setLength(0);
    }

    private static String classify(String lexeme, String fallbackType) {
        String upper = lexeme.toUpperCase(Locale.ROOT);

        if (isKeyword(upper)) {
            return "KEYWORD";
        }
        if (lexeme.matches("[0-9]+(\\.[0-9]+)?")) {
            return "NUMBER";
        }

        return fallbackType;
    }

    private static boolean isSingleQuotedStringStart(char character) {
        return character == '\'';
    }

    private static boolean isKeyword(String lexeme) {
        return List.of(
                "SELECT", "INSERT", "INTO", "UPDATE", "DELETE", "FROM", "WHERE", "SET", "VALUES", "AND", "OR",
                "IN", "EXISTS", "AS", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "ON", "GROUP", "BY",
                "HAVING", "ORDER", "LIMIT", "WITH", "ALL", "ANY", "OUTER", "USING", "NATURAL"
        ).contains(lexeme);
    }

    private static boolean isSymbol(String lexeme) {
        return List.of(",", "(", ")", ";", "*", ".").contains(lexeme);
    }

    private static boolean isOperator(String lexeme) {
        return List.of("=", ">", "<", ">=", "<=", "!=", "<>").contains(lexeme);
    }

    private record SqlToken(String type, String lexeme, int line, int column) {

        private static SqlToken missing(String expected) {
            return new SqlToken("MISSING", "<" + expected + ">", -1, -1);
        }

        private boolean isKeyword(String expected) {
            return "KEYWORD".equals(type) && lexeme.equalsIgnoreCase(expected);
        }

        private boolean isSymbol(String expected) {
            return "SYMBOL".equals(type) && lexeme.equals(expected);
        }

        private boolean isOperator() {
            return "OPERATOR".equals(type) || BasicSqlParserAdapter.isOperator(lexeme);
        }

        private boolean isControlToken() {
            return isClauseBoundary() || isOperator();
        }

        private boolean isClauseBoundary() {
            return isSymbol(";")
                    || isSymbol(")")
                    || isSymbol(",")
                    || isKeyword("WHERE")
                    || isKeyword("AND")
                    || isKeyword("OR")
                    || isKeyword("ON")
                    || isKeyword("JOIN")
                    || isKeyword("INNER")
                    || isKeyword("LEFT")
                    || isKeyword("RIGHT")
                    || isKeyword("FULL")
                    || isKeyword("CROSS")
                    || isKeyword("NATURAL")
                    || isKeyword("USING")
                    || isKeyword("GROUP")
                    || isKeyword("HAVING")
                    || isKeyword("ORDER")
                    || isKeyword("LIMIT")
                    || isKeyword("FROM")
                    || isKeyword("SET")
                    || isKeyword("VALUES");
        }
    }

    private static class ParserState {

        private final List<SqlToken> tokens;
        private int position;

        ParserState(List<SqlToken> tokens) {
            this.tokens = tokens;
        }

        private boolean isAtEnd() {
            return position >= tokens.size();
        }

        private SqlToken peek() {
            return tokens.get(position);
        }

        private int currentLine() {
            if (tokens.isEmpty()) {
                return 1;
            }
            if (position >= tokens.size()) {
                return tokens.get(tokens.size() - 1).line();
            }
            return peek().line();
        }

        private int currentColumn() {
            if (tokens.isEmpty()) {
                return 1;
            }
            if (position >= tokens.size()) {
                return tokens.get(tokens.size() - 1).column();
            }
            return peek().column();
        }

        private String currentFragment() {
            if (tokens.isEmpty()) {
                return "";
            }
            if (position >= tokens.size()) {
                return tokens.get(tokens.size() - 1).lexeme();
            }
            return peek().lexeme();
        }

        private SqlToken advance() {
            return tokens.get(position++);
        }

        private SqlToken previous() {
            return tokens.get(position - 1);
        }

        private boolean checkKeyword(String keyword) {
            return !isAtEnd() && peek().isKeyword(keyword);
        }

        private boolean matchKeyword(String keyword) {
            if (checkKeyword(keyword)) {
                advance();
                return true;
            }
            return false;
        }

        private boolean checkSymbol(String symbol) {
            return !isAtEnd() && peek().isSymbol(symbol);
        }

        private boolean hasNext() {
            return position + 1 < tokens.size();
        }

        private boolean matchSymbol(String symbol) {
            if (checkSymbol(symbol)) {
                advance();
                return true;
            }
            return false;
        }

        private boolean matchOperator(String operator) {
            if (!isAtEnd() && peek().isOperator() && peek().lexeme().equals(operator)) {
                advance();
                return true;
            }
            return false;
        }
    }
}

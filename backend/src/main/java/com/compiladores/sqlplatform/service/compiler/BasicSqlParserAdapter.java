package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BasicSqlParserAdapter implements ParserPort {

    private static final String PARSER_READY = "BASIC_SQL_PARSER";
    private static final String PARSER_ERROR = "BASIC_SQL_PARSER_WITH_ERRORS";

    @Override
    public AstNode parse(List<TokenInfo> tokens, String query, DatabaseEngine engine) {
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

        for (String error : errors) {
            children.add(node("SyntaxError", error, Map.of(), List.of()));
        }

        return node("Query", query, attributes, children);
    }

    private AstNode parseStatement(ParserState state, List<String> errors) {
        if (state.isAtEnd()) {
            errors.add("No se encontraron tokens para analizar.");
            return null;
        }

        if (state.matchKeyword("SELECT")) {
            return parseSelect(state, errors);
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

        errors.add("Se esperaba SELECT, INSERT, UPDATE o DELETE y se encontro '" + state.peek().lexeme() + "'.");
        return null;
    }

    private AstNode parseSelect(ParserState state, List<String> errors) {
        List<AstNode> children = new ArrayList<>();
        children.add(parseProjectionList(state, errors));

        if (state.matchKeyword("FROM")) {
            SqlToken table = consumeIdentifier(state, "nombre de tabla despues de FROM", errors);
            children.add(node("FromClause", valueOf(table), tokenAttributes(table), List.of()));
        } else {
            errors.add("La sentencia SELECT debe incluir FROM.");
        }

        parseOptionalWhere(state, errors, children);
        consumeOptionalSemicolon(state);

        return node("SelectStatement", "SELECT", Map.of(), children);
    }

    private AstNode parseProjectionList(ParserState state, List<String> errors) {
        List<AstNode> projections = new ArrayList<>();

        while (!state.isAtEnd() && !state.checkKeyword("FROM")) {
            if (state.matchSymbol(",")) {
                continue;
            }

            SqlToken projection = state.advance();
            projections.add(node("Projection", projection.lexeme(), tokenAttributes(projection), List.of()));
        }

        if (projections.isEmpty()) {
            errors.add("La sentencia SELECT debe indicar al menos una columna o '*'.");
        }

        return node("ProjectionList", null, Map.of("count", projections.size()), projections);
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
            SqlToken left = consumeValue(state, "operando izquierdo en WHERE", errors);
            SqlToken operator = consumeOperator(state, errors);
            SqlToken right = consumeValue(state, "operando derecho en WHERE", errors);

            conditions.add(node(
                    "Condition",
                    valueOf(left),
                    Map.of("operator", valueOf(operator), "right", valueOf(right)),
                    List.of(tokenNode("LeftOperand", left), tokenNode("Operator", operator), tokenNode("RightOperand", right))
            ));

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
            return state.advance();
        }

        errors.add("Se esperaba " + expected + ".");
        return SqlToken.missing(expected);
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

    private static String valueOf(SqlToken token) {
        return token == null ? null : token.lexeme();
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
                "SELECT", "INSERT", "INTO", "UPDATE", "DELETE", "FROM", "WHERE", "SET", "VALUES", "AND", "OR"
        ).contains(lexeme);
    }

    private static boolean isSymbol(String lexeme) {
        return List.of(",", "(", ")", ";", "*").contains(lexeme);
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

        private SqlToken advance() {
            return tokens.get(position++);
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

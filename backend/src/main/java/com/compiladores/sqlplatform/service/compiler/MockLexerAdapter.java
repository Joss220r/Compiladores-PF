package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MockLexerAdapter implements LexerPort {

    private static final Set<String> GENERIC_SQL_KEYWORDS = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "TRUNCATE",
            "FROM", "WHERE", "SET", "VALUES", "JOIN", "INNER", "LEFT", "RIGHT", "FULL",
            "OUTER", "CROSS", "ON", "GROUP", "BY", "ORDER", "HAVING", "AS", "DISTINCT",
            "INTO", "TABLE", "VIEW", "INDEX", "DATABASE", "SCHEMA", "AND", "OR", "NOT",
            "NULL", "IS", "IN", "BETWEEN", "LIKE", "CASE", "WHEN", "THEN", "ELSE", "END",
            "LIMIT", "OFFSET", "UNION", "ALL", "EXISTS", "PRIMARY", "KEY", "FOREIGN",
            "REFERENCES", "DEFAULT", "CONSTRAINT", "UNIQUE", "CHECK"
    );

    private static final Map<DatabaseEngine, Set<String>> ENGINE_KEYWORDS = new EnumMap<>(DatabaseEngine.class);

    static {
        ENGINE_KEYWORDS.put(DatabaseEngine.MYSQL, Set.of(
                "SHOW", "USE", "DESCRIBE", "DESC", "EXPLAIN", "REPLACE", "AUTO_INCREMENT",
                "ENGINE", "CHARSET", "COLLATE", "ENUM", "UNSIGNED", "ZEROFILL", "IF",
                "LOCK", "UNLOCK", "START", "TRANSACTION"
        ));
        ENGINE_KEYWORDS.put(DatabaseEngine.POSTGRESQL, Set.of(
                "RETURNING", "SERIAL", "BIGSERIAL", "JSON", "JSONB", "ILIKE", "WITH",
                "RECURSIVE", "CONFLICT", "DO", "NOTHING", "ARRAY", "CAST", "WINDOW",
                "FILTER", "TRUE", "FALSE"
        ));
        ENGINE_KEYWORDS.put(DatabaseEngine.SQL_SERVER, Set.of(
                "TOP", "GO", "IDENTITY", "NVARCHAR", "VARCHAR", "BIT", "MERGE", "OUTPUT",
                "TRY_CONVERT", "TRY_CAST", "NOLOCK", "BEGIN", "TRAN", "COMMIT", "ROLLBACK"
        ));
        ENGINE_KEYWORDS.put(DatabaseEngine.MONGODB, Set.of(
                "DB", "FIND", "FINDONE", "AGGREGATE", "INSERTONE", "INSERTMANY", "UPDATEONE",
                "UPDATEMANY", "DELETEONE", "DELETEMANY", "CREATEINDEX", "DROPINDEX",
                "SORT", "LIMIT", "SKIP", "PROJECT", "MATCH", "GROUP", "LOOKUP", "UNWIND",
                "$GT", "$GTE", "$LT", "$LTE", "$NE", "$IN", "$NIN", "$AND", "$OR", "$NOT",
                "$SET", "$UNSET", "$PUSH", "$PULL", "$MATCH", "$GROUP", "$PROJECT", "$SORT",
                "$LOOKUP", "$UNWIND", "TRUE", "FALSE", "NULL"
        ));
        ENGINE_KEYWORDS.put(DatabaseEngine.REDIS, Set.of(
                "GET", "SET", "DEL", "EXISTS", "EXPIRE", "TTL", "HGET", "HSET", "HDEL",
                "LPUSH", "RPUSH", "LPOP", "RPOP", "SADD", "SREM", "SMEMBERS", "ZADD",
                "ZRANGE", "INCR", "DECR", "PING", "AUTH", "SELECT", "KEYS"
        ));
    }

    private static final Set<String> MULTI_CHARACTER_OPERATORS = Set.of(
            ">=", "<=", "<>", "!=", "==", "&&", "||", "::", ":=", "=>", "->", "->>",
            "#>", "#>>", "@>", "<@", "?|", "?&", "++", "--"
    );

    private static final Set<Character> SINGLE_CHARACTER_OPERATORS = Set.of(
            '=', '>', '<', '+', '-', '*', '/', '%', '!', '~', '^', '|', '&', '?'
    );

    private static final Set<Character> SYMBOLS = Set.of(
            '(', ')', '[', ']', '{', '}', ',', ';', '.', ':'
    );

    @Override
    public List<TokenInfo> tokenize(String query, DatabaseEngine engine) {
        LexerState state = new LexerState(query == null ? "" : query, engine);
        return state.scan();
    }

    private static String classifyWord(String lexeme, DatabaseEngine engine) {
        String upper = lexeme.toUpperCase();
        Set<String> engineKeywords = engine == null ? Set.of() : ENGINE_KEYWORDS.getOrDefault(engine, Set.of());
        if (GENERIC_SQL_KEYWORDS.contains(upper) || engineKeywords.contains(upper)) {
            return "KEYWORD";
        }
        return "IDENTIFIER";
    }

    private static final class LexerState {

        private final String query;
        private final DatabaseEngine engine;
        private final List<TokenInfo> tokens = new ArrayList<>();
        private int index;
        private int line = 1;
        private int column = 1;

        private LexerState(String query, DatabaseEngine engine) {
            this.query = query;
            this.engine = engine;
        }

        private List<TokenInfo> scan() {
            while (!isAtEnd()) {
                char current = peek();

                if (Character.isWhitespace(current)) {
                    advance();
                } else if (startsWith("--") || isMysqlHashComment()) {
                    scanLineComment();
                } else if (startsWith("/*")) {
                    scanBlockComment();
                } else if (current == '\'' || current == '"') {
                    scanQuotedText(current, "STRING");
                } else if (current == '`') {
                    scanQuotedText(current, "IDENTIFIER");
                } else if (current == '[') {
                    scanBracketIdentifier();
                } else if (current == '$' && tryScanDollarQuotedString()) {
                    continue;
                } else if (Character.isDigit(current)) {
                    scanNumber();
                } else if (isIdentifierStart(current)) {
                    scanIdentifierOrKeyword();
                } else if (tryScanOperator()) {
                    continue;
                } else if (SYMBOLS.contains(current)) {
                    addToken("SYMBOL", String.valueOf(current), line, column);
                    advance();
                } else {
                    addToken("UNKNOWN", String.valueOf(current), line, column);
                    advance();
                }
            }

            return tokens;
        }

        private void scanLineComment() {
            int startLine = line;
            int startColumn = column;
            int start = index;

            while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
                advance();
            }

            addToken("COMMENT", query.substring(start, index), startLine, startColumn);
        }

        private void scanBlockComment() {
            int startLine = line;
            int startColumn = column;
            int start = index;

            advance();
            advance();

            while (!isAtEnd() && !startsWith("*/")) {
                advance();
            }

            if (!isAtEnd()) {
                advance();
                advance();
            }

            addToken("COMMENT", query.substring(start, index), startLine, startColumn);
        }

        private void scanQuotedText(char quote, String type) {
            int startLine = line;
            int startColumn = column;
            int start = index;

            advance();
            while (!isAtEnd()) {
                char current = advance();

                if (current == quote) {
                    if (!isAtEnd() && peek() == quote) {
                        advance();
                        continue;
                    }
                    break;
                }

                if (current == '\\' && !isAtEnd()) {
                    advance();
                }
            }

            addToken(type, query.substring(start, index), startLine, startColumn);
        }

        private void scanBracketIdentifier() {
            int startLine = line;
            int startColumn = column;
            int start = index;

            advance();
            while (!isAtEnd() && peek() != ']') {
                advance();
            }
            if (!isAtEnd()) {
                advance();
            }

            addToken("IDENTIFIER", query.substring(start, index), startLine, startColumn);
        }

        private boolean tryScanDollarQuotedString() {
            int delimiterEnd = index + 1;
            while (delimiterEnd < query.length() && isIdentifierPart(query.charAt(delimiterEnd))) {
                delimiterEnd++;
            }

            if (delimiterEnd >= query.length() || query.charAt(delimiterEnd) != '$') {
                return false;
            }

            String delimiter = query.substring(index, delimiterEnd + 1);
            int closingIndex = query.indexOf(delimiter, delimiterEnd + 1);
            int startLine = line;
            int startColumn = column;
            int start = index;
            int end = closingIndex >= 0 ? closingIndex + delimiter.length() : query.length();

            while (index < end) {
                advance();
            }

            addToken("STRING", query.substring(start, index), startLine, startColumn);
            return true;
        }

        private void scanNumber() {
            int startLine = line;
            int startColumn = column;
            int start = index;

            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }

            if (!isAtEnd() && peek() == '.' && hasNextDigit()) {
                advance();
                while (!isAtEnd() && Character.isDigit(peek())) {
                    advance();
                }
            }

            if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
                int exponentStart = index;
                advance();
                if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                    advance();
                }
                if (!isAtEnd() && Character.isDigit(peek())) {
                    while (!isAtEnd() && Character.isDigit(peek())) {
                        advance();
                    }
                } else {
                    int consumedExponentCharacters = index - exponentStart;
                    index = exponentStart;
                    column -= consumedExponentCharacters;
                }
            }

            addToken("NUMBER", query.substring(start, index), startLine, startColumn);
        }

        private void scanIdentifierOrKeyword() {
            int startLine = line;
            int startColumn = column;
            int start = index;

            while (!isAtEnd() && isIdentifierPart(peek())) {
                advance();
            }

            String lexeme = query.substring(start, index);
            addToken(classifyWord(lexeme, engine), lexeme, startLine, startColumn);
        }

        private boolean tryScanOperator() {
            int startLine = line;
            int startColumn = column;

            if (index + 3 <= query.length()) {
                String threeCharacters = query.substring(index, index + 3);
                if (MULTI_CHARACTER_OPERATORS.contains(threeCharacters)) {
                    addToken("OPERATOR", threeCharacters, startLine, startColumn);
                    advance();
                    advance();
                    advance();
                    return true;
                }
            }

            if (index + 2 <= query.length()) {
                String twoCharacters = query.substring(index, index + 2);
                if (MULTI_CHARACTER_OPERATORS.contains(twoCharacters)) {
                    addToken("OPERATOR", twoCharacters, startLine, startColumn);
                    advance();
                    advance();
                    return true;
                }
            }

            if (SINGLE_CHARACTER_OPERATORS.contains(peek())) {
                addToken("OPERATOR", String.valueOf(peek()), startLine, startColumn);
                advance();
                return true;
            }

            return false;
        }

        private void addToken(String type, String lexeme, int tokenLine, int tokenColumn) {
            tokens.add(new TokenInfo(type, lexeme, tokenLine, tokenColumn));
        }

        private boolean startsWith(String value) {
            return query.startsWith(value, index);
        }

        private boolean isMysqlHashComment() {
            return engine == DatabaseEngine.MYSQL && startsWith("#");
        }

        private boolean hasNextDigit() {
            return index + 1 < query.length() && Character.isDigit(query.charAt(index + 1));
        }

        private boolean isAtEnd() {
            return index >= query.length();
        }

        private char peek() {
            return query.charAt(index);
        }

        private char advance() {
            char current = query.charAt(index++);
            if (current == '\r') {
                if (!isAtEnd() && peek() == '\n') {
                    index++;
                }
                line++;
                column = 1;
            } else if (current == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            return current;
        }

        private boolean isIdentifierStart(char character) {
            return Character.isLetter(character) || character == '_' || character == '@' || character == '$';
        }

        private boolean isIdentifierPart(char character) {
            return Character.isLetterOrDigit(character)
                    || character == '_'
                    || character == '$'
                    || character == '@';
        }
    }
}

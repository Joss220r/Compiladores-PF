package com.compiladores.sqlplatform.service.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class BasicLexerAdapterTest {

    private final LexerPort lexer = new BasicLexerAdapter();

    @Test
    void tokenizesGenericSqlWithOperatorsSymbolsAndPositions() {
        List<TokenInfo> tokens = lexer.tokenize("SELECT * FROM usuarios\nWHERE edad >= 18;", DatabaseEngine.SQL);

        assertThat(tokens).extracting(TokenInfo::getType)
                .containsExactly(
                        "KEYWORD", "OPERATOR", "KEYWORD", "IDENTIFIER",
                        "KEYWORD", "IDENTIFIER", "OPERATOR", "NUMBER", "SYMBOL"
                );
        assertThat(tokens).extracting(TokenInfo::getLexeme)
                .containsExactly("SELECT", "*", "FROM", "usuarios", "WHERE", "edad", ">=", "18", ";");
        assertThat(tokens.get(4).getLine()).isEqualTo(2);
        assertThat(tokens.get(4).getColumn()).isEqualTo(1);
        assertThat(tokens.get(7).getColumn()).isEqualTo(15);
    }

    @Test
    void tokenizesStringsAndComments() {
        List<TokenInfo> tokens = lexer.tokenize("SELECT 'O''Reilly' -- autor\nFROM libros", DatabaseEngine.MYSQL);

        assertThat(tokens).extracting(TokenInfo::getType)
                .containsExactly("KEYWORD", "STRING", "COMMENT", "KEYWORD", "IDENTIFIER");
        assertThat(tokens.get(1).getLexeme()).isEqualTo("'O''Reilly'");
        assertThat(tokens.get(2).getLexeme()).isEqualTo("-- autor");
        assertThat(tokens.get(3).getLine()).isEqualTo(2);
    }

    @Test
    void tokenizesMongoOperatorsAsEngineKeywords() {
        List<TokenInfo> tokens = lexer.tokenize("db.users.find({ age: { $gt: 18 } })", DatabaseEngine.MONGODB);

        assertThat(tokens).extracting(TokenInfo::getLexeme)
                .contains("db", "users", "find", "$gt", "18");
        assertThat(tokens.stream()
                .filter(token -> token.getLexeme().equals("$gt"))
                .findFirst())
                .get()
                .extracting(TokenInfo::getType)
                .isEqualTo("KEYWORD");
    }

    @Test
    void tokenizesRedisCommandsAsKeywords() {
        List<TokenInfo> tokens = lexer.tokenize("HSET user:1 name Andre", DatabaseEngine.REDIS);

        assertThat(tokens).extracting(TokenInfo::getType)
                .containsExactly("KEYWORD", "IDENTIFIER", "SYMBOL", "NUMBER", "IDENTIFIER", "IDENTIFIER");
    }
}

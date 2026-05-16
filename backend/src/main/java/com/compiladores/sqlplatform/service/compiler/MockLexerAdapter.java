package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockLexerAdapter implements LexerPort {

    @Override
    public List<TokenInfo> tokenize(String query, DatabaseEngine engine) {
        List<TokenInfo> tokens = new ArrayList<>();
        String[] lexemes = query.split("\\s+");

        for (int index = 0; index < lexemes.length; index++) {
            String lexeme = lexemes[index];
            if (!lexeme.isBlank()) {
                tokens.add(new TokenInfo(classify(lexeme, engine), lexeme, 1, index + 1));
            }
        }

        return tokens;
    }

    private String classify(String lexeme, DatabaseEngine engine) {
        String upper = lexeme.toUpperCase();

        if (upper.matches("SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|FROM|WHERE|SET|VALUES")) {
            return "KEYWORD";
        }

        if (engine == DatabaseEngine.MONGODB && lexeme.startsWith("db.")) {
            return "MONGO_COLLECTION";
        }

        if (engine == DatabaseEngine.REDIS && upper.matches("GET|SET|DEL|HGET|HSET|LPUSH|RPUSH")) {
            return "REDIS_COMMAND";
        }

        if (lexeme.matches("[0-9]+")) {
            return "NUMBER";
        }

        return "IDENTIFIER";
    }
}

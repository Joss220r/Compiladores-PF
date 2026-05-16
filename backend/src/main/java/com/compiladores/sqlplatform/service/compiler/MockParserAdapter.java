package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MockParserAdapter implements ParserPort {

    @Override
    public AstNode parse(List<TokenInfo> tokens, String query, DatabaseEngine engine) {
        List<AstNode> children = tokens.stream()
                .map(token -> new AstNode(
                        "TokenNode",
                        token.getLexeme(),
                        Map.of("tokenType", token.getType()),
                        List.of()
                ))
                .toList();

        return new AstNode(
                "MockQuery",
                query,
                Map.of(
                        "engine", engine.name(),
                        "parserStatus", "PENDING_REAL_PARSER"
                ),
                children
        );
    }
}

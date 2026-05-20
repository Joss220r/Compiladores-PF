package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.SemanticResult;
import java.util.List;
import java.util.Map;

@Deprecated
public class MockSemanticAnalyzerAdapter implements SemanticAnalyzerPort {

    @Override
    public SemanticResult analyze(AstNode ast, DatabaseEngine engine) {
        return new SemanticResult(
                true,
                List.of("Resultado semantico simulado hasta integrar el modulo real."),
                Map.of(
                        "engine", engine.name(),
                        "rootNode", ast.getType(),
                        "semanticStatus", "PENDING_REAL_SEMANTIC_ANALYZER"
                )
        );
    }
}

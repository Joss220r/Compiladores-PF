package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.SemanticResult;
import com.compiladores.sqlplatform.model.ValidationIssue;
import java.util.List;

public interface SemanticAnalyzerPort {

    SemanticResult analyze(AstNode ast, DatabaseEngine engine);

    default List<ValidationIssue> getIssues() {
        return List.of();
    }
}

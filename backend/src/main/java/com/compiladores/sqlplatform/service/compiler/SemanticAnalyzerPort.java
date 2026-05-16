package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.SemanticResult;

public interface SemanticAnalyzerPort {

    SemanticResult analyze(AstNode ast, DatabaseEngine engine);
}

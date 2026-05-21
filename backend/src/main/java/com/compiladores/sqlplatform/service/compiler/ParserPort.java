package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import com.compiladores.sqlplatform.model.ValidationIssue;
import java.util.List;

public interface ParserPort {

    AstNode parse(List<TokenInfo> tokens, String query, DatabaseEngine engine);

    default List<ValidationIssue> getIssues() {
        return List.of();
    }
}

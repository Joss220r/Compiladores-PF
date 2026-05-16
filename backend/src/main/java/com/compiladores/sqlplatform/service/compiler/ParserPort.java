package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.AstNode;
import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import java.util.List;

public interface ParserPort {

    AstNode parse(List<TokenInfo> tokens, String query, DatabaseEngine engine);
}

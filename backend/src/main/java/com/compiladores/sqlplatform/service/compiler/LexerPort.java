package com.compiladores.sqlplatform.service.compiler;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.TokenInfo;
import java.util.List;

public interface LexerPort {

    List<TokenInfo> tokenize(String query, DatabaseEngine engine);
}

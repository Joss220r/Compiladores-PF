package com.compiladores.sqlplatform.model;

public class TokenInfo {

    private String type;
    private String lexeme;
    private int line;
    private int column;

    public TokenInfo(String type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    public String getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public String getValue() {
        return lexeme;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}

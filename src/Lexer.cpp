/**
 * Lexer.cpp - Implementación del Analizador Léxico
 * Curso de Compiladores 2026 - UMG
 */

#include "../include/Lexer.h"
#include <algorithm>

Lexer::Lexer(const string& src) : source(src), position(0), line(1), column(1) {
    currentChar = source.empty() ? '\0' : source[0];
}

void Lexer::advance() {
    if (currentChar == '\n') {
        line++;
        column = 1;
    } else {
        column++;
    }
    
    position++;
    if (position >= source.length()) {
        currentChar = '\0';
    } else {
        currentChar = source[position];
    }
}

char Lexer::peek() {
    size_t nextPos = position + 1;
    if (nextPos >= source.length()) {
        return '\0';
    }
    return source[nextPos];
}

void Lexer::skipWhitespace() {
    while (currentChar != '\0' && (isspace(currentChar) || currentChar == '\n' || currentChar == '\r')) {
        advance();
    }
    
    // Saltar comentarios SQL (-- hasta fin de línea)
    if (currentChar == '-' && peek() == '-') {
        while (currentChar != '\0' && currentChar != '\n') {
            advance();
        }
        if (currentChar == '\n') {
            advance();
        }
        skipWhitespace(); // Recursivo para saltar más whitespace después del comentario
    }
}

bool Lexer::isKeyword(const string& str) {
    string upper = str;
    transform(upper.begin(), upper.end(), upper.begin(), ::toupper);
    return upper == "SELECT" || upper == "FROM" || upper == "WHERE";
}

TokenType Lexer::keywordToTokenType(const string& str) {
    string upper = str;
    transform(upper.begin(), upper.end(), upper.begin(), ::toupper);
    
    if (upper == "SELECT") return TokenType::SELECT;
    if (upper == "FROM") return TokenType::FROM;
    if (upper == "WHERE") return TokenType::WHERE;
    
    return TokenType::IDENTIFIER;
}

Token Lexer::readIdentifierOrKeyword() {
    int startLine = line;
    int startCol = column;
    string value = "";
    
    while (currentChar != '\0' && (isalnum(currentChar) || currentChar == '_')) {
        value += currentChar;
        advance();
    }
    
    if (isKeyword(value)) {
        return Token(keywordToTokenType(value), value, startLine, startCol);
    }
    
    return Token(TokenType::IDENTIFIER, value, startLine, startCol);
}

Token Lexer::readNumber() {
    int startLine = line;
    int startCol = column;
    string value = "";
    
    while (currentChar != '\0' && isdigit(currentChar)) {
        value += currentChar;
        advance();
    }
    
    return Token(TokenType::NUMBER, value, startLine, startCol);
}

Token Lexer::readString() {
    int startLine = line;
    int startCol = column;
    string value = "";
    
    // Saltar la comilla inicial
    advance();
    
    while (currentChar != '\0' && currentChar != '\'') {
        value += currentChar;
        advance();
    }
    
    if (currentChar == '\'') {
        advance(); // Saltar la comilla final
    } else {
        // Error: string sin cerrar
        return Token(TokenType::INVALID, value, startLine, startCol);
    }
    
    return Token(TokenType::STRING, value, startLine, startCol);
}

Token Lexer::getNextToken() {
    skipWhitespace();
    
    if (currentChar == '\0') {
        return Token(TokenType::END_OF_FILE, "", line, column);
    }
    
    int startLine = line;
    int startCol = column;
    
    // Identificadores y palabras clave
    if (isalpha(currentChar) || currentChar == '_') {
        return readIdentifierOrKeyword();
    }
    
    // Números
    if (isdigit(currentChar)) {
        return readNumber();
    }
    
    // Strings (comillas simples)
    if (currentChar == '\'') {
        return readString();
    }
    
    // Operadores de dos caracteres
    if (currentChar == '>') {
        advance();
        if (currentChar == '=') {
            advance();
            return Token(TokenType::GREATER_EQUAL, ">=", startLine, startCol);
        }
        return Token(TokenType::GREATER, ">", startLine, startCol);
    }
    
    if (currentChar == '<') {
        advance();
        if (currentChar == '=') {
            advance();
            return Token(TokenType::LESS_EQUAL, "<=", startLine, startCol);
        }
        return Token(TokenType::LESS, "<", startLine, startCol);
    }
    
    if (currentChar == '!') {
        advance();
        if (currentChar == '=') {
            advance();
            return Token(TokenType::NOT_EQUAL, "!=", startLine, startCol);
        }
        // ! solo no es válido en SQL
        return Token(TokenType::INVALID, "!", startLine, startCol);
    }
    
    // Operadores y símbolos de un carácter
    char ch = currentChar;
    advance();
    
    switch(ch) {
        case '=':
            return Token(TokenType::EQUAL, "=", startLine, startCol);
        case '*':
            return Token(TokenType::ASTERISK, "*", startLine, startCol);
        case ',':
            return Token(TokenType::COMMA, ",", startLine, startCol);
        case ';':
            return Token(TokenType::SEMICOLON, ";", startLine, startCol);
        default:
            return Token(TokenType::INVALID, string(1, ch), startLine, startCol);
    }
}

vector<Token> Lexer::tokenize() {
    vector<Token> tokens;
    Token token;
    
    do {
        token = getNextToken();
        tokens.push_back(token);
    } while (token.type != TokenType::END_OF_FILE);
    
    return tokens;
}

/**
 * Token.h - Definición de tokens para el compilador SQL
 * Curso de Compiladores 2026 - UMG
 */

#ifndef TOKEN_H
#define TOKEN_H

#include <string>
#include <iostream>

using namespace std;

/**
 * Tipos de tokens que reconoce el lexer
 */
enum class TokenType {
    // Palabras clave
    SELECT,
    FROM,
    WHERE,
    
    // Identificadores y literales
    IDENTIFIER,
    NUMBER,
    STRING,
    
    // Operadores de comparación
    EQUAL,          // =
    GREATER,        // >
    LESS,           // <
    GREATER_EQUAL,  // >=
    LESS_EQUAL,     // <=
    NOT_EQUAL,      // !=
    
    // Símbolos especiales
    ASTERISK,       // *
    COMMA,          // ,
    SEMICOLON,      // ;
    
    // Fin de archivo
    END_OF_FILE,
    
    // Token inválido
    INVALID
};

/**
 * Estructura de un token
 */
struct Token {
    TokenType type;
    string value;
    int line;
    int column;
    
    Token() : type(TokenType::INVALID), value(""), line(0), column(0) {}
    
    Token(TokenType t, string v, int l, int c) 
        : type(t), value(v), line(l), column(c) {}
    
    /**
     * Convierte el tipo de token a string (para debugging)
     */
    string typeToString() const {
        switch(type) {
            case TokenType::SELECT:        return "SELECT";
            case TokenType::FROM:          return "FROM";
            case TokenType::WHERE:         return "WHERE";
            case TokenType::IDENTIFIER:    return "IDENTIFIER";
            case TokenType::NUMBER:        return "NUMBER";
            case TokenType::STRING:        return "STRING";
            case TokenType::EQUAL:         return "EQUAL";
            case TokenType::GREATER:       return "GREATER";
            case TokenType::LESS:          return "LESS";
            case TokenType::GREATER_EQUAL: return "GREATER_EQUAL";
            case TokenType::LESS_EQUAL:    return "LESS_EQUAL";
            case TokenType::NOT_EQUAL:     return "NOT_EQUAL";
            case TokenType::ASTERISK:      return "ASTERISK";
            case TokenType::COMMA:         return "COMMA";
            case TokenType::SEMICOLON:     return "SEMICOLON";
            case TokenType::END_OF_FILE:   return "END_OF_FILE";
            case TokenType::INVALID:       return "INVALID";
            default:                       return "UNKNOWN";
        }
    }
    
    /**
     * Imprime el token (para debugging)
     */
    void print() const {
        cout << "[" << typeToString() << "] ";
        if (!value.empty()) {
            cout << "'" << value << "' ";
        }
        cout << "(L" << line << ":C" << column << ")";
    }
};

#endif // TOKEN_H

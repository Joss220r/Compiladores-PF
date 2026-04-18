/**
 * Lexer.h - Analizador Léxico para el compilador SQL
 * Curso de Compiladores 2026 - UMG
 */

#ifndef LEXER_H
#define LEXER_H

#include "Token.h"
#include <string>
#include <vector>
#include <cctype>

using namespace std;

/**
 * Clase Lexer - Convierte el código fuente en tokens
 */
class Lexer {
private:
    string source;          // Código fuente
    size_t position;        // Posición actual en el source
    int line;               // Línea actual
    int column;             // Columna actual
    char currentChar;       // Carácter actual
    
    /**
     * Avanza al siguiente carácter
     */
    void advance();
    
    /**
     * Mira el siguiente carácter sin consumirlo
     */
    char peek();
    
    /**
     * Salta espacios en blanco y comentarios
     */
    void skipWhitespace();
    
    /**
     * Lee un identificador o palabra clave
     */
    Token readIdentifierOrKeyword();
    
    /**
     * Lee un número
     */
    Token readNumber();
    
    /**
     * Lee un string (entre comillas simples)
     */
    Token readString();
    
    /**
     * Determina si un string es palabra clave
     */
    bool isKeyword(const string& str);
    
    /**
     * Convierte palabra clave a tipo de token
     */
    TokenType keywordToTokenType(const string& str);
    
public:
    /**
     * Constructor
     */
    Lexer(const string& src);
    
    /**
     * Obtiene el siguiente token
     */
    Token getNextToken();
    
    /**
     * Tokeniza todo el source y retorna lista de tokens
     */
    vector<Token> tokenize();
};

#endif // LEXER_H

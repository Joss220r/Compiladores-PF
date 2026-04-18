/**
 * Parser.h - Analizador Sintáctico para SQL
 * Curso de Compiladores 2026 - UMG
 */

#ifndef PARSER_H
#define PARSER_H

#include "Token.h"
#include "AST.h"
#include <vector>
#include <memory>
#include <stdexcept>

using namespace std;

/**
 * Parser Descendente Recursivo
 */
class Parser {
private:
    vector<Token> tokens;
    size_t position;
    Token currentToken;
    
    /**
     * Avanza al siguiente token
     */
    void advance();
    
    /**
     * Verifica que el token actual sea del tipo esperado
     */
    void expect(TokenType type);
    
    /**
     * Verifica si el token actual es del tipo dado
     */
    bool check(TokenType type);
    
    /**
     * Reporta un error de sintaxis
     */
    void error(const string& message);
    
    /**
     * Parsea la query principal: SELECT ... FROM ... WHERE?
     */
    unique_ptr<SelectNode> parseQuery();
    
    /**
     * Parsea las columnas: * o lista de identificadores
     */
    void parseColumns(SelectNode* selectNode);
    
    /**
     * Parsea la cláusula WHERE
     */
    unique_ptr<ConditionNode> parseWhere();
    
    /**
     * Parsea una condición: expr op expr
     */
    unique_ptr<ConditionNode> parseCondition();
    
    /**
     * Parsea una expresión: identificador, número o string
     */
    unique_ptr<ExpressionNode> parseExpression();
    
    /**
     * Convierte TokenType de operador a CompOperator
     */
    CompOperator tokenTypeToCompOperator(TokenType type);
    
public:
    /**
     * Constructor
     */
    Parser(const vector<Token>& toks);
    
    /**
     * Parsea los tokens y retorna el AST
     */
    unique_ptr<SelectNode> parse();
};

#endif // PARSER_H

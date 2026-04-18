/**
 * Parser.cpp - Implementación del Analizador Sintáctico
 * Curso de Compiladores 2026 - UMG
 */

#include "../include/Parser.h"

Parser::Parser(const vector<Token>& toks) : tokens(toks), position(0) {
    if (!tokens.empty()) {
        currentToken = tokens[0];
    }
}

void Parser::advance() {
    position++;
    if (position < tokens.size()) {
        currentToken = tokens[position];
    }
}

bool Parser::check(TokenType type) {
    return currentToken.type == type;
}

void Parser::expect(TokenType type) {
    if (!check(type)) {
        error("Se esperaba " + Token(type, "", 0, 0).typeToString() + 
              " pero se encontró " + currentToken.typeToString());
    }
    advance();
}

void Parser::error(const string& message) {
    throw runtime_error("Error de sintaxis en línea " + to_string(currentToken.line) +
                       ", columna " + to_string(currentToken.column) + ": " + message);
}

CompOperator Parser::tokenTypeToCompOperator(TokenType type) {
    switch(type) {
        case TokenType::EQUAL:         return CompOperator::EQUAL;
        case TokenType::GREATER:       return CompOperator::GREATER;
        case TokenType::LESS:          return CompOperator::LESS;
        case TokenType::GREATER_EQUAL: return CompOperator::GREATER_EQUAL;
        case TokenType::LESS_EQUAL:    return CompOperator::LESS_EQUAL;
        case TokenType::NOT_EQUAL:     return CompOperator::NOT_EQUAL;
        default:
            error("Operador de comparación inválido");
            return CompOperator::EQUAL; // No debería llegar aquí
    }
}

unique_ptr<ExpressionNode> Parser::parseExpression() {
    if (check(TokenType::IDENTIFIER)) {
        string value = currentToken.value;
        advance();
        return make_unique<ExpressionNode>(ExpressionNode::ExprType::IDENTIFIER, value);
    }
    else if (check(TokenType::NUMBER)) {
        string value = currentToken.value;
        advance();
        return make_unique<ExpressionNode>(ExpressionNode::ExprType::NUMBER, value);
    }
    else if (check(TokenType::STRING)) {
        string value = currentToken.value;
        advance();
        return make_unique<ExpressionNode>(ExpressionNode::ExprType::STRING, value);
    }
    else {
        error("Se esperaba una expresión (identificador, número o string)");
        return nullptr; // No debería llegar aquí
    }
}

unique_ptr<ConditionNode> Parser::parseCondition() {
    // expr op expr
    auto left = parseExpression();
    
    // Verificar que sea un operador de comparación
    if (!check(TokenType::EQUAL) && !check(TokenType::GREATER) && 
        !check(TokenType::LESS) && !check(TokenType::GREATER_EQUAL) &&
        !check(TokenType::LESS_EQUAL) && !check(TokenType::NOT_EQUAL)) {
        error("Se esperaba un operador de comparación (=, >, <, >=, <=, !=)");
    }
    
    CompOperator op = tokenTypeToCompOperator(currentToken.type);
    advance();
    
    auto right = parseExpression();
    
    return make_unique<ConditionNode>(std::move(left), op, std::move(right));
}

unique_ptr<ConditionNode> Parser::parseWhere() {
    expect(TokenType::WHERE);
    return parseCondition();
}

void Parser::parseColumns(SelectNode* selectNode) {
    if (check(TokenType::ASTERISK)) {
        selectNode->selectAll = true;
        advance();
    }
    else if (check(TokenType::IDENTIFIER)) {
        // Lista de columnas separadas por comas
        selectNode->columns.push_back(currentToken.value);
        advance();
        
        while (check(TokenType::COMMA)) {
            advance(); // Consumir la coma
            expect(TokenType::IDENTIFIER);
            selectNode->columns.push_back(tokens[position - 1].value);
        }
    }
    else {
        error("Se esperaba '*' o una lista de columnas");
    }
}

unique_ptr<SelectNode> Parser::parseQuery() {
    auto selectNode = make_unique<SelectNode>();
    
    // SELECT
    expect(TokenType::SELECT);
    
    // Columnas
    parseColumns(selectNode.get());
    
    // FROM
    expect(TokenType::FROM);
    
    // Tabla
    expect(TokenType::IDENTIFIER);
    selectNode->tableName = tokens[position - 1].value;
    
    // WHERE (opcional)
    if (check(TokenType::WHERE)) {
        selectNode->whereCondition = parseWhere();
    }
    
    // Punto y coma opcional
    if (check(TokenType::SEMICOLON)) {
        advance();
    }
    
    // Debe ser fin de archivo
    if (!check(TokenType::END_OF_FILE)) {
        error("Se esperaba fin de archivo después de la consulta");
    }
    
    return selectNode;
}

unique_ptr<SelectNode> Parser::parse() {
    return parseQuery();
}

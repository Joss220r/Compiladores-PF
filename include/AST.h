/**
 * AST.h - Árbol de Sintaxis Abstracta para SQL
 * Curso de Compiladores 2026 - UMG
 */

#ifndef AST_H
#define AST_H

#include <string>
#include <vector>
#include <memory>
#include <iostream>

using namespace std;

/**
 * Nodo base del AST
 */
class ASTNode {
public:
    virtual ~ASTNode() = default;
    virtual void print(int indent = 0) const = 0;
};

/**
 * Nodo de expresión (literal o identificador)
 */
class ExpressionNode : public ASTNode {
public:
    enum class ExprType {
        IDENTIFIER,
        NUMBER,
        STRING
    };
    
    ExprType type;
    string value;
    
    ExpressionNode(ExprType t, string v) : type(t), value(v) {}
    
    void print(int indent = 0) const override {
        string spaces(indent, ' ');
        cout << spaces;
        if (type == ExprType::IDENTIFIER) {
            cout << "Identifier: " << value;
        } else if (type == ExprType::NUMBER) {
            cout << "Number: " << value;
        } else {
            cout << "String: '" << value << "'";
        }
        cout << endl;
    }
};

/**
 * Operadores de comparación
 */
enum class CompOperator {
    EQUAL,          // =
    GREATER,        // >
    LESS,           // <
    GREATER_EQUAL,  // >=
    LESS_EQUAL,     // <=
    NOT_EQUAL       // !=
};

/**
 * Convierte operador a string
 */
inline string compOperatorToString(CompOperator op) {
    switch(op) {
        case CompOperator::EQUAL:         return "=";
        case CompOperator::GREATER:       return ">";
        case CompOperator::LESS:          return "<";
        case CompOperator::GREATER_EQUAL: return ">=";
        case CompOperator::LESS_EQUAL:    return "<=";
        case CompOperator::NOT_EQUAL:     return "!=";
        default:                          return "?";
    }
}

/**
 * Nodo de condición (WHERE)
 */
class ConditionNode : public ASTNode {
public:
    unique_ptr<ExpressionNode> left;
    CompOperator op;
    unique_ptr<ExpressionNode> right;
    
    ConditionNode(unique_ptr<ExpressionNode> l, CompOperator o, unique_ptr<ExpressionNode> r)
        : left(std::move(l)), op(o), right(std::move(r)) {}
    
    void print(int indent = 0) const override {
        string spaces(indent, ' ');
        cout << spaces << "Condition:" << endl;
        cout << spaces << "  Left:" << endl;
        left->print(indent + 4);
        cout << spaces << "  Operator: " << compOperatorToString(op) << endl;
        cout << spaces << "  Right:" << endl;
        right->print(indent + 4);
    }
};

/**
 * Nodo de SELECT (query completa)
 */
class SelectNode : public ASTNode {
public:
    vector<string> columns;     // Lista de columnas (o "*")
    bool selectAll;             // true si es SELECT *
    string tableName;           // Nombre de la tabla
    unique_ptr<ConditionNode> whereCondition; // Condición WHERE (puede ser nullptr)
    
    SelectNode() : selectAll(false), whereCondition(nullptr) {}
    
    void print(int indent = 0) const override {
        string spaces(indent, ' ');
        cout << spaces << "SELECT Query:" << endl;
        
        cout << spaces << "  Columns: ";
        if (selectAll) {
            cout << "*";
        } else {
            for (size_t i = 0; i < columns.size(); i++) {
                cout << columns[i];
                if (i < columns.size() - 1) cout << ", ";
            }
        }
        cout << endl;
        
        cout << spaces << "  FROM: " << tableName << endl;
        
        if (whereCondition) {
            cout << spaces << "  WHERE:" << endl;
            whereCondition->print(indent + 4);
        }
    }
};

#endif // AST_H

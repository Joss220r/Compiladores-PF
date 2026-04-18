/**
 * SemanticAnalyzer.cpp - Implementación del Analizador Semántico
 * Curso de Compiladores 2026 - UMG
 */

#include "../include/SemanticAnalyzer.h"
#include <algorithm>

const Table* SemanticAnalyzer::validateTable(const string& tableName) {
    const Table* table = symbolTable.findTable(tableName);
    if (!table) {
        errors.push_back("Tabla '" + tableName + "' no existe en el schema");
    }
    return table;
}

void SemanticAnalyzer::validateColumns(const SelectNode* selectNode, const Table* table) {
    if (!table) return;
    
    if (selectNode->selectAll) {
        // SELECT * es siempre válido
        return;
    }
    
    // Validar cada columna
    for (const string& colName : selectNode->columns) {
        const Column* col = table->findColumn(colName);
        if (!col) {
            errors.push_back("Columna '" + colName + "' no existe en la tabla '" + table->name + "'");
        }
    }
}

DataType SemanticAnalyzer::getExpressionType(const ExpressionNode* expr, const Table* table) {
    if (expr->type == ExpressionNode::ExprType::NUMBER) {
        // Asumimos INT por simplicidad (podríamos detectar FLOAT si tiene '.')
        return DataType::INT;
    }
    else if (expr->type == ExpressionNode::ExprType::STRING) {
        return DataType::VARCHAR;
    }
    else if (expr->type == ExpressionNode::ExprType::IDENTIFIER) {
        // Buscar la columna en la tabla
        if (!table) {
            return DataType::VARCHAR; // Valor por defecto
        }
        
        const Column* col = table->findColumn(expr->value);
        if (!col) {
            errors.push_back("Columna '" + expr->value + "' no existe en la tabla '" + table->name + "'");
            return DataType::VARCHAR; // Valor por defecto
        }
        
        return col->type;
    }
    
    return DataType::VARCHAR; // Por defecto
}

bool SemanticAnalyzer::areTypesCompatible(DataType left, DataType right, CompOperator op) {
    // Reglas de compatibilidad:
    // 1. INT puede compararse con INT
    // 2. VARCHAR puede compararse con VARCHAR
    // 3. FLOAT puede compararse con FLOAT o INT
    // 4. No se puede comparar INT con VARCHAR
    
    if (left == right) {
        return true; // Tipos idénticos siempre son compatibles
    }
    
    // FLOAT es compatible con INT
    if ((left == DataType::FLOAT && right == DataType::INT) ||
        (left == DataType::INT && right == DataType::FLOAT)) {
        return true;
    }
    
    return false;
}

void SemanticAnalyzer::validateCondition(const ConditionNode* condition, const Table* table) {
    if (!condition || !table) return;
    
    // Obtener tipos de ambos lados
    DataType leftType = getExpressionType(condition->left.get(), table);
    DataType rightType = getExpressionType(condition->right.get(), table);
    
    // Validar compatibilidad
    if (!areTypesCompatible(leftType, rightType, condition->op)) {
        errors.push_back("Tipos incompatibles en comparación: " +
                        dataTypeToString(leftType) + " " +
                        compOperatorToString(condition->op) + " " +
                        dataTypeToString(rightType));
    }
}

bool SemanticAnalyzer::analyze(const SelectNode* ast) {
    if (!ast) {
        errors.push_back("AST vacío");
        return false;
    }
    
    errors.clear();
    warnings.clear();
    
    // 1. Validar que la tabla existe
    const Table* table = validateTable(ast->tableName);
    
    // 2. Validar las columnas
    validateColumns(ast, table);
    
    // 3. Validar la condición WHERE (si existe)
    if (ast->whereCondition) {
        validateCondition(ast->whereCondition.get(), table);
    }
    
    // Retornar true si no hay errores
    return errors.empty();
}

void SemanticAnalyzer::printDiagnostics() const {
    if (!errors.empty()) {
        cout << endl;
        cout << "❌ ERRORES SEMÁNTICOS:" << endl;
        for (const string& err : errors) {
            cout << "  - " << err << endl;
        }
    }
    
    if (!warnings.empty()) {
        cout << endl;
        cout << "⚠️  ADVERTENCIAS:" << endl;
        for (const string& warn : warnings) {
            cout << "  - " << warn << endl;
        }
    }
    
    if (errors.empty() && warnings.empty()) {
        cout << endl;
        cout << "✅ Análisis semántico exitoso - No se encontraron errores" << endl;
    }
}

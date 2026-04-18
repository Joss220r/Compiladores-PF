/**
 * SemanticAnalyzer.h - Analizador Semántico
 * Curso de Compiladores 2026 - UMG
 */

#ifndef SEMANTIC_ANALYZER_H
#define SEMANTIC_ANALYZER_H

#include "AST.h"
#include "SymbolTable.h"
#include <vector>
#include <string>

using namespace std;

/**
 * Analizador Semántico - Valida el AST contra el schema
 */
class SemanticAnalyzer {
private:
    const SymbolTable& symbolTable;
    vector<string> errors;
    vector<string> warnings;
    
    /**
     * Valida que la tabla exista
     */
    const Table* validateTable(const string& tableName);
    
    /**
     * Valida que las columnas existan en la tabla
     */
    void validateColumns(const SelectNode* selectNode, const Table* table);
    
    /**
     * Valida la condición WHERE
     */
    void validateCondition(const ConditionNode* condition, const Table* table);
    
    /**
     * Obtiene el tipo de dato de una expresión
     */
    DataType getExpressionType(const ExpressionNode* expr, const Table* table);
    
    /**
     * Valida compatibilidad de tipos en comparación
     */
    bool areTypesCompatible(DataType left, DataType right, CompOperator op);
    
public:
    /**
     * Constructor
     */
    SemanticAnalyzer(const SymbolTable& st) : symbolTable(st) {}
    
    /**
     * Analiza el AST
     */
    bool analyze(const SelectNode* ast);
    
    /**
     * Obtiene los errores encontrados
     */
    const vector<string>& getErrors() const { return errors; }
    
    /**
     * Obtiene los warnings
     */
    const vector<string>& getWarnings() const { return warnings; }
    
    /**
     * Imprime errores y warnings
     */
    void printDiagnostics() const;
};

#endif // SEMANTIC_ANALYZER_H

/**
 * SymbolTable.h - Tabla de Símbolos (Schema de BD)
 * Curso de Compiladores 2026 - UMG
 */

#ifndef SYMBOL_TABLE_H
#define SYMBOL_TABLE_H

#include <string>
#include <vector>
#include <map>
#include <iostream>

using namespace std;

/**
 * Tipos de datos soportados
 */
enum class DataType {
    INT,
    VARCHAR,
    FLOAT
};

/**
 * Convierte DataType a string
 */
inline string dataTypeToString(DataType type) {
    switch(type) {
        case DataType::INT:     return "INT";
        case DataType::VARCHAR: return "VARCHAR";
        case DataType::FLOAT:   return "FLOAT";
        default:                return "UNKNOWN";
    }
}

/**
 * Definición de una columna
 */
struct Column {
    string name;
    DataType type;
    
    Column(string n, DataType t) : name(n), type(t) {}
};

/**
 * Definición de una tabla
 */
struct Table {
    string name;
    vector<Column> columns;
    
    Table() : name("") {}
    Table(string n) : name(n) {}
    
    void addColumn(const string& name, DataType type) {
        columns.push_back(Column(name, type));
    }
    
    /**
     * Busca una columna por nombre (case-insensitive)
     */
    const Column* findColumn(const string& columnName) const {
        for (const auto& col : columns) {
            string colNameUpper = col.name;
            string searchNameUpper = columnName;
            
            // Convertir a mayúsculas para comparación case-insensitive
            transform(colNameUpper.begin(), colNameUpper.end(), colNameUpper.begin(), ::toupper);
            transform(searchNameUpper.begin(), searchNameUpper.end(), searchNameUpper.begin(), ::toupper);
            
            if (colNameUpper == searchNameUpper) {
                return &col;
            }
        }
        return nullptr;
    }
    
    void print() const {
        cout << "Tabla: " << name << endl;
        for (const auto& col : columns) {
            cout << "  - " << col.name << " (" << dataTypeToString(col.type) << ")" << endl;
        }
    }
};

/**
 * Tabla de símbolos (Schema de la base de datos)
 */
class SymbolTable {
private:
    map<string, Table> tables;
    
public:
    /**
     * Constructor - inicializa el schema fijo
     */
    SymbolTable();
    
    /**
     * Busca una tabla por nombre (case-insensitive)
     */
    const Table* findTable(const string& tableName) const;
    
    /**
     * Imprime el schema completo
     */
    void print() const;
};

#endif // SYMBOL_TABLE_H

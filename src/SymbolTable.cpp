/**
 * SymbolTable.cpp - Implementación de la Tabla de Símbolos
 * Curso de Compiladores 2026 - UMG
 */

#include "../include/SymbolTable.h"
#include <algorithm>

SymbolTable::SymbolTable() {
    // Tabla: usuarios (id: INT, nombre: VARCHAR, edad: INT, ciudad: VARCHAR)
    Table usuarios("usuarios");
    usuarios.addColumn("id", DataType::INT);
    usuarios.addColumn("nombre", DataType::VARCHAR);
    usuarios.addColumn("edad", DataType::INT);
    usuarios.addColumn("ciudad", DataType::VARCHAR);
    tables["usuarios"] = usuarios;
    
    // Tabla: productos (id: INT, nombre: VARCHAR, precio: FLOAT, categoria: VARCHAR)
    Table productos("productos");
    productos.addColumn("id", DataType::INT);
    productos.addColumn("nombre", DataType::VARCHAR);
    productos.addColumn("precio", DataType::FLOAT);
    productos.addColumn("categoria", DataType::VARCHAR);
    tables["productos"] = productos;
}

const Table* SymbolTable::findTable(const string& tableName) const {
    // Búsqueda case-insensitive
    for (const auto& pair : tables) {
        string tableNameUpper = pair.first;
        string searchNameUpper = tableName;
        
        transform(tableNameUpper.begin(), tableNameUpper.end(), tableNameUpper.begin(), ::toupper);
        transform(searchNameUpper.begin(), searchNameUpper.end(), searchNameUpper.begin(), ::toupper);
        
        if (tableNameUpper == searchNameUpper) {
            return &pair.second;
        }
    }
    return nullptr;
}

void SymbolTable::print() const {
    cout << "========== SCHEMA DE BASE DE DATOS ==========" << endl;
    for (const auto& pair : tables) {
        pair.second.print();
        cout << endl;
    }
    cout << "=============================================" << endl;
}

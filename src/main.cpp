/**
 * Compilador SQL - Mini Lenguaje
 * Curso de Compiladores 2026 - Universidad Mariano Gálvez
 * 
 * Archivo principal que integra todas las fases del compilador:
 *   1. Análisis Léxico (Lexer)     → Tokenización
 *   2. Análisis Sintáctico (Parser) → AST
 *   3. Análisis Semántico           → Validación
 * 
 * Autor: Richard Ortiz
 * Fecha: Marzo 2026
 */

#include <iostream>
#include <string>
#include <fstream>
#include <sstream>

#include "../include/Lexer.h"
#include "../include/Parser.h"
#include "../include/AST.h"
#include "../include/SymbolTable.h"
#include "../include/SemanticAnalyzer.h"

using namespace std;

/**
 * Lee el contenido de un archivo SQL
 */
string readFile(const string& filename) {
    ifstream file(filename);
    if (!file.is_open()) {
        cerr << "Error: No se pudo abrir el archivo '" << filename << "'" << endl;
        exit(1);
    }
    
    stringstream buffer;
    buffer << file.rdbuf();
    return buffer.str();
}

/**
 * Función principal
 */
int main(int argc, char* argv[]) {
    cout << "============================================" << endl;
    cout << "  Compilador SQL - Mini Lenguaje" << endl;
    cout << "  Curso de Compiladores 2026 - UMG" << endl;
    cout << "  Autor: Richard Ortiz" << endl;
    cout << "============================================" << endl;
    cout << endl;
    
    string query;
    
    if (argc >= 2) {
        // Modo archivo
        string filename = argv[1];
        cout << "[ARCHIVO] " << filename << endl;
        query = readFile(filename);
    } else {
        // Modo interactivo
        cout << "[MODO INTERACTIVO] Escriba su query SQL:" << endl;
        cout << "> ";
        getline(cin, query);
    }
    
    cout << endl;
    cout << "--- QUERY DE ENTRADA ---" << endl;
    cout << query << endl;
    cout << "------------------------" << endl;
    cout << endl;
    
    // ========================================================
    // FASE 1: ANÁLISIS LÉXICO (TOKENIZACIÓN)
    // ========================================================
    cout << "========== FASE 1: ANÁLISIS LÉXICO ==========" << endl;
    
    Lexer lexer(query);
    vector<Token> tokens = lexer.tokenize();
    
    cout << "Tokens encontrados: " << tokens.size() << endl;
    cout << endl;
    
    for (const Token& token : tokens) {
        cout << "  ";
        token.print();
        cout << endl;
    }
    cout << endl;
    
    // ========================================================
    // FASE 2: ANÁLISIS SINTÁCTICO (PARSER + AST)
    // ========================================================
    cout << "========== FASE 2: ANÁLISIS SINTÁCTICO ==========" << endl;
    
    try {
        Parser parser(tokens);
        unique_ptr<SelectNode> ast = parser.parse();
        
        cout << "Parsing exitoso. Árbol de Sintaxis Abstracta (AST):" << endl;
        cout << endl;
        ast->print(2);
        cout << endl;
        
        // ========================================================
        // FASE 3: ANÁLISIS SEMÁNTICO
        // ========================================================
        cout << "========== FASE 3: ANÁLISIS SEMÁNTICO ==========" << endl;
        
        SymbolTable symbolTable;
        
        cout << "Schema de referencia:" << endl;
        symbolTable.print();
        cout << endl;
        
        SemanticAnalyzer analyzer(symbolTable);
        bool valid = analyzer.analyze(ast.get());
        
        analyzer.printDiagnostics();
        cout << endl;
        
        // ========================================================
        // RESULTADO FINAL
        // ========================================================
        cout << "========== RESULTADO ==========" << endl;
        if (valid) {
            cout << "✅ La query SQL es VÁLIDA sintáctica y semánticamente." << endl;
        } else {
            cout << "❌ La query SQL tiene ERRORES." << endl;
        }
        cout << endl;
        
    } catch (const runtime_error& e) {
        cout << endl;
        cout << "❌ ERROR DE SINTAXIS:" << endl;
        cout << "  " << e.what() << endl;
        cout << endl;
        cout << "========== RESULTADO ==========" << endl;
        cout << "❌ La query SQL tiene ERRORES de sintaxis." << endl;
        cout << endl;
        return 1;
    }
    
    return 0;
}

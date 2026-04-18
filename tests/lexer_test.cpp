/**
 * @file lexer_test.cpp
 * @brief Tests para el Analizador Léxico siguiendo TDD
 * 
 * Este archivo demuestra el desarrollo dirigido por pruebas (TDD) para
 * el analizador léxico del compilador SQL.
 * 
 * CONCEPTOS CLAVE:
 * - Tokens: Unidades léxicas con significado (SELECT, *, FROM, etc.)
 * - Lexemas: La cadena actual que representa el token
 * - Expresiones Regulares: Patrones para identificar tokens
 * 
 * @author Curso Compiladores 2026
 */

#include <gtest/gtest.h>
#include "../include/Lexer.h"
#include "../include/Token.h"
#include <sstream>

/**
 * Test Suite para el Analizador Léxico
 * 
 * Cada test sigue el patrón AAA:
 * - Arrange: Preparar datos de entrada
 * - Act: Ejecutar el código a probar
 * - Assert: Verificar resultados esperados
 */
class LexerTest : public ::testing::Test {
protected:
    /**
     * SetUp se ejecuta antes de cada test
     * Útil para inicializar estado común
     */
    void SetUp() override {
        // Inicialización común si es necesaria
    }
};

/**
 * TEST 1: Reconocimiento de palabras clave SQL
 * 
 * OBJETIVO: Verificar que el lexer identifica correctamente
 * las palabras reservadas de SQL.
 */
TEST_F(LexerTest, RecognizeKeywords) {
    // Arrange
    std::string input = "SELECT FROM WHERE INSERT UPDATE DELETE CREATE TABLE";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    Token token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::SELECT);
    EXPECT_EQ(token.lexeme, "SELECT");
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::FROM);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::WHERE);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::INSERT);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::UPDATE);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::DELETE);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::CREATE);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::TABLE);
}

/**
 * TEST 2: Reconocimiento de identificadores
 * 
 * OBJETIVO: Verificar que el lexer distingue entre
 * palabras clave e identificadores de usuario.
 */
TEST_F(LexerTest, RecognizeIdentifiers) {
    // Arrange
    std::string input = "usuarios nombre_completo edad tabla1 _id";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    Token token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::IDENTIFIER);
    EXPECT_EQ(token.lexeme, "usuarios");
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::IDENTIFIER);
    EXPECT_EQ(token.lexeme, "nombre_completo");
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::IDENTIFIER);
    EXPECT_EQ(token.lexeme, "edad");
}

/**
 * TEST 3: Reconocimiento de números
 * 
 * OBJETIVO: Verificar el reconocimiento de literales
 * numéricos enteros y decimales.
 */
TEST_F(LexerTest, RecognizeNumbers) {
    // Arrange
    std::string input = "42 3.14159 0 -15 100.00";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    Token token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::NUMBER);
    EXPECT_EQ(token.lexeme, "42");
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::NUMBER);
    EXPECT_EQ(token.lexeme, "3.14159");
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::NUMBER);
    EXPECT_EQ(token.lexeme, "0");
}

/**
 * TEST 4: Reconocimiento de strings
 * 
 * OBJETIVO: Verificar el manejo de cadenas de texto
 * con comillas simples (estándar SQL).
 */
TEST_F(LexerTest, RecognizeStrings) {
    // Arrange
    std::string input = "'Hola Mundo' 'SQL es genial' 'Compiladores 2026'";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    Token token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::STRING);
    EXPECT_EQ(token.lexeme, "'Hola Mundo'");
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::STRING);
    EXPECT_EQ(token.lexeme, "'SQL es genial'");
}

/**
 * TEST 5: Reconocimiento de operadores
 * 
 * OBJETIVO: Verificar el reconocimiento de operadores
 * SQL incluyendo comparación y aritméticos.
 */
TEST_F(LexerTest, RecognizeOperators) {
    // Arrange
    std::string input = "= != < > <= >= + - * / AND OR NOT";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    Token token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::EQUAL);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::NOT_EQUAL);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::LESS_THAN);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::GREATER_THAN);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::LESS_EQUAL);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::GREATER_EQUAL);
}

/**
 * TEST 6: Reconocimiento de símbolos especiales
 * 
 * OBJETIVO: Verificar el reconocimiento de delimitadores
 * y símbolos de puntuación SQL.
 */
TEST_F(LexerTest, RecognizeSymbols) {
    // Arrange
    std::string input = "( ) , ; * .";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    Token token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::LEFT_PAREN);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::RIGHT_PAREN);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::COMMA);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::SEMICOLON);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::ASTERISK);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::DOT);
}

/**
 * TEST 7: Consulta SQL completa
 * 
 * OBJETIVO: Test de integración del lexer con una
 * consulta SQL real.
 */
TEST_F(LexerTest, CompleteSelectQuery) {
    // Arrange
    std::string input = "SELECT * FROM usuarios WHERE edad > 18;";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    std::vector<TokenType> expected = {
        TokenType::SELECT,
        TokenType::ASTERISK,
        TokenType::FROM,
        TokenType::IDENTIFIER,  // usuarios
        TokenType::WHERE,
        TokenType::IDENTIFIER,  // edad
        TokenType::GREATER_THAN,
        TokenType::NUMBER,      // 18
        TokenType::SEMICOLON,
        TokenType::END_OF_FILE
    };
    
    for (const auto& expectedType : expected) {
        Token token = lexer.getNextToken();
        EXPECT_EQ(token.type, expectedType);
    }
}

/**
 * TEST 8: Manejo de errores léxicos
 * 
 * OBJETIVO: Verificar que el lexer maneja correctamente
 * caracteres no válidos y reporta errores apropiados.
 */
TEST_F(LexerTest, HandleLexicalErrors) {
    // Arrange
    std::string input = "SELECT @ FROM usuarios";  // @ no es válido en SQL
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act
    Token token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::SELECT);
    
    token = lexer.getNextToken();
    
    // Assert
    EXPECT_EQ(token.type, TokenType::ERROR);
    EXPECT_FALSE(token.lexeme.empty());  // Debe contener info del error
}

/**
 * TEST 9: Ignorar espacios en blanco y comentarios
 * 
 * OBJETIVO: Verificar que el lexer ignora correctamente
 * espacios, tabs, saltos de línea y comentarios SQL.
 */
TEST_F(LexerTest, IgnoreWhitespaceAndComments) {
    // Arrange
    std::string input = R"(
        SELECT  *   -- Seleccionar todo
        FROM    usuarios
        /* Comentario
           multilínea */
        WHERE   edad > 18
    )";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    Token token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::SELECT);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::ASTERISK);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::FROM);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::IDENTIFIER);
    
    token = lexer.getNextToken();
    EXPECT_EQ(token.type, TokenType::WHERE);
}

/**
 * TEST 10: Tracking de posición (línea y columna)
 * 
 * OBJETIVO: Verificar que el lexer mantiene correctamente
 * la información de posición para reportes de error.
 */
TEST_F(LexerTest, TrackPosition) {
    // Arrange
    std::string input = "SELECT *\nFROM usuarios\nWHERE edad > 18";
    std::istringstream stream(input);
    Lexer lexer(stream);
    
    // Act & Assert
    Token token = lexer.getNextToken();  // SELECT en línea 1
    EXPECT_EQ(token.line, 1);
    EXPECT_EQ(token.column, 1);
    
    token = lexer.getNextToken();  // * en línea 1
    EXPECT_EQ(token.line, 1);
    EXPECT_GT(token.column, 1);
    
    token = lexer.getNextToken();  // FROM en línea 2
    EXPECT_EQ(token.line, 2);
    EXPECT_EQ(token.column, 1);
    
    token = lexer.getNextToken();  // usuarios en línea 2
    EXPECT_EQ(token.line, 2);
    
    token = lexer.getNextToken();  // WHERE en línea 3
    EXPECT_EQ(token.line, 3);
}

// Función principal para ejecutar los tests
int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
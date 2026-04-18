# Makefile para Compilador SQL (C++)
# Curso de Compiladores 2026 - UMG

# Compilador
CXX = g++

# Flags de compilación
CXXFLAGS = -std=c++17 -Wall -Wextra -pedantic -g -Iinclude

# Directorios
SRC_DIR = src
INCLUDE_DIR = include
TEST_DIR = tests
BUILD_DIR = build
EXAMPLES_DIR = examples

# Archivos fuente
SOURCES = $(wildcard $(SRC_DIR)/*.cpp)
OBJECTS = $(SOURCES:$(SRC_DIR)/%.cpp=$(BUILD_DIR)/%.o)

# Ejecutable
TARGET = sql_compiler

# Regla principal
all: $(BUILD_DIR) $(TARGET)

# Crear directorio de build
$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

# Compilar ejecutable
$(TARGET): $(OBJECTS)
	$(CXX) $(CXXFLAGS) -o $@ $^

# Compilar objetos
$(BUILD_DIR)/%.o: $(SRC_DIR)/%.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

# Ejecutar ejemplos
run: $(TARGET)
	@echo "Ejecutando query1.sql:"
	./$(TARGET) $(EXAMPLES_DIR)/query1.sql

run-all: $(TARGET)
	@echo "=== Ejecutando todos los ejemplos ==="
	@for file in $(EXAMPLES_DIR)/*.sql; do \
		echo "\n--- $$file ---"; \
		./$(TARGET) $$file; \
	done

# Limpiar
clean:
	rm -rf $(BUILD_DIR) $(TARGET)

# Tests (cuando estén implementados)
test: $(TARGET)
	@echo "Ejecutando tests..."
	# TODO: implementar tests

# Ayuda
help:
	@echo "Makefile para Compilador SQL"
	@echo ""
	@echo "Targets disponibles:"
	@echo "  make          - Compilar el proyecto"
	@echo "  make run      - Ejecutar query1.sql"
	@echo "  make run-all  - Ejecutar todos los ejemplos"
	@echo "  make clean    - Limpiar archivos compilados"
	@echo "  make test     - Ejecutar tests (TODO)"
	@echo "  make help     - Mostrar esta ayuda"

.PHONY: all clean run run-all test help

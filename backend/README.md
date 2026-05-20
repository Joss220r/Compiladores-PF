# Backend - SQL Platform

Backend Java/Spring Boot para validar querys de distintos motores:

- SQL generico
- NoSQL
- MongoDB
- SQL Server
- MySQL
- PostgreSQL
- Redis

## Estructura
  
```text
backend/
  src/main/java/com/compiladores/sqlplatform/
    controller/              # Endpoints REST y configuracion HTTP
    dto/                     # Requests y responses
    model/                   # Tipos compartidos de respuesta
    service/                 # Servicio principal
    service/compiler/        # Interfaces para Lexer, Parser y Semantico
```

`QueryValidationService` usa interfaces (`LexerPort`, `ParserPort`, `SemanticAnalyzerPort`) para no depender todavia de los modulos de Lexer, Parser y Semantico. Por ahora estan conectados adaptadores mock.

## Ejecutar localmente

Requisitos:

- Java 17+
- Maven 3.9+

```bash
cd backend
mvn spring-boot:run
```

El backend escucha en `http://localhost:8080`.

## Endpoint principal

```http
POST /api/validate
Content-Type: application/json
```

Body:

```json
{
  "engine": "SQL",
  "query": "SELECT * FROM usuarios WHERE edad > 18;"
}
```

Respuesta:

```json
{
  "valid": true,
  "engine": "SQL",
  "message": "Query validada con mocks. Pendiente conectar Lexer, Parser y Semantico reales.",
  "errors": [],
  "tokens": [],
  "ast": {},
  "semanticResult": {}
}
```

## Variables de entorno

- `PORT`: puerto del servidor. Render lo inyecta automaticamente.
- `CORS_ALLOWED_ORIGINS`: origenes permitidos para el frontend, separados por coma.

Ejemplo:

```bash
CORS_ALLOWED_ORIGINS=http://localhost:5173 mvn spring-boot:run
```

## Render

Este modulo incluye `Dockerfile`. El archivo `render.yaml` de la raiz define el servicio web del backend con `rootDir: backend`.

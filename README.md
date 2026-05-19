# SQL Platform Validator - Backend y Frontend

Documentacion del bloque de Jose: backend, frontend, API de validacion, mocks de integracion y preparacion para Render.

Este bloque no implementa directamente Lexer, Parser ni Analisis Semantico reales. Es la base de integracion para que esos modulos se conecten despues mediante interfaces.

## Objetivo

Crear una plataforma web que permita escribir una query, seleccionar el motor/base de datos y enviarla a un backend Java para recibir una respuesta estandar de validacion.

Motores contemplados:

- SQL generico
- NoSQL
- MongoDB
- SQL Server
- MySQL
- PostgreSQL
- Redis

## Alcance de este bloque

Incluido:

- Backend Java con Spring Boot.
- Frontend en Vue.js.
- Endpoint `POST /api/validate`.
- Servicio principal `QueryValidationService`.
- DTOs y modelos de respuesta.
- Interfaces para conectar Lexer, Parser y Semantico.
- Mocks temporales para responder mientras los otros modulos no esten listos.
- Manejo basico de errores en frontend.
- Pruebas basicas del endpoint.
- Configuracion inicial para Render.
- Favicon y paleta visual del proyecto.

No incluido en este bloque:

- Lexer real.
- Parser real.
- Analisis semantico real.
- Ejecucion real de queries contra una base de datos.
- Administracion de ramas o acciones de Git.

## Estructura

```text
backend/
  Dockerfile
  pom.xml
  src/main/java/com/compiladores/sqlplatform/
    controller/
    dto/
    model/
    service/
    service/compiler/
  src/test/java/com/compiladores/sqlplatform/

frontend/
  index.html
  package.json
  public/favicon.png
  src/
    App.vue
    assets/main.css
    services/queryValidationApi.js

render.yaml
```

## Backend

Tecnologia:

- Java 17+
- Spring Boot
- Maven

Ruta:

```text
backend/
```

### Ejecutar backend local

```powershell
cd C:\Proyectos\Compiladores-PF\backend
mvn spring-boot:run
```

Si Maven no esta en el PATH, usar la ruta completa instalada en la maquina:

```powershell
cd C:\Proyectos\Compiladores-PF\backend
C:\Maven\apache-maven-3.9.15\bin\mvn.cmd spring-boot:run
```

El backend corre en:

```text
http://localhost:8080
```

Health check:

```text
GET http://localhost:8080/api/health
```

### Endpoint principal

```http
POST http://localhost:8080/api/validate
Content-Type: application/json
```

Body:

```json
{
  "engine": "SQL",
  "query": "SELECT * FROM usuarios WHERE edad > 18;"
}
```

Respuesta esperada:

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

### Contratos de integracion

Los otros modulos deben conectarse mediante estas interfaces:

```text
backend/src/main/java/com/compiladores/sqlplatform/service/compiler/LexerPort.java
backend/src/main/java/com/compiladores/sqlplatform/service/compiler/ParserPort.java
backend/src/main/java/com/compiladores/sqlplatform/service/compiler/SemanticAnalyzerPort.java
```

Implementaciones temporales actuales:

```text
MockLexerAdapter.java
MockParserAdapter.java
MockSemanticAnalyzerAdapter.java
```

Cuando los modulos reales esten listos, estos mocks se reemplazan o se desactivan.

## Frontend

Tecnologia:

- Vue.js
- Vite
- JavaScript

Ruta:

```text
frontend/
```

### Ejecutar frontend local

```powershell
cd C:\Proyectos\Compiladores-PF\frontend
npm.cmd install
npm.cmd run dev
```

El frontend corre en:

```text
http://localhost:5173
```

La app permite:

- Seleccionar motor/base de datos.
- Escribir una query.
- Enviar la query al backend.
- Mostrar si la respuesta es valida o invalida.
- Mostrar errores.
- Mostrar tokens, AST y resultado semantico si el backend los devuelve.

### Configurar URL del backend

Archivo de ejemplo:

```text
frontend/.env.example
```

Variable:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Para Render, esta variable debe apuntar a la URL publica del backend.

## Base de datos en Render

Se creo una base PostgreSQL en Render para usarla despues como catalogo real del proyecto.

Uso recomendado en esta fase:

- Guardar tablas disponibles.
- Guardar columnas disponibles.
- Guardar tipos de datos.
- Guardar logs de validacion.

No se recomienda ejecutar directamente las querys escritas por el usuario contra la base de datos. Primero deben pasar por Lexer, Parser y Analisis Semantico.

Variables sugeridas para backend:

```text
DATABASE_URL=jdbc:postgresql://HOST:5432/DATABASE
DATABASE_USERNAME=USER
DATABASE_PASSWORD=PASSWORD
```

No subir credenciales reales al repositorio.

## Render

El archivo `render.yaml` prepara dos servicios:

- `sql-platform-backend`: backend Spring Boot usando Docker.
- `sql-platform-frontend`: sitio estatico generado por Vite.

Antes de desplegar, revisar:

```text
render.yaml
backend/Dockerfile
frontend/.env.example
```

## Pruebas

### Backend

Pruebas agregadas:

```text
backend/src/test/java/com/compiladores/sqlplatform/controller/QueryValidationControllerTest.java
```

Cubre:

- `GET /api/health`
- `POST /api/validate` con query valida
- rechazo de query vacia
- rechazo cuando falta `engine`

Ejecutar:

```powershell
cd C:\Proyectos\Compiladores-PF\backend
mvn test
```

O con ruta completa:

```powershell
cd C:\Proyectos\Compiladores-PF\backend
C:\Maven\apache-maven-3.9.15\bin\mvn.cmd test
```

### Frontend

Compilar:

```powershell
cd C:\Proyectos\Compiladores-PF\frontend
npm.cmd run build
```

## Flujo local recomendado

1. Levantar backend:

```powershell
cd C:\Proyectos\Compiladores-PF\backend
mvn spring-boot:run
```

2. Levantar frontend:

```powershell
cd C:\Proyectos\Compiladores-PF\frontend
npm.cmd run dev
```

3. Abrir:

```text
http://localhost:5173
```

4. Probar una query:

```sql
SELECT * FROM usuarios WHERE edad > 18;
```

## Estado actual

Funcional:

- Frontend Vue corre localmente.
- Backend Spring Boot corre localmente.
- Frontend se conecta al backend.
- Backend responde con mocks.
- Tests del backend pasan.
- Build del frontend funciona.
- Base PostgreSQL de Render creada para integracion posterior.

Pendiente:

- Conectar backend a PostgreSQL.
- Crear servicio de catalogo real.
- Integrar Lexer real.
- Integrar Parser real.
- Integrar Analisis Semantico real.
- Desplegar backend y frontend en Render.

## Responsabilidades futuras del equipo

- Andre: Lexer real.
- Ademar: Parser real.
- Hugo: Analisis semantico real.
- Jose: Backend, frontend, DB, integracion y despliegue.

Cada modulo debe integrarse respetando las interfaces existentes para evitar romper la demo web.

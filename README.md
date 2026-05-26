# SQL Platform Validator

Plataforma web para validar querys de distintos motores y estilos de base de datos. El proyecto integra un backend en Java con Spring Boot y un frontend en Vue.js, con una interfaz orientada a pruebas, análisis de errores, sugerencias de corrección, historial y dashboard.

El objetivo principal es apoyar el desarrollo de un compilador/validador multi-motor capaz de revisar sintaxis, dialecto, tokens, AST, semántica básica y errores comunes antes de ejecutar o migrar consultas.

## Motores soportados

- MySQL
- PostgreSQL
- SQL Server
- MongoDB
- Redis
- NoSQL

También existe soporte interno para SQL genérico en el backend, aunque el selector visual prioriza los motores principales del proyecto.

## Funcionalidades principales

- Validación de querys desde una interfaz web.
- Lexer básico con tokens, línea y columna.
- Parser básico para SQL, MongoDB y Redis.
- Análisis semántico inicial.
- Validación de dialectos SQL por motor.
- Detección de motor equivocado.
- Sugerencias de corrección sin usar IA externa.
- Historial de análisis guardado en PostgreSQL si la base de datos está disponible.
- Dashboard con estadísticas de uso.
- Login básico contra PostgreSQL.
- Modo claro y oscuro con preferencia guardada.
- Loader visual durante el análisis.
- Tokens, AST y resultado semántico en paneles colapsables.
- Configuración preparada para despliegue en Render.

## Arquitectura general

```text
Frontend Vue.js
    |
    | HTTP/JSON
    v
Backend Spring Boot
    |
    |-- Lexer
    |-- Parser
    |-- Dialect Validator
    |-- Semantic Analyzer
    |-- Correction Suggestions
    |-- History Service
    |-- Auth Service
    |
    v
PostgreSQL en Render
```

## Estructura del proyecto

```text
Compiladores-PF/
  backend/
    Dockerfile
    pom.xml
    src/main/java/com/compiladores/sqlplatform/
      controller/
      dto/
      model/
      service/
      service/compiler/
      service/semantic/
    src/main/resources/
      application.properties
      db/
        app_users.sql
        query_history.sql
    src/test/java/com/compiladores/sqlplatform/

  frontend/
    index.html
    package.json
    public/
      favicon.png
      loading.gif
    src/
      App.vue
      App.test.js
      assets/
      services/

  render.yaml
  README.md
```

## Backend

El backend está desarrollado con:

- Java 17+
- Spring Boot 3.3.5
- Maven
- PostgreSQL Driver

Ruta:

```text
backend/
```

### Ejecutar backend local

```powershell
cd C:\Proyectos\Compiladores-PF\backend
mvn spring-boot:run
```

Si el sistema tiene Java 8 configurado por defecto, usar Java 17 o superior para Spring Boot:

```powershell
cd C:\Proyectos\Compiladores-PF\backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

URL local:

```text
http://localhost:8080
```

Health check:

```http
GET /api/health
```

## Frontend

El frontend está desarrollado con:

- Vue.js 3
- Vite
- JavaScript
- Vitest

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

URL local:

```text
http://localhost:5173
```

### Configurar URL del backend

El frontend lee la URL del backend desde:

```text
VITE_API_BASE_URL
```

En local, si no existe esa variable, usa:

```text
http://localhost:8080
```

Para Render debe apuntar a la URL pública del backend.

## Login

La aplicación incluye un login básico para controlar el acceso visual al sistema.

Endpoint:

```http
POST /api/auth/login
```

Body:

```json
{
  "username": "usuario",
  "password": "contraseña"
}
```

Respuesta exitosa:

```json
{
  "success": true,
  "username": "usuario",
  "displayName": "Nombre",
  "role": "USER",
  "message": "Login correcto."
}
```

La sesión se guarda en `localStorage`, por eso al recargar la página el usuario continúa dentro. Para salir se usa el botón `Salir`.

Script SQL:

```text
backend/src/main/resources/db/app_users.sql
```

## Endpoint principal de validación

```http
POST /api/validate
Content-Type: application/json
```

Body:

```json
{
  "engine": "MYSQL",
  "query": "SELECT * FROM usuarios WHERE edad > 18;"
}
```

Respuesta general:

```json
{
  "success": true,
  "valid": true,
  "engine": "MYSQL",
  "message": "Query validada por Lexer, Parser y Analisis Semantico.",
  "errors": [],
  "warnings": [],
  "tokens": [],
  "ast": {},
  "semanticResult": {},
  "output": null,
  "suggestions": []
}
```

### Modelo estándar de errores

Cada error o warning tiene esta estructura:

```json
{
  "phase": "PARSER",
  "severity": "ERROR",
  "message": "Mensaje claro del problema.",
  "line": 1,
  "column": 15,
  "fragment": "WHERE"
}
```

Fases usadas:

- `LEXER`
- `PARSER`
- `SEMANTIC`
- `DIALECT`
- `SYSTEM`

## Validaciones implementadas

### Lexer

Detecta:

- Query vacía.
- Comillas simples sin cerrar.
- Comillas dobles sin cerrar.
- Paréntesis sin cerrar.
- Llaves sin cerrar.
- Corchetes sin cerrar.
- Caracteres desconocidos.
- Comentarios de línea y bloque.
- Tokens con línea y columna.

### Parser SQL

Valida instrucciones principales:

- `SELECT`
- `INSERT`
- `UPDATE`
- `DELETE`
- `CREATE TABLE`
- `DROP TABLE`
- `WITH`

También soporta casos avanzados:

- Alias de tablas y columnas.
- `JOIN ... ON`
- `JOIN ... USING (...)`
- `LEFT JOIN`
- `INNER JOIN`
- `NATURAL JOIN`
- múltiples joins.
- `GROUP BY`
- `HAVING`
- `ORDER BY`
- `LIMIT`
- subconsultas en `WHERE IN`.
- subconsultas con `EXISTS`.
- subconsultas escalares.
- subconsultas en `FROM`.
- CTE con `WITH`.
- cuantificadores `ALL` y `ANY`.
- funciones y agregados como `SUM`, `AVG`, `MAX`, `COUNT`.

### Parser MongoDB

Soporte básico para:

- `db.coleccion.find({...})`
- `db.coleccion.insertOne({...})`
- `db.coleccion.insertMany([...])`
- `db.coleccion.updateOne(filtro, actualizacion)`
- `db.coleccion.updateMany(filtro, actualizacion)`
- `db.coleccion.deleteOne({...})`
- `db.coleccion.deleteMany({...})`

Valida:

- forma `db.coleccion.operacion(...)`.
- operación soportada.
- cantidad de argumentos.
- objetos y arreglos básicos.
- uso correcto de `$set`.

### Parser Redis

Soporte básico para:

- `SET key value`
- `GET key`
- `DEL key`
- `EXISTS key`
- `EXPIRE key seconds`
- `TTL key`
- `HSET key field value`
- `HGET key field`
- `HGETALL key`
- `LPUSH key value`
- `RPUSH key value`
- `LPOP key`
- `RPOP key`
- `SADD key member`
- `SMEMBERS key`

Valida:

- comando soportado.
- cantidad mínima de argumentos.
- `EXPIRE` con segundos numéricos.
- detección de sintaxis SQL usada por error en Redis.

### Dialectos SQL

Validaciones por motor:

MySQL:

- acepta `AUTO_INCREMENT`.
- acepta `LIMIT`.
- rechaza `TOP`.
- rechaza `SERIAL`.
- rechaza `IDENTITY(1,1)`.

PostgreSQL:

- acepta `SERIAL`.
- acepta `LIMIT`.
- rechaza `AUTO_INCREMENT`.
- rechaza `IDENTITY(1,1)`.
- rechaza `TOP`.

SQL Server:

- acepta `TOP`.
- acepta `IDENTITY(1,1)`.
- acepta `NVARCHAR`.
- rechaza `LIMIT`.
- rechaza `AUTO_INCREMENT`.
- rechaza `SERIAL`.

## Sugerencias de corrección

El backend genera sugerencias por reglas internas. No usa IA externa.

Casos soportados:

- SQL Server con `LIMIT` sugiere `TOP`.
- MySQL/PostgreSQL con `TOP` sugiere `LIMIT`.
- PostgreSQL con `AUTO_INCREMENT` sugiere `SERIAL`.
- MySQL con `SERIAL` sugiere `INT AUTO_INCREMENT`.
- MongoDB con `set` sugiere `$set`.
- Redis `SET` incompleto sugiere agregar valor.
- `WHERE` vacío sugiere una condición.
- comillas o delimitadores sin cerrar sugieren cierre.

Ejemplo:

```json
{
  "title": "Usar LIMIT",
  "explanation": "MySQL usa LIMIT al final del SELECT.",
  "fixedQuery": "SELECT * FROM usuarios LIMIT 10;",
  "confidence": "HIGH",
  "sourcePhase": "DIALECT"
}
```

## Historial y dashboard

Cada análisis puede guardarse en PostgreSQL si las variables de base de datos están configuradas. Si la base de datos falla, el análisis sigue funcionando y el error solo se registra en logs.

Endpoints:

```http
GET /api/history
GET /api/history?limit=20
GET /api/history?engine=MYSQL
GET /api/history?success=false
GET /api/history/stats
DELETE /api/history
```

Script SQL:

```text
backend/src/main/resources/db/query_history.sql
```

La tabla principal es:

```text
query_history
```

Campos principales:

- `id`
- `engine`
- `original_query`
- `success`
- `error_count`
- `warning_count`
- `errors`
- `warnings`
- `suggestions`
- `created_at`

## Base de datos

El proyecto usa PostgreSQL en Render para:

- usuarios de login.
- historial de análisis.
- dashboard.

El validador de querys no ejecuta las querys del usuario contra la base de datos. La base de datos se usa para persistencia de la aplicación.

La validación semántica puede usar un catálogo local en memoria. Si una tabla no existe en ese catálogo local, no significa que no exista en tu base real; por eso se maneja como warning y no como error fatal.

## Variables de entorno del backend

```text
CORS_ALLOWED_ORIGINS=http://localhost:5173
CATALOG_SOURCE=memory

DB_HOST=
DB_PORT=5432
DB_NAME=
DB_USER=
DB_PASSWORD=
DB_SSL=require
```

Variables opcionales para catálogo PostgreSQL:

```text
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=
```

Para desarrollo local sin catálogo real:

```text
CATALOG_SOURCE=memory
```

Para Render con PostgreSQL:

```text
DB_SSL=require
```

## Render

El archivo `render.yaml` define dos servicios:

- `sql-platform-backend`: Web Service con Docker.
- `sql-platform-frontend`: Static Site con Vite.

Archivo:

```text
render.yaml
```

Configuración esperada:

Backend:

```text
Root Directory: backend
Runtime: Docker
```

Frontend:

```text
Root Directory: frontend
Build Command: npm install && npm run build
Publish Directory: dist
```

Variable del frontend:

```text
VITE_API_BASE_URL=https://url-publica-del-backend
```

Variable importante del backend:

```text
CORS_ALLOWED_ORIGINS=https://url-publica-del-frontend
```

## Comandos de desarrollo

### Backend

Ejecutar:

```powershell
cd C:\Proyectos\Compiladores-PF\backend
mvn spring-boot:run
```

Probar:

```powershell
cd C:\Proyectos\Compiladores-PF\backend
mvn test
```

Con Java específico:

```powershell
cd C:\Proyectos\Compiladores-PF\backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
```

### Frontend

Instalar dependencias:

```powershell
cd C:\Proyectos\Compiladores-PF\frontend
npm.cmd install
```

Ejecutar:

```powershell
cd C:\Proyectos\Compiladores-PF\frontend
npm.cmd run dev
```

Compilar:

```powershell
cd C:\Proyectos\Compiladores-PF\frontend
npm.cmd run build
```

Probar:

```powershell
cd C:\Proyectos\Compiladores-PF\frontend
npm.cmd run test
```

## Ejemplos de prueba

### SQL válido

```sql
SELECT nombre, edad
FROM usuarios
WHERE edad > 18;
```

### SQL con JOIN

```sql
SELECT c.nombre, p.monto
FROM clientes c
INNER JOIN pedidos p USING (id_cliente);
```

### SQL con subconsulta

```sql
SELECT nombre, salario
FROM empleados
WHERE salario > ALL (
    SELECT salario
    FROM empleados
    WHERE puesto = 'Reclutador'
);
```

### SQL con CTE

```sql
WITH ResumenVentas AS (
    SELECT id_vendedor, SUM(monto) AS monto_total
    FROM ventas
    GROUP BY id_vendedor
)
SELECT id_vendedor, monto_total
FROM ResumenVentas;
```

### MongoDB válido

```javascript
db.usuarios.updateOne(
  { id: 1 },
  { $set: { nombre: "Carlos" } }
)
```

### Redis válido

```text
SET usuario:1 "Jose"
```

## Pruebas automatizadas

Backend:

- Pruebas de health check.
- Validación de query vacía.
- Validación SQL.
- Dialectos MySQL/PostgreSQL/SQL Server.
- MongoDB válido e inválido.
- Redis válido e inválido.
- Motor equivocado.
- Sugerencias de corrección.
- Historial.
- Tolerancia a fallo de base de datos.
- Subconsultas, joins, CTE, `GROUP BY`, `HAVING`, `ALL`, `ANY`.

Frontend:

- Render de login.
- Validación visual de errores.
- Warnings.
- Dashboard.
- Historial vacío.
- Loader.
- Modo oscuro/claro.
- Paneles colapsables.
- Aplicar sugerencias.

## Consideraciones importantes

- El sistema valida querys, no las ejecuta.
- La base de datos se usa para login e historial.
- La validación semántica es inicial y usa catálogo local o configurable.
- Si una tabla no está en el catálogo local, se muestra warning.
- Los errores de dialecto graves detienen validaciones secundarias para evitar mensajes confusos.
- El frontend mantiene la sesión en `localStorage`.
- Para probar el login desde cero, usar el botón `Salir` o limpiar `localStorage`.

## Roles del proyecto

El proyecto fue organizado por módulos:

- Lexer
- Parser
- Análisis semántico
- Backend y frontend

El backend mantiene interfaces para integrar implementaciones futuras:

```text
LexerPort.java
ParserPort.java
SemanticAnalyzerPort.java
```

Esto permite reemplazar las implementaciones básicas actuales sin cambiar el endpoint principal ni el frontend.

## Estado del proyecto

El proyecto cuenta con:

- backend funcional.
- frontend funcional.
- login funcional.
- validación multi-motor.
- historial y dashboard.
- despliegue preparado para Render.
- pruebas automatizadas de backend y frontend.
- documentación de scripts SQL para base de datos.


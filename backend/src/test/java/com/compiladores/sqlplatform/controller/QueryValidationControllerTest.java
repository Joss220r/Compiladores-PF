package com.compiladores.sqlplatform.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class QueryValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void historyStoresValidAnalysis() throws Exception {
        mockMvc.perform(delete("/api/history")).andExpect(status().isOk());

        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT * FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/history?limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].engine").value("MYSQL"))
                .andExpect(jsonPath("$.items[0].valid").value(true))
                .andExpect(jsonPath("$.items[0].errorCount").value(0));
    }

    @Test
    void historyStoresInvalidAnalysis() throws Exception {
        mockMvc.perform(delete("/api/history")).andExpect(status().isOk());

        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT * FROM usuarios WHERE;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/history?limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].valid").value(false))
                .andExpect(jsonPath("$.items[0].errorCount", greaterThanOrEqualTo(1)));
    }

    @Test
    void historyStoresWarningAnalysis() throws Exception {
        mockMvc.perform(delete("/api/history")).andExpect(status().isOk());

        String request = """
                {
                  "engine": "MYSQL",
                  "query": "UPDATE usuarios SET nombre = 'Carlos';"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings", hasSize(1)));

        mockMvc.perform(get("/api/history?limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].valid").value(true))
                .andExpect(jsonPath("$.items[0].warningCount").value(1));
    }

    @Test
    void historyReturnsNewestFirstAndFilters() throws Exception {
        mockMvc.perform(delete("/api/history")).andExpect(status().isOk());

        String mysqlValid = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT * FROM usuarios;"
                }
                """;
        String redisInvalid = """
                {
                  "engine": "REDIS",
                  "query": "SET usuario:1"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(mysqlValid))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(redisInvalid))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/history?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].engine").value("REDIS"));

        mockMvc.perform(get("/api/history?engine=MYSQL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].engine").value("MYSQL"));

        mockMvc.perform(get("/api/history?success=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].valid").value(false));
    }

    @Test
    void historyStatsReturnsTotalsAndByEngine() throws Exception {
        mockMvc.perform(delete("/api/history")).andExpect(status().isOk());

        String mysqlValid = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT * FROM usuarios;"
                }
                """;
        String redisInvalid = """
                {
                  "engine": "REDIS",
                  "query": "SET usuario:1"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(mysqlValid))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(redisInvalid))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/history/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.valid").value(1))
                .andExpect(jsonPath("$.invalid").value(1))
                .andExpect(jsonPath("$.byEngine", hasSize(2)));
    }

    @Test
    void validateQueryReturnsBasicParserAst() throws Exception {
        String request = """
                {
                  "engine": "SQL",
                  "query": "SELECT * FROM usuarios WHERE edad > 18;"
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.engine").value("SQL"))
                .andExpect(jsonPath("$.message", containsString("Analisis Semantico")))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.tokens").isArray())
                .andExpect(jsonPath("$.ast.type").value("Query"))
                .andExpect(jsonPath("$.ast.attributes.parserStatus").value("BASIC_SQL_PARSER"))
                .andExpect(jsonPath("$.ast.children[0].type").value("SelectStatement"))
                .andExpect(jsonPath("$.semanticResult.valid").value(true));
    }

    @Test
    void validateQueryRejectsBlankQuery() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": ""
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Request invalido."))
                .andExpect(jsonPath("$.errors[0].message", containsString("query")));
    }

    @Test
    void validateQueryRejectsMissingEngine() throws Exception {
        String request = """
                {
                  "query": "SELECT * FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("engine")));
    }

    @Test
    void validateQueryWarnsUnknownTableWithoutInvalidatingSyntax() throws Exception {
        String request = """
                {
                  "engine": "POSTGRESQL",
                  "query": "SELECT nombre FROM tabla_inexistente;"
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.warnings[0].message", containsString("catalogo local")))
                .andExpect(jsonPath("$.semanticResult.valid").value(true));
    }

    @Test
    void validateQueryRejectsUnknownColumn() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT email FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("columna 'email'")))
                .andExpect(jsonPath("$.semanticResult.valid").value(false));
    }

    @Test
    void validateQueryRejectsUnsupportedOperationForEngine() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "SELECT nombre FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("parece SQL")))
                .andExpect(jsonPath("$.semanticResult").doesNotExist());
    }

    @Test
    void validateQueryRejectsSqlUpdateWhenRedisEngineSelected() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "UPDATE usuarios SET nombre = 'Carlos';"
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].phase").value("DIALECT"))
                .andExpect(jsonPath("$.errors[0].message").value("Esta consulta parece SQL, pero el motor seleccionado es Redis."))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andExpect(jsonPath("$.ast").doesNotExist())
                .andExpect(jsonPath("$.semanticResult").doesNotExist());
    }

    @Test
    void validateQueryRejectsIncompatibleWhereTypes() throws Exception {
        String request = """
                {
                  "engine": "POSTGRESQL",
                  "query": "SELECT nombre FROM usuarios WHERE edad > 'hola';"
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("Tipos incompatibles")))
                .andExpect(jsonPath("$.semanticResult.valid").value(false));
    }

    @Test
    void validateQueryRejectsInsertWithDifferentColumnAndValueCounts() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "INSERT INTO usuarios (id, nombre) VALUES (1);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("columna")));
    }

    @Test
    void validateQueryWarnsUpdateWithoutWhere() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "UPDATE usuarios SET nombre = 'Jose';"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings[0].message", containsString("UPDATE sin WHERE")));
    }

    @Test
    void validateQueryWarnsDeleteWithoutWhere() throws Exception {
        String request = """
                {
                  "engine": "POSTGRESQL",
                  "query": "DELETE FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings[0].message", containsString("DELETE sin WHERE")));
    }

    @Test
    void validateQueryAcceptsMysqlAutoIncrement() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "CREATE TABLE usuarios (id INT AUTO_INCREMENT, nombre VARCHAR);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void validateQueryRejectsPostgresqlAutoIncrement() throws Exception {
        String request = """
                {
                  "engine": "POSTGRESQL",
                  "query": "CREATE TABLE usuarios (id INT AUTO_INCREMENT);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("AUTO_INCREMENT")));
    }

    @Test
    void validateQueryRejectsPostgresqlAutoIncrementInCompleteCreateTable() throws Exception {
        String request = """
                {
                  "engine": "POSTGRESQL",
                  "query": "CREATE TABLE usuarios (id INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(100));"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].phase").value("DIALECT"))
                .andExpect(jsonPath("$.errors[0].message", containsString("AUTO_INCREMENT")));
    }

    @Test
    void validateQueryRejectsMysqlSerialInCompleteCreateTable() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "CREATE TABLE usuarios (id SERIAL PRIMARY KEY, nombre VARCHAR(100));"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].phase").value("DIALECT"))
                .andExpect(jsonPath("$.errors[0].message", containsString("SERIAL")));
    }

    @Test
    void validateQueryRejectsSqlServerLimit() throws Exception {
        String request = """
                {
                  "engine": "SQL_SERVER",
                  "query": "SELECT * FROM usuarios LIMIT 10;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("LIMIT no es valido")));
    }

    @Test
    void validateQueryRejectsSqlServerLimitWithOnlyDialectError() throws Exception {
        String request = """
                {
                  "engine": "SQL_SERVER",
                  "query": "SELECT * FROM usuarios LIMIT 10;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].phase").value("DIALECT"))
                .andExpect(jsonPath("$.errors[0].message", containsString("LIMIT no es valido")))
                .andExpect(jsonPath("$.semanticResult").doesNotExist());
    }

    @Test
    void validateQuerySuggestsTopForSqlServerLimit() throws Exception {
        String request = """
                {
                  "engine": "SQL_SERVER",
                  "query": "SELECT * FROM usuarios LIMIT 10;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title", containsString("LIMIT")))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery").value("SELECT TOP 10 * FROM usuarios;"))
                .andExpect(jsonPath("$.suggestions[0].sourcePhase").value("DIALECT"));
    }

    @Test
    void validateQuerySuggestsLimitForMysqlTop() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT TOP 10 * FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title", containsString("TOP")))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery").value("SELECT * FROM usuarios LIMIT 10;"));
    }

    @Test
    void validateQuerySuggestsSerialForPostgresqlAutoIncrement() throws Exception {
        String request = """
                {
                  "engine": "POSTGRESQL",
                  "query": "CREATE TABLE usuarios (id INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(100));"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title", containsString("SERIAL")))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery", containsString("id SERIAL PRIMARY KEY")));
    }

    @Test
    void validateQuerySuggestsAutoIncrementForMysqlSerial() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "CREATE TABLE usuarios (id SERIAL PRIMARY KEY, nombre VARCHAR(100));"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title", containsString("AUTO_INCREMENT")))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery", containsString("id INT AUTO_INCREMENT PRIMARY KEY")));
    }

    @Test
    void validateQueryAcceptsMongoFind() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.find({ nombre: \\"Jose\\" })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.ast.type").value("MongoQuery"));
    }

    @Test
    void validateQueryAcceptsMongoFindWithoutFilter() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.find()"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andExpect(jsonPath("$.ast.attributes.operation").value("find"))
                .andExpect(jsonPath("$.ast.attributes.collection").value("usuarios"));
    }

    @Test
    void validateQueryAcceptsMongoInsertOneWithoutSemanticWarnings() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.insertOne({ nombre: \\"Jose\\", edad: 20 })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings", hasSize(0)));
    }

    @Test
    void validateQueryAcceptsMongoDeleteOneWithoutSemanticWarnings() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.deleteOne({ id: 1 })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings", hasSize(0)));
    }

    @Test
    void validateQueryRejectsMongoUnclosedBrace() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.find({ nombre: \\"Jose\\" "
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].phase").value("LEXER"));
    }

    @Test
    void validateQueryRejectsMongoUpdateOneWithoutSecondArgument() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.updateOne({ id: 1 })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("requiere filtro")));
    }

    @Test
    void validateQueryRejectsMongoUpdateOneWithSetWithoutDollar() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.updateOne({ id: 1 }, { set: { nombre: \\"Carlos\\" } })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("$set")));
    }

    @Test
    void validateQuerySuggestsDollarSetForMongoSet() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.updateOne({ id: 1 }, { set: { nombre: \\"Carlos\\" } })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title", containsString("$set")))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery", containsString("{ $set:")));
    }

    @Test
    void validateQueryAcceptsRedisSet() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "SET usuario:1 \\"Jose\\""
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andExpect(jsonPath("$.ast.type").value("RedisCommand"));
    }

    @Test
    void validateQueryAcceptsRedisCommonCommandsWithoutSemanticWarnings() throws Exception {
        String[] requests = {
                """
                {
                  "engine": "REDIS",
                  "query": "GET usuario:1"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "DEL usuario:1"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "EXPIRE usuario:1 3600"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "HSET usuario:1 nombre \\"Jose\\""
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "EXISTS usuario:1"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "TTL usuario:1"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "HGET usuario:1 nombre"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "HGETALL usuario:1"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "LPUSH cola \\"valor\\""
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "RPUSH cola \\"valor\\""
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "LPOP cola"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "RPOP cola"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "SADD conjunto miembro"
                }
                """,
                """
                {
                  "engine": "REDIS",
                  "query": "SMEMBERS conjunto"
                }
                """
        };

        for (String request : requests) {
            mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Test
    void validateQueryAcceptsMongoOperationsWithoutSemanticWarnings() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.updateOne({ id: 1 }, { $set: { nombre: \\"Carlos\\" } })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.warnings", hasSize(0)));
    }

    @Test
    void validateQueryAcceptsMongoSupportedOperationsWithoutSemanticWarnings() throws Exception {
        String[] requests = {
                """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.insertMany([{ nombre: \\"Jose\\" }, { nombre: \\"Carlos\\" }])"
                }
                """,
                """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.updateMany({ activo: true }, { $set: { estado: \\"ok\\" } })"
                }
                """,
                """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.deleteMany({ activo: false })"
                }
                """
        };

        for (String request : requests) {
            mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Test
    void validateQueryDoesNotRunSemanticAfterDialectError() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT TOP 10 * FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].phase").value("DIALECT"))
                .andExpect(jsonPath("$.errors[0].message", containsString("TOP no es valido")));
    }

    @Test
    void validateQueryRejectsRedisSetIncomplete() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "SET usuario:1"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("requiere")));
    }

    @Test
    void validateQuerySuggestsValueForIncompleteRedisSet() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "SET usuario:1"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title", containsString("SET")))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery").value("SET usuario:1 \"valor\""));
    }

    @Test
    void validateQueryRejectsRedisExpireWithNonNumericSeconds() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "EXPIRE usuario:1 abc"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("segundos")));
    }

    @Test
    void validateQueryRejectsMongoSyntaxWhenSqlEngineSelected() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "db.usuarios.find({ nombre: \\"Jose\\" })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("parece MongoDB")));
    }

    @Test
    void validateQueryReturnsLineAndColumnForLexerError() throws Exception {
        String request = """
                {
                  "engine": "SQL",
                  "query": "SELECT * FROM usuarios WHERE edad >= 18 @;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].line").value(1))
                .andExpect(jsonPath("$.errors[0].column").exists());
    }

    @Test
    void validateQuerySuggestsConditionForEmptyWhere() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT * FROM usuarios WHERE;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.suggestions[0].title", containsString("WHERE")))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery").value("SELECT * FROM usuarios WHERE id = 1;"));
    }

    @Test
    void validateQuerySuggestsClosingQuote() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT * FROM usuarios WHERE nombre = 'Jose"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.suggestions[0].title", containsString("comilla")))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery").value("SELECT * FROM usuarios WHERE nombre = 'Jose';"));
    }

    @Test
    void redisDoesNotAcceptSqlDelete() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "DELETE FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("Esta consulta parece SQL, pero el motor seleccionado es Redis."))
                .andExpect(jsonPath("$.warnings", hasSize(0)));
    }

    @Test
    void redisDoesNotAcceptSqlSelect() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "SELECT * FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("La query contiene errores."))
                .andExpect(jsonPath("$.errors[0].message").value("Esta consulta parece SQL, pero el motor seleccionado es Redis."));
    }

    @Test
    void redisSetIncompleteUsesClearMessage() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "SET usuario:1"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("El comando SET requiere key y value."));
    }

    @Test
    void redisExpireWithNonNumericSecondsUsesClearMessage() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "EXPIRE usuario:1 abc"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("EXPIRE requiere segundos numéricos."));
    }

    @Test
    void redisUnknownCommandIsInvalid() throws Exception {
        String request = """
                {
                  "engine": "REDIS",
                  "query": "UPDATE usuario:1 \\"Jose\\""
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("Comando Redis no soportado: UPDATE."));
    }

    @Test
    void mongodbUpdateOneIncompleteUsesClearMessage() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.updateOne({ id: 1 })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("updateOne requiere filtro y actualización."));
    }

    @Test
    void mongodbSetWithoutDollarIncludesSuggestion() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "db.usuarios.updateOne({ id: 1 }, { set: { nombre: \\"Carlos\\" } })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("En MongoDB el operador correcto es $set, no set."))
                .andExpect(jsonPath("$.suggestions[0].fixedQuery")
                        .value("db.usuarios.updateOne({ id: 1 }, { $set: { nombre: \"Carlos\" } })"));
    }

    @Test
    void genericSqlDoesNotAcceptRedisSyntax() throws Exception {
        String request = """
                {
                  "engine": "SQL",
                  "query": "SET usuario:1 \\"Jose\\""
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("Esta consulta parece Redis, pero el motor seleccionado es SQL."));
    }

    @Test
    void mysqlDoesNotAcceptMongoSyntax() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "db.usuarios.find({ nombre: \\"Jose\\" })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("Esta consulta parece MongoDB, pero el motor seleccionado es MySQL."));
    }

    @Test
    void sqlServerDoesNotAcceptMongoSyntax() throws Exception {
        String request = """
                {
                  "engine": "SQL_SERVER",
                  "query": "db.usuarios.find({ nombre: \\"Jose\\" })"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("Esta consulta parece MongoDB, pero el motor seleccionado es SQL Server."));
    }

    @Test
    void mongodbDoesNotAcceptSqlSyntax() throws Exception {
        String request = """
                {
                  "engine": "MONGODB",
                  "query": "SELECT * FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("Esta consulta parece SQL, pero el motor seleccionado es MongoDB."));
    }

    @Test
    void sqlAcceptsWhereInNestedSelect() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT nombre FROM usuarios WHERE id IN (SELECT id FROM usuarios);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsWhereExistsNestedSelect() throws Exception {
        String request = """
                {
                  "engine": "POSTGRESQL",
                  "query": "SELECT nombre FROM usuarios WHERE EXISTS (SELECT id FROM usuarios WHERE edad > 18);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsFromNestedSelectWithAlias() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT nombre FROM (SELECT nombre FROM usuarios) u;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlRejectsNestedSelectWithoutFrom() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT nombre FROM usuarios WHERE id IN (SELECT id);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("La sentencia SELECT debe incluir FROM."));
    }

    @Test
    void sqlRejectsUnclosedNestedSelect() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT nombre FROM usuarios WHERE id IN (SELECT id FROM usuarios;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("Falta cerrar")));
    }

    @Test
    void sqlAcceptsCorrelatedNestedSelectWithAliasAndAggregate() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT e.nombre, e.salario, e.id_departamento FROM empleados e WHERE e.salario > (SELECT AVG(sub.salario) FROM empleados sub WHERE sub.id_departamento = e.id_departamento);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsNestedSelectAcrossCatalogTables() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT nombre, email FROM clientes WHERE id IN (SELECT id_cliente FROM pedidos WHERE cantidad > 100);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsDerivedTableWithJoinGroupByAndHavingLikeFilter() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT sub.categoria, sub.total_ventas, (sub.total_ventas * 0.15) AS impuesto_estimado FROM (SELECT p.categoria, SUM(d.cantidad * d.precio_unitario) AS total_ventas FROM productos p JOIN detalles_pedidos d ON p.id = d.id_producto GROUP BY p.categoria) AS sub WHERE sub.total_ventas > 5000;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsScalarSubqueriesInsideProjectionList() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT e.nombre AS empleado, e.salario, (SELECT MAX(salario) FROM empleados) AS salario_maximo_empresa, e.salario - (SELECT AVG(salario) FROM empleados) AS desviacion_promedio FROM empleados e;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsHavingWithNestedDerivedTable() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT id_departamento, SUM(salario) AS total_departamento FROM empleados GROUP BY id_departamento HAVING SUM(salario) > (SELECT AVG(total_salario) FROM (SELECT SUM(salario) AS total_salario FROM empleados GROUP BY id_departamento) AS subconsulta_promedio);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsAllQuantifierWithNestedSelect() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT nombre, salario, puesto FROM empleados WHERE salario > ALL (SELECT salario FROM empleados WHERE puesto = 'Reclutador');"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsWithCtesAndNestedSelects() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "WITH ResumenVentas AS (SELECT id_vendedor, COUNT(id_pedido) AS total_pedidos, SUM(monto) AS monto_total FROM ventas GROUP BY id_vendedor), MejoresVendedores AS (SELECT id_vendedor FROM ResumenVentas WHERE monto_total > (SELECT AVG(monto_total) FROM ResumenVentas)) SELECT v.nombre, r.total_pedidos, r.monto_total FROM empleados v JOIN ResumenVentas r ON v.id = r.id_vendedor WHERE v.id IN (SELECT id_vendedor FROM MejoresVendedores);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsInnerJoinWithAliasesAndOnCondition() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT id, nombre FROM clientes c INNER JOIN pedidos p ON c.id = p.id_cliente;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlAcceptsJoinUsingClause() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT c.nombre, p.monto FROM clientes c INNER JOIN pedidos p USING (id_cliente);"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void sqlRejectsOriginalTableNameAfterAlias() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT clientes.nombre FROM clientes c INNER JOIN pedidos p ON c.id = p.id_cliente;"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("fue renombrada como 'c'")));
    }

    @Test
    void sqlAcceptsMultipleJoinTypesAndDateFilter() throws Exception {
        String request = """
                {
                  "engine": "MYSQL",
                  "query": "SELECT c.nombre AS cliente, p.id AS factura, prod.nombre AS producto FROM clientes c LEFT JOIN pedidos p ON c.id = p.id_cliente INNER JOIN detalles d ON p.id = d.id_pedido INNER JOIN productos prod ON d.id_producto = prod.id WHERE p.fecha > '2026-01-01';"
                }
                """;

        mockMvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }
}

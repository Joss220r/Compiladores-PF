package com.compiladores.sqlplatform.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
    void validateQueryRejectsUnknownTable() throws Exception {
        String request = """
                {
                  "engine": "POSTGRESQL",
                  "query": "SELECT nombre FROM clientes;"
                }
                """;

        mockMvc.perform(post("/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].message", containsString("tabla 'clientes'")))
                .andExpect(jsonPath("$.semanticResult.valid").value(false));
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
                .andExpect(jsonPath("$.errors[0].message", containsString("segundos numericos")));
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
}

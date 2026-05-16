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
    void validateQueryReturnsMockValidationResult() throws Exception {
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
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.engine").value("SQL"))
                .andExpect(jsonPath("$.message", containsString("Query validada con mocks")))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.tokens").isArray())
                .andExpect(jsonPath("$.ast.type").value("MockQuery"))
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
                .andExpect(jsonPath("$.errors[0]", containsString("query")));
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
                .andExpect(jsonPath("$.errors[0]", containsString("engine")));
    }
}

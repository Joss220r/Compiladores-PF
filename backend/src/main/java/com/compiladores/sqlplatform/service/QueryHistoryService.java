package com.compiladores.sqlplatform.service;

import com.compiladores.sqlplatform.dto.EngineStatsResponse;
import com.compiladores.sqlplatform.dto.HistoryItemResponse;
import com.compiladores.sqlplatform.dto.HistoryStatsResponse;
import com.compiladores.sqlplatform.dto.QueryValidationResponse;
import com.compiladores.sqlplatform.dto.TopErrorResponse;
import com.compiladores.sqlplatform.model.QueryHistory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class QueryHistoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryHistoryService.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ObjectMapper objectMapper;
    private final List<QueryHistory> memoryHistory = new CopyOnWriteArrayList<>();
    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final String dbSsl;
    private volatile boolean tableChecked;

    public QueryHistoryService(
            ObjectMapper objectMapper,
            @Value("${app.history.db-host:}") String dbHost,
            @Value("${app.history.db-port:5432}") String dbPort,
            @Value("${app.history.db-name:}") String dbName,
            @Value("${app.history.db-user:}") String dbUser,
            @Value("${app.history.db-password:}") String dbPassword,
            @Value("${app.history.db-ssl:require}") String dbSsl
    ) {
        this.objectMapper = objectMapper;
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.dbSsl = dbSsl;
    }

    @PostConstruct
    void initialize() {
        if (!isDatabaseConfigured()) {
            return;
        }
        try {
            ensureTable();
        } catch (Exception exception) {
            LOGGER.warn("No se pudo preparar la tabla de historial de consultas", exception);
        }
    }

    public void saveAnalysis(String engine, String originalQuery, QueryValidationResponse response) {
        if (response == null) {
            return;
        }

        QueryHistory history = new QueryHistory(
                UUID.randomUUID(),
                engine,
                originalQuery,
                response.isSuccess(),
                response.getErrors() == null ? 0 : response.getErrors().size(),
                response.getWarnings() == null ? 0 : response.getWarnings().size(),
                toJson(response.getErrors()),
                toJson(response.getWarnings()),
                toJson(response.getSuggestions()),
                LocalDateTime.now()
        );

        memoryHistory.add(history);
        if (!isDatabaseConfigured()) {
            return;
        }

        try {
            ensureTable();
            insert(history);
        } catch (Exception exception) {
            LOGGER.warn("No se pudo guardar historial de consulta", exception);
        }
    }

    public List<HistoryItemResponse> findHistory(Integer limit, String engine, Boolean success) {
        List<QueryHistory> history = readHistory(limit, engine, success);
        return history.stream()
                .map(this::toItem)
                .toList();
    }

    public HistoryStatsResponse getStats() {
        List<QueryHistory> history = readAllHistory();
        long total = history.size();
        long valid = history.stream().filter(QueryHistory::isSuccess).count();
        long invalid = total - valid;
        long warningTotal = history.stream().mapToLong(QueryHistory::getWarningCount).sum();
        List<EngineStatsResponse> byEngine = history.stream()
                .collect(Collectors.groupingBy(QueryHistory::getEngine))
                .entrySet()
                .stream()
                .map(entry -> {
                    long engineTotal = entry.getValue().size();
                    long engineValid = entry.getValue().stream().filter(QueryHistory::isSuccess).count();
                    return new EngineStatsResponse(entry.getKey(), engineTotal, engineValid, engineTotal - engineValid);
                })
                .sorted(Comparator.comparing(EngineStatsResponse::getTotal).reversed())
                .toList();
        String mostUsedEngine = byEngine.isEmpty() ? null : byEngine.get(0).getEngine();

        return new HistoryStatsResponse(
                true,
                total,
                valid,
                invalid,
                warningTotal,
                mostUsedEngine,
                byEngine,
                List.<TopErrorResponse>of()
        );
    }

    public void clearHistory() {
        memoryHistory.clear();
        if (!isDatabaseConfigured()) {
            return;
        }

        try {
            ensureTable();
            try (Connection connection = openConnection();
                    PreparedStatement statement = connection.prepareStatement("DELETE FROM query_history")) {
                statement.executeUpdate();
            }
        } catch (Exception exception) {
            LOGGER.warn("No se pudo limpiar historial de consultas", exception);
        }
    }

    private List<QueryHistory> readHistory(Integer limit, String engine, Boolean success) {
        if (isDatabaseConfigured()) {
            try {
                ensureTable();
                return select(limit, engine, success);
            } catch (Exception exception) {
                LOGGER.warn("No se pudo leer historial de consultas desde PostgreSQL", exception);
            }
        }

        int safeLimit = normalizeLimit(limit);
        return memoryHistory.stream()
                .filter(item -> engine == null || engine.isBlank() || item.getEngine().equalsIgnoreCase(engine))
                .filter(item -> success == null || item.isSuccess() == success)
                .sorted(Comparator.comparing(QueryHistory::getCreatedAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    private List<QueryHistory> readAllHistory() {
        if (isDatabaseConfigured()) {
            try {
                ensureTable();
                return selectAll();
            } catch (Exception exception) {
                LOGGER.warn("No se pudo leer estadisticas de historial desde PostgreSQL", exception);
            }
        }

        return memoryHistory.stream()
                .sorted(Comparator.comparing(QueryHistory::getCreatedAt).reversed())
                .toList();
    }

    private List<QueryHistory> selectAll() throws Exception {
        String sql = """
                SELECT id, engine, original_query, success, error_count, warning_count,
                       errors::text, warnings::text, suggestions::text, created_at
                FROM query_history
                ORDER BY created_at DESC
                """;
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<QueryHistory> items = new ArrayList<>();
            while (resultSet.next()) {
                items.add(mapRow(resultSet));
            }
            return items;
        }
    }

    private List<QueryHistory> select(Integer limit, String engine, Boolean success) throws Exception {
        StringBuilder sql = new StringBuilder("""
                SELECT id, engine, original_query, success, error_count, warning_count,
                       errors::text, warnings::text, suggestions::text, created_at
                FROM query_history
                """);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (engine != null && !engine.isBlank()) {
            conditions.add("engine = ?");
            params.add(engine);
        }
        if (success != null) {
            conditions.add("success = ?");
            params.add(success);
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(normalizeLimit(limit));

        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < params.size(); index++) {
                statement.setObject(index + 1, params.get(index));
            }

            List<QueryHistory> items = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(mapRow(resultSet));
                }
            }
            return items;
        }
    }

    private void insert(QueryHistory history) throws Exception {
        String sql = """
                INSERT INTO query_history (
                    id, engine, original_query, success, error_count, warning_count,
                    errors, warnings, suggestions, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """;
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, history.getId());
            statement.setString(2, history.getEngine());
            statement.setString(3, history.getOriginalQuery());
            statement.setBoolean(4, history.isSuccess());
            statement.setInt(5, history.getErrorCount());
            statement.setInt(6, history.getWarningCount());
            statement.setString(7, history.getErrorsJson());
            statement.setString(8, history.getWarningsJson());
            statement.setString(9, history.getSuggestionsJson());
            statement.setTimestamp(10, Timestamp.valueOf(history.getCreatedAt()));
            statement.executeUpdate();
        }
    }

    private void ensureTable() throws Exception {
        if (tableChecked) {
            return;
        }
        List<String> statements = List.of(
                """
                CREATE TABLE IF NOT EXISTS query_history (
                    id UUID PRIMARY KEY,
                    engine VARCHAR(50) NOT NULL,
                    original_query TEXT NOT NULL,
                    success BOOLEAN NOT NULL,
                    error_count INT NOT NULL DEFAULT 0,
                    warning_count INT NOT NULL DEFAULT 0,
                    errors JSONB,
                    warnings JSONB,
                    suggestions JSONB,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_query_history_created_at ON query_history (created_at DESC)",
                "CREATE INDEX IF NOT EXISTS idx_query_history_engine ON query_history (engine)",
                "CREATE INDEX IF NOT EXISTS idx_query_history_success ON query_history (success)"
        );
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            tableChecked = true;
        }
    }

    private QueryHistory mapRow(ResultSet resultSet) throws Exception {
        return new QueryHistory(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("engine"),
                resultSet.getString("original_query"),
                resultSet.getBoolean("success"),
                resultSet.getInt("error_count"),
                resultSet.getInt("warning_count"),
                resultSet.getString("errors"),
                resultSet.getString("warnings"),
                resultSet.getString("suggestions"),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private HistoryItemResponse toItem(QueryHistory history) {
        return new HistoryItemResponse(
                history.getId(),
                history.getEngine(),
                history.getOriginalQuery(),
                history.isSuccess(),
                history.getErrorCount(),
                history.getWarningCount(),
                history.getCreatedAt()
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("No se pudo convertir historial a JSON", exception);
            return "[]";
        }
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl(), dbUser, dbPassword);
    }

    private String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName + "?sslmode=" + dbSsl;
    }

    private boolean isDatabaseConfigured() {
        return !dbHost.isBlank() && !dbName.isBlank() && !dbUser.isBlank() && !dbPassword.isBlank();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}

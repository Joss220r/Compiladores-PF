package com.compiladores.sqlplatform.service.semantic;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.catalog.source", havingValue = "postgres")
public class PostgresCatalogService implements CatalogService {

    private static final String FIND_TABLE_SQL = """
            SELECT t.table_name, c.column_name, c.data_type
            FROM schema_tables t
            LEFT JOIN schema_columns c ON c.table_id = t.id
            WHERE UPPER(t.engine) IN (?, ?)
              AND LOWER(t.table_name) = LOWER(?)
            ORDER BY c.id
            """;

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresCatalogService(
            @Value("${app.catalog.jdbc-url}") String jdbcUrl,
            @Value("${app.catalog.username}") String username,
            @Value("${app.catalog.password}") String password
    ) {
        this.jdbcUrl = normalizeJdbcUrl(jdbcUrl);
        this.username = username;
        this.password = password;
    }

    @Override
    public Optional<TableDefinition> findTable(DatabaseEngine engine, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return Optional.empty();
        }

        validateConfiguration();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(FIND_TABLE_SQL)) {
            List<String> engineCandidates = engineCandidates(engine);
            statement.setString(1, engineCandidates.get(0));
            statement.setString(2, engineCandidates.get(1));
            statement.setString(3, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {
                String resolvedTableName = null;
                Map<String, ColumnDefinition> columns = new LinkedHashMap<>();

                while (resultSet.next()) {
                    resolvedTableName = resultSet.getString("table_name");
                    String columnName = resultSet.getString("column_name");
                    String dataType = resultSet.getString("data_type");
                    if (columnName != null && dataType != null) {
                        columns.put(columnName.toLowerCase(Locale.ROOT), new ColumnDefinition(columnName, normalizeDataType(dataType)));
                    }
                }

                if (resolvedTableName == null) {
                    return Optional.empty();
                }

                return Optional.of(new TableDefinition(resolvedTableName, Map.copyOf(columns)));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo consultar el catalogo PostgreSQL.", exception);
        }
    }

    private List<String> engineCandidates(DatabaseEngine engine) {
        List<String> candidates = new ArrayList<>();
        candidates.add(engine.name());

        if (engine == DatabaseEngine.SQL) {
            candidates.add(DatabaseEngine.POSTGRESQL.name());
        } else {
            candidates.add(engine.name());
        }

        return candidates;
    }

    private void validateConfiguration() {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("DATABASE_URL es obligatorio cuando CATALOG_SOURCE=postgres.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("DATABASE_USERNAME es obligatorio cuando CATALOG_SOURCE=postgres.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("DATABASE_PASSWORD es obligatorio cuando CATALOG_SOURCE=postgres.");
        }
    }

    private String normalizeJdbcUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.startsWith("jdbc:postgresql://")) {
            return value;
        }
        if (value.startsWith("postgresql://")) {
            return "jdbc:" + value;
        }
        return value;
    }

    private String normalizeDataType(String dataType) {
        String normalized = dataType.toUpperCase(Locale.ROOT);
        if ("INT".equals(normalized)) {
            return "INTEGER";
        }
        return normalized;
    }
}

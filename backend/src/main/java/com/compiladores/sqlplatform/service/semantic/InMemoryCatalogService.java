package com.compiladores.sqlplatform.service.semantic;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.catalog.source", havingValue = "memory", matchIfMissing = true)
public class InMemoryCatalogService implements CatalogService {

    private final Map<String, TableDefinition> relationalCatalog;

    public InMemoryCatalogService() {
        this.relationalCatalog = Map.of(
                "usuarios", table("usuarios",
                        column("id", "INTEGER"),
                        column("nombre", "VARCHAR"),
                        column("edad", "INTEGER")
                ),
                "productos", table("productos",
                        column("id", "INTEGER"),
                        column("nombre", "VARCHAR"),
                        column("precio", "DECIMAL"),
                        column("categoria", "VARCHAR")
                )
        );
    }

    @Override
    public Optional<TableDefinition> findTable(DatabaseEngine engine, String tableName) {
        if (tableName == null || !isRelational(engine)) {
            return Optional.empty();
        }

        return Optional.ofNullable(relationalCatalog.get(tableName.toLowerCase(Locale.ROOT)));
    }

    private boolean isRelational(DatabaseEngine engine) {
        return engine == DatabaseEngine.SQL
                || engine == DatabaseEngine.POSTGRESQL
                || engine == DatabaseEngine.MYSQL
                || engine == DatabaseEngine.SQL_SERVER;
    }

    private TableDefinition table(String name, ColumnDefinition... columns) {
        Map<String, ColumnDefinition> byName = new LinkedHashMap<>();
        for (ColumnDefinition column : columns) {
            byName.put(column.name().toLowerCase(Locale.ROOT), column);
        }
        return new TableDefinition(name, Map.copyOf(byName));
    }

    private ColumnDefinition column(String name, String dataType) {
        return new ColumnDefinition(name, dataType);
    }
}

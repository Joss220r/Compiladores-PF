package com.compiladores.sqlplatform.service.semantic;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record TableDefinition(String name, Map<String, ColumnDefinition> columns) {

    public Optional<ColumnDefinition> findColumn(String columnName) {
        if (columnName == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(columns.get(columnName.toLowerCase(Locale.ROOT)));
    }
}

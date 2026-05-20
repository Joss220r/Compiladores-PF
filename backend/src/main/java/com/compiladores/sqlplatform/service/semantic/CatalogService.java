package com.compiladores.sqlplatform.service.semantic;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import java.util.Optional;

public interface CatalogService {

    Optional<TableDefinition> findTable(DatabaseEngine engine, String tableName);
}

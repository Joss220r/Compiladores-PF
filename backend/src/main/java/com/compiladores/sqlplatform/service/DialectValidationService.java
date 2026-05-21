package com.compiladores.sqlplatform.service;

import com.compiladores.sqlplatform.model.DatabaseEngine;
import com.compiladores.sqlplatform.model.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DialectValidationService {

    public List<ValidationIssue> validate(String query, DatabaseEngine engine) {
        List<ValidationIssue> issues = new ArrayList<>();
        String trimmed = query == null ? "" : query.stripLeading();
        String upper = trimmed.toUpperCase(Locale.ROOT);

        validateWrongEngine(trimmed, upper, engine, issues);
        validateSqlDialect(upper, engine, issues);

        return issues;
    }

    private void validateWrongEngine(String trimmed, String upper, DatabaseEngine engine, List<ValidationIssue> issues) {
        boolean looksSql = upper.matches("^(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP)\\b.*");
        boolean looksMongo = trimmed.matches("(?is)^db\\.[a-zA-Z_][\\w]*\\.[a-zA-Z_][\\w]*\\s*\\(.*");
        boolean looksRedis = upper.matches("^(GET|SET|DEL|EXISTS|EXPIRE|TTL|HSET|HGET|HGETALL|LPUSH|RPUSH|LPOP|RPOP|SADD|SMEMBERS)\\b.*");

        if (engine == DatabaseEngine.MONGODB && looksSql) {
            issues.add(error("Esta consulta parece SQL, pero el motor seleccionado es MongoDB.", 1, 1, firstWord(trimmed)));
        }
        if (engine == DatabaseEngine.REDIS && looksSql) {
            issues.add(error("Esta consulta parece SQL, pero el motor seleccionado es Redis.", 1, 1, firstWord(trimmed)));
        }
        if (isRelational(engine) && looksMongo) {
            issues.add(error("Esta consulta parece MongoDB, pero el motor seleccionado es " + engine.name() + ".", 1, 1, "db."));
        }
        if (isRelational(engine) && looksRedis && !upper.startsWith("SELECT")) {
            issues.add(error("Esta consulta parece Redis, pero el motor seleccionado es " + engine.name() + ".", 1, 1, firstWord(trimmed)));
        }
    }

    private void validateSqlDialect(String upper, DatabaseEngine engine, List<ValidationIssue> issues) {
        if (!isRelational(engine)) {
            return;
        }

        if (engine == DatabaseEngine.SQL_SERVER && upper.matches("(?is).*\\bLIMIT\\s+\\d+.*")) {
            issues.add(error("LIMIT no es valido para SQL Server. Usa TOP u OFFSET FETCH.", 1, upper.indexOf("LIMIT") + 1, "LIMIT"));
        }
        if (engine == DatabaseEngine.POSTGRESQL && upper.contains("AUTO_INCREMENT")) {
            issues.add(error("AUTO_INCREMENT no es valido en PostgreSQL. Usa SERIAL o GENERATED.", 1, upper.indexOf("AUTO_INCREMENT") + 1, "AUTO_INCREMENT"));
        }
        if (engine == DatabaseEngine.SQL_SERVER && upper.contains("AUTO_INCREMENT")) {
            issues.add(error("AUTO_INCREMENT no es valido en SQL Server. Usa IDENTITY(1,1).", 1, upper.indexOf("AUTO_INCREMENT") + 1, "AUTO_INCREMENT"));
        }
        if ((engine == DatabaseEngine.MYSQL || engine == DatabaseEngine.SQL_SERVER) && upper.matches("(?is).*\\bSERIAL\\b.*")) {
            issues.add(error("SERIAL no es valido para " + engine.name() + ".", 1, upper.indexOf("SERIAL") + 1, "SERIAL"));
        }
        if ((engine == DatabaseEngine.MYSQL || engine == DatabaseEngine.POSTGRESQL) && upper.contains("IDENTITY(")) {
            issues.add(error("IDENTITY(1,1) no es valido para " + engine.name() + ".", 1, upper.indexOf("IDENTITY") + 1, "IDENTITY"));
        }
        if (engine == DatabaseEngine.MYSQL && upper.matches("(?is).*\\bTOP\\s+\\d+.*")) {
            issues.add(error("TOP no es valido para MySQL. Usa LIMIT.", 1, upper.indexOf("TOP") + 1, "TOP"));
        }
        if (engine == DatabaseEngine.POSTGRESQL && upper.matches("(?is).*\\bTOP\\s+\\d+.*")) {
            issues.add(error("TOP no es valido para PostgreSQL. Usa LIMIT.", 1, upper.indexOf("TOP") + 1, "TOP"));
        }
        if (engine == DatabaseEngine.SQL && (upper.contains("AUTO_INCREMENT") || upper.matches("(?is).*\\bSERIAL\\b.*")
                || upper.contains("IDENTITY(") || upper.matches("(?is).*\\bTOP\\s+\\d+.*"))) {
            issues.add(ValidationIssue.warning("DIALECT", "La consulta usa sintaxis especifica de un motor SQL.", 1, 1, firstWord(upper)));
        }
    }

    private boolean isRelational(DatabaseEngine engine) {
        return engine == DatabaseEngine.SQL
                || engine == DatabaseEngine.MYSQL
                || engine == DatabaseEngine.POSTGRESQL
                || engine == DatabaseEngine.SQL_SERVER;
    }

    private ValidationIssue error(String message, int line, int column, String fragment) {
        return ValidationIssue.error("DIALECT", message, line, Math.max(column, 1), fragment);
    }

    private String firstWord(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return query.trim().split("\\s+")[0];
    }
}

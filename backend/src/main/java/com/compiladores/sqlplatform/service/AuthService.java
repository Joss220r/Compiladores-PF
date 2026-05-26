package com.compiladores.sqlplatform.service;

import com.compiladores.sqlplatform.dto.LoginResponse;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final String dbSsl;
    private volatile boolean tableChecked;

    public AuthService(
            @Value("${app.history.db-host:}") String dbHost,
            @Value("${app.history.db-port:5432}") String dbPort,
            @Value("${app.history.db-name:}") String dbName,
            @Value("${app.history.db-user:}") String dbUser,
            @Value("${app.history.db-password:}") String dbPassword,
            @Value("${app.history.db-ssl:require}") String dbSsl
    ) {
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
            LOGGER.warn("No se pudo preparar la tabla de usuarios de login", exception);
        }
    }

    public LoginResponse login(String username, String password) {
        if (!isDatabaseConfigured()) {
            return new LoginResponse(false, "Login no configurado. Revisa variables DB_* en Render.", null, null, null);
        }

        try {
            ensureTable();
            return findUser(username, password);
        } catch (Exception exception) {
            LOGGER.warn("No se pudo validar login", exception);
            return new LoginResponse(false, "No se pudo validar el login.", null, null, null);
        }
    }

    private LoginResponse findUser(String username, String password) throws Exception {
        String sql = """
                SELECT username, display_name, role, password_hash
                FROM app_users
                WHERE lower(username) = lower(?)
                """;
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return invalidLogin();
                }

                String expectedHash = resultSet.getString("password_hash");
                if (!sha256(password).equalsIgnoreCase(expectedHash)) {
                    return invalidLogin();
                }

                return new LoginResponse(
                        true,
                        "Login correcto.",
                        resultSet.getString("username"),
                        resultSet.getString("display_name"),
                        resultSet.getString("role")
                );
            }
        }
    }

    private LoginResponse invalidLogin() {
        return new LoginResponse(false, "Usuario o contrasena incorrectos.", null, null, null);
    }

    private void ensureTable() throws Exception {
        if (tableChecked) {
            return;
        }
        List<String> statements = List.of(
                """
                CREATE TABLE IF NOT EXISTS app_users (
                    id UUID PRIMARY KEY,
                    username VARCHAR(80) UNIQUE NOT NULL,
                    password_hash VARCHAR(128) NOT NULL,
                    display_name VARCHAR(120),
                    role VARCHAR(40) NOT NULL DEFAULT 'USER',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_app_users_username ON app_users (lower(username))"
        );
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            tableChecked = true;
        }
    }

    private String sha256(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
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
}

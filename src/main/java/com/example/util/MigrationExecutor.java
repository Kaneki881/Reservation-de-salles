package com.example.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Runs classpath SQL migrations before the JPA factory validates the schema. */
public class MigrationExecutor {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void executeAll() throws SQLException, IOException {
        executeMigration("db/migration/v1_0_initial.sql");
        executeMigration("db/migration/v2_0_add_description.sql");
    }

    public void executeMigration(String resourcePath) throws SQLException, IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) throw new IOException("Migration introuvable : " + resourcePath);
        try (InputStream is = stream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            StringBuilder sql = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String withoutComment = line.split("--", 2)[0];
                sql.append(withoutComment).append('\n');
            }
            for (String command : sql.toString().split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }
        }
    }
}

package com.github.qmulda.dependencyanalyser.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import org.flywaydb.core.Flyway;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * ProjectService for managing H2 database connections.
 * This service handles the lifecycle of the local H2 database used to persist
 * dependency scan results. It provides methods to obtain connections and initialize
 * the database schema.
 * Database location: ~/.dependencyanalyser/localdb
 */
@Service(Service.Level.PROJECT)
public final class DatabaseService {

    private static final String DB_URL = "jdbc:h2:~/.dependencyanalyser/localdb";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    
    private static final Logger logger = Logger.getInstance(DatabaseService.class);
    
    private final Project project;

    public DatabaseService(Project project) {
        this.project = project;
        logger.info("DatabaseService initialized for project: " + project.getName());
        initializeDatabase();
    }

    public static DatabaseService getInstance(Project project) {
        return project.getService(DatabaseService.class);
    }

    /**
     * Initialize the H2 database and ensure the database directory exists.
     */
    private void initializeDatabase() {
        try {
            // Ensure the database directory exists
            String userHome = System.getProperty("user.home");
            File dbDir = new File(userHome, ".dependencyanalyser");
            if (!dbDir.exists()) {
                if (dbDir.mkdirs()) {
                    logger.info("Created database directory: " + dbDir.getAbsolutePath());
                }
            }

            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL(DB_URL);
            ds.setUser(DB_USER);
            ds.setPassword(DB_PASSWORD);

            Flyway flyway = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            logger.info("Flyway migration completed successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Get a new H2 database connection.
     *
     * @return A SQL Connection to the H2 database
     * @throws SQLException if connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /**
     * Execute a SQL query and return results as a list of maps.
     *
     * @param query The SQL query to execute
     * @return List of rows, each row represented as a map of column names to values
     */
    public List<Map<String, Object>> executeQuery(String query) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            Connection connection = getConnection();
            try (connection) {
                Statement statement = connection.createStatement();
                try (statement) {
                    ResultSet resultSet = statement.executeQuery(query);
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    int columnCount = metadata.getColumnCount();

                    while (resultSet.next()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metadata.getColumnName(i);
                            Object value = resultSet.getObject(i);
                            row.put(columnName, value != null ? value : "");
                        }
                        results.add(row);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to execute query: " + query, e);
            throw new RuntimeException("Query execution failed", e);
        }
        return results;
    }

    /**
     * Execute a SQL update/insert/delete statement.
     *
     * @param sql The SQL statement to execute
     * @return Number of rows affected
     */
    public int executeUpdate(String sql) {
        try {
            Connection connection = getConnection();
            try (connection) {
                Statement statement = connection.createStatement();
                try (statement) {
                    return statement.executeUpdate(sql);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to execute update: " + sql, e);
            throw new RuntimeException("Update execution failed", e);
        }
    }
}


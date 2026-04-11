package com.github.qmulda.dependencyanalyser.services;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

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
    private org.h2.jdbcx.JdbcDataSource dataSource;

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

            dataSource = new org.h2.jdbcx.JdbcDataSource();
            dataSource.setURL(DB_URL);
            dataSource.setUser(DB_USER);
            dataSource.setPassword(DB_PASSWORD);

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
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
        return dataSource.getConnection();
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
            System.out.println("Attempting to get connection for query: " + query);
            Connection connection = getConnection();
            System.out.println("Got connection");
            try (connection) {
                Statement statement = connection.createStatement();
                System.out.println("Created statement");
                try (statement) {
                    ResultSet resultSet = statement.executeQuery(query);
                    System.out.println("Executed query");
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    System.out.println("Got metadata");
                    int columnCount = metadata.getColumnCount();
                    System.out.println("Column count: " + columnCount);

                    while (resultSet.next()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metadata.getColumnName(i);
                            Object value = resultSet.getObject(i);
                            row.put(columnName, value != null ? value : "");
                        }
                        results.add(row);
                    }
                    System.out.println("Processed result set, rows fetched: " + results.size());
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


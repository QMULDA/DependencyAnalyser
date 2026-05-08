package com.github.qmulda.dependencyanalyser.services;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;

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

    private static final String DB_URL = "jdbc:h2:~/.dependencyanalyser/localdb;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static final Logger logger = Logger.getInstance(DatabaseService.class);

    private final Project project;
    private JdbcDataSource dataSource;

    public DatabaseService(Project project) {
        this.project = project;
        logger.info("DatabaseService initialized for project: " + project.getName());
        initializeDatabase();
    }

    public static DatabaseService getInstance(Project project) {
        return project.getService(DatabaseService.class);
    }

    /**
     * Initialise the H2 database and ensure the database directory exists.
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

            Flyway flyway = Flyway.configure(DatabaseService.class.getClassLoader())
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();

            // Debug: show which migration files Flyway has resolved before running
            for (org.flywaydb.core.api.MigrationInfo info : flyway.info().all()) {
                System.out.println("Flyway migration found: " + info.getScript()
                        + " [state=" + info.getState() + "]");
            }

            org.flywaydb.core.api.output.MigrateResult result = flyway.migrate();
            System.out.println("Flyway migrations applied: " + result.migrationsExecuted);
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
     */
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (Exception e) {
            logger.error("Failed to get connection: " + e.getMessage());
            throw new RuntimeException("Query execution failed", e);
        }
    }

    public Statement createStatement(Connection connection) {
        try {
            return connection.createStatement();
        } catch (Exception e) {
            logger.error("Failed to create statement: " + e.getMessage());
            throw new RuntimeException("Query execution failed", e);
        }
    }

    /**
     * Execute a SQL query and return results as a list of maps.
     *
     * @param query The SQL query to execute
     * @return List of rows, each row represented as a map of column names to values
     */
    public List<Map<String, Object>> executeQuery(String query) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();

        System.out.println("Attempting to get connection to execute query...");
        Connection connection = getConnection();
        System.out.println("Got connection.");

        Statement statement = createStatement(connection);
        System.out.println("Created statement");

        try {
            ResultSet resultSet = statement.executeQuery(query);
            System.out.println("Executed query: " + query);
            ResultSetMetaData metadata = resultSet.getMetaData();
            System.out.println("Got metadata");
            int columnCount = metadata.getColumnCount();
            System.out.println("Column count: " + columnCount);

            while (resultSet.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metadata.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
            System.out.println("Processed result set, rows fetched: " + results.size());
        } catch (Exception e) {
            System.out.println("Failed to execute query: " + e);
        }
        return results;
    }

    /**
     * Execute a parameterized SQL update/insert/delete statement using a PreparedStatement.
     *
     * @param sql    The SQL statement with ? placeholders
     * @param params Values to bind to the placeholders in order
     */
    public void executeUpdate(String sql, Object... params) throws SQLException {
        System.out.println("Attempting to get connection to execute update...");
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        }
    }

    /**
     * Execute a parameterized INSERT statement and return the generated auto-increment key.
     *
     * @param sql    The INSERT statement with ? placeholders
     * @param params Values to bind to the placeholders in order
     * @return The generated primary key value
     */
    public int executeInsertGetKey(String sql, Object... params) throws SQLException {
        System.out.println("Attempting to get connection to execute insert...");
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert succeeded but no generated key returned");
            }
        }
    }

    /**
     * Execute a SQL update/insert/delete statement.
     *
     * @param sql The SQL statement to execute
     * @return Number of rows affected
     */
    public void executeUpdate(String sql) {
        try {
            System.out.println("Attempting to get connection to execute update...");
            Connection connection = getConnection();
            try (connection) {
                Statement statement = connection.createStatement();
                try (statement) {
                    statement.executeUpdate(sql);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to execute update: " + sql, e);
            throw new RuntimeException("Update execution failed", e);
        }
    }
}

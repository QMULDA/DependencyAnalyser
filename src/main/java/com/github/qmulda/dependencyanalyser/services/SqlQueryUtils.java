package com.github.qmulda.dependencyanalyser.services;

import com.github.qmulda.dependencyanalyser.risk.RiskTier;
import com.github.qmulda.dependencyanalyser.scan.ScannedDependency;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Collection of SQL statements to deal with H2 db. Used with DatabaseService.
 */
@Service(Service.Level.PROJECT)
public final class SqlQueryUtils {

    private final DatabaseService db;

    public SqlQueryUtils(Project project) {
        this.db = DatabaseService.getInstance(project);
    }

    public static SqlQueryUtils getInstance(Project project) {
        return project.getService(SqlQueryUtils.class);
    }

    /**
     * Persists a complete scan in a single transaction: upserts the project, inserts the scan
     * row, and inserts all dependencies with their computed risk tiers.
     *
     * @return the generated scan_id
     */
    public int persistScan(String projectId, String name, String path,
                           List<ScannedDependency> deps) throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                upsertProjectConn(conn, projectId, name, path);
                int scanId = insertScanConn(conn, projectId);
                for (ScannedDependency dep : deps) {
                    int libId = upsertLibraryConn(conn, dep.groupId, dep.artifactId);
                    int versionId = upsertVersionConn(conn, libId, dep.version, dep.riskTier);
                    insertDependencyConn(conn, scanId, versionId, dep.scope, dep.relation);
                }
                updateLastScannedConn(conn, projectId);
                conn.commit();
                return scanId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Connection-scoped helpers, used by persistScan
    private void upsertProjectConn(Connection conn, String projectId, String name,
                                   String path) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT project_id FROM project WHERE project_id = ?")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return; // already exists
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO project (project_id, name, path) VALUES (?, ?, ?)")) {
            ps.setString(1, projectId);
            ps.setString(2, name);
            ps.setString(3, path);
            ps.executeUpdate();
        }
    }

    private int insertScanConn(Connection conn, String projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO scan (project_id) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, projectId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert into scan succeeded but no generated key returned");
            }
        }
    }

    private int upsertLibraryConn(Connection conn, String groupId,
                                  String artifactId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT library_id FROM library WHERE group_id = ? AND artifact_id = ?")) {
            ps.setString(1, groupId);
            ps.setString(2, artifactId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("library_id");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO library (group_id, artifact_id) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, groupId);
            ps.setString(2, artifactId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert into library succeeded but no generated key returned");
            }
        }
    }

    private int upsertVersionConn(Connection conn, int libraryId, String versionString,
                                  RiskTier riskTier) throws SQLException {
        String tierName = riskTier != null ? riskTier.name() : null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT version_id FROM version WHERE library_id = ? AND version_string = ?")) {
            ps.setInt(1, libraryId);
            ps.setString(2, versionString);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int versionId = rs.getInt("version_id");
                    // Risk tiers shift between scans as new versions are released — always update
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE version SET risk_tier = ? WHERE version_id = ?")) {
                        upd.setString(1, tierName);
                        upd.setInt(2, versionId);
                        upd.executeUpdate();
                    }
                    return versionId;
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO version (library_id, version_string, risk_tier) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, libraryId);
            ps.setString(2, versionString);
            ps.setString(3, tierName);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert into version succeeded but no generated key returned");
            }
        }
    }

    private void insertDependencyConn(Connection conn, int scanId, int versionId,
                                      String scope, String relation) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO dependency (scan_id, version_id, scope, relation) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, scanId);
            ps.setInt(2, versionId);
            ps.setString(3, scope);
            ps.setString(4, relation);
            ps.executeUpdate();
        }
    }

    private void updateLastScannedConn(Connection conn, String projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE project SET last_scanned = CURRENT_TIMESTAMP WHERE project_id = ?")) {
            ps.setString(1, projectId);
            ps.executeUpdate();
        }
    }

    // old methods

    /** Returns the existing project_id for the given projectId, or inserts a new row and returns it. */
    public String upsertProject(String projectId, String name, String path) throws SQLException {
        System.out.println("Attempting to get connection to upsert project...");
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT project_id FROM project WHERE project_id = ?")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("project_id");
            } catch (Exception e){
                System.out.println("Error upserting project: " + e.getMessage());
                throw e;
            }
        }
        db.executeUpdate(
                "INSERT INTO project (project_id, name, path) VALUES (?, ?, ?)", projectId, name, path);
        return projectId;
    }

    /** Updates last_scanned to the current timestamp. Call this after the scan loop completes. */
    public void updateLastScanned(String projectId) throws SQLException {
        db.executeUpdate(
                "UPDATE project SET last_scanned = CURRENT_TIMESTAMP WHERE project_id = ?", projectId);
    }

    /** Inserts a new scan row and returns its generated scan_id. scanned_at is set by the DB default. */
    public int insertScanIntoH2(String projectId) throws SQLException {
        return db.executeInsertGetKey("INSERT INTO scan (project_id) VALUES (?)", projectId);
    }

    /** Returns the existing library_id for (groupId, artifactId), or inserts a new row and returns its generated key. */
    public int upsertLibrary(String groupId, String artifactId) throws SQLException {
        System.out.println("Attempting to get connection to upsert library...");
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT library_id FROM library WHERE group_id = ? AND artifact_id = ?")) {
            ps.setString(1, groupId);
            ps.setString(2, artifactId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("library_id");
            }
        }
        return db.executeInsertGetKey(
                "INSERT INTO library (group_id, artifact_id) VALUES (?, ?)", groupId, artifactId);
    }

    /**
     * Returns the existing version_id for (libraryId, versionString), inserting if absent.
     * Updates risk_tier on existing rows since tiers shift as new versions are released.
     */
    public int upsertVersion(int libraryId, String versionString, RiskTier riskTier) throws SQLException {
        System.out.println("Attempting to get connection to upsert version...");
        String tierName = riskTier != null ? riskTier.name() : null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT version_id FROM version WHERE library_id = ? AND version_string = ?")) {
            ps.setInt(1, libraryId);
            ps.setString(2, versionString);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int versionId = rs.getInt("version_id");
                    db.executeUpdate(
                            "UPDATE version SET risk_tier = ? WHERE version_id = ?",
                            tierName, versionId);
                    return versionId;
                }
            }
        }
        return db.executeInsertGetKey(
                "INSERT INTO version (library_id, version_string, risk_tier) VALUES (?, ?, ?)",
                libraryId, versionString, tierName);
    }

    /** Inserts one dependency row linking a scan to a version. No upsert — each scan produces fresh rows. */
    public void insertDependency(int scanId, int versionId, String scope, String relation)
            throws SQLException {
        db.executeUpdate(
                "INSERT INTO dependency (scan_id, version_id, scope, relation) VALUES (?, ?, ?, ?)",
                scanId, versionId, scope, relation);
    }
}

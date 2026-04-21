package com.github.qmulda.dependencyanalyser.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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

    /** Returns the existing project_id for the given path, or inserts a new row and returns its generated key. */
    public int upsertProject(String name, String path) throws SQLException {
        System.out.println("Attempting to get connection to upsert project...");
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT project_id FROM project WHERE path = ?")) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("project_id");
            }
        }
        return db.executeInsertGetKey("INSERT INTO project (name, path) VALUES (?, ?)", name, path);
    }

    /** Updates last_scanned to the current timestamp. Call this after the scan loop completes. */
    public void updateLastScanned(int projectId) throws SQLException {
        db.executeUpdate(
                "UPDATE project SET last_scanned = CURRENT_TIMESTAMP WHERE project_id = ?", projectId);
    }

    /** Inserts a new scan row and returns its generated scan_id. scanned_at is set by the DB default. */
    public int insertScanIntoH2(int projectId) throws SQLException {
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

    /** Returns the existing version_id for (libraryId, versionString), or inserts a new row and returns its generated key. risk_tier is left NULL until semver scoring is implemented. */
    public int upsertVersion(int libraryId, String versionString) throws SQLException {
        System.out.println("Attempting to get connection to upsert version...");
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT version_id FROM version WHERE library_id = ? AND version_string = ?")) {
            ps.setInt(1, libraryId);
            ps.setString(2, versionString);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("version_id");
            }
        }
        return db.executeInsertGetKey(
                "INSERT INTO version (library_id, version_string, risk_tier) VALUES (?, ?, NULL)",
                libraryId, versionString);
    }

    /** Inserts one dependency row linking a scan to a version. No upsert — each scan produces fresh rows. */
    public void insertDependency(int scanId, int versionId, String scope, String relation)
            throws SQLException {
        db.executeUpdate(
                "INSERT INTO dependency (scan_id, version_id, scope, relation) VALUES (?, ?, ?, ?)",
                scanId, versionId, scope, relation);
    }
}

package com.github.qmulda.dependencyanalyser.dependencyhandler;

import com.github.qmulda.dependencyanalyser.services.SqlQueryUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import deps_dev.v3.Api.Dependencies;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DependencyHandler {
    private static final Logger logger = Logger.getInstance(DependencyHandler.class);

    private final Project project;
    private final Component parent;
    private final DefaultTableModel tableModel;
    private final JBLabel statusLabel;

    public DependencyHandler(Project project, Component parent, DefaultTableModel tableModel, JBLabel statusLabel) {
        this.project = project;
        this.parent = parent;
        this.tableModel = tableModel;
        this.statusLabel = statusLabel;
    }

    public void performScan() {
        System.out.println("performScan() CALLED");
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    System.out.println("Starting dependency scan");
                    tableModel.setRowCount(0);

                    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
                    System.out.println("Is Maven project: " + manager.isMavenizedProject());

                    List<MavenProject> roots = manager.getRootProjects();
                    if (roots.isEmpty()) {
                        JOptionPane.showMessageDialog(parent,
                                "No Maven root project found.", "Scan Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    MavenProject root = roots.get(0);
                    String groupId    = root.getMavenId().getGroupId();
                    String artifactId = root.getMavenId().getArtifactId();
                    String projectId   = sha256Hex(groupId + ":" + artifactId);
                    String name        = root.getName();
                    if (name == null || name.isBlank()) name = artifactId;
                    String path        = root.getDirectory();

                    System.out.println("project_id = " + projectId);
                    System.out.println("name       = " + name);
                    System.out.println("path       = " + path);

                    List<MavenArtifact> allDeps = new ArrayList<>();
                    for (MavenProject mp : manager.getProjects()) {
                        System.out.println("Collecting deps from module: " + mp.getName());
                        allDeps.addAll(mp.getDependencies());
                    }
                    System.out.println("Total dependencies collected: " + allDeps.size());

                    for (MavenArtifact dep : allDeps) {
                        tableModel.addRow(new Object[]{
                                dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                                dep.getScope(), "DIRECT"
                        });
                    }
                    statusLabel.setText("Found " + allDeps.size() + " direct deps. Fetching transitives...");

                    getTransitiveDependencies(allDeps, projectId, name, path);
                } catch (Exception e) {
                    System.out.println("Error during scan:\n" + e);
                    JOptionPane.showMessageDialog(
                            parent,
                            "Error scanning project: " + e.getMessage(),
                            "Scan Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });
        } catch (Exception e) {
            System.out.println("Failed to start scan:\n" + e);
            JOptionPane.showMessageDialog(
                    parent,
                    "Failed to start scan: " + e.getMessage(),
                    "Scan Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
        System.out.println("Scan finished");
    }

    public void getTransitiveDependencies(List<MavenArtifact> directDeps,
                                          String projectId, String name, String path) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            System.out.println("directDeps in background thread: " + directDeps.size());
            for (MavenArtifact d : directDeps) {
                System.out.println("  -> " + d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getVersion());
            }
            System.out.println(">>> executeOnPooledThread STARTED, deps count: " + directDeps.size());
            DepsDevClient client = new DepsDevClient();
            try {
                SqlQueryUtils utils = SqlQueryUtils.getInstance(project);
                utils.upsertProject(projectId, name, path);
                int scanId    = utils.insertScanIntoH2(projectId);

                int completed = 0;
                final int total = directDeps.size();

                for (MavenArtifact dep : directDeps) {
                    fetchTransitivesForDep(client, dep, scanId, utils);
                    completed++;
                    final int done = completed;
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Enriching transitives: " + done + "/" + total + "...")
                    );
                }

                utils.updateLastScanned(projectId);
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Scan complete. " + tableModel.getRowCount() + " dependencies found.")
                );
            } catch (SQLException e) {
                logger.error("Database error during scan", e);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Scan failed: " + e.getMessage()));
            } finally {
                client.shutdown();
            }
        });
    }

    private void fetchTransitivesForDep(DepsDevClient client, MavenArtifact dep,
                                        int scanId, SqlQueryUtils repo) {
        String coords = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();
        System.out.println("Fetching transitive deps for: " + coords);
        Dependencies graph = client.getDependencies(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
        if (graph == null) {
            System.out.println("  Not found in deps.dev, skipping: " + coords);
            return;
        }
        for (Dependencies.Node node : graph.getNodesList()) {
            String relation   = node.getRelation().name();
            String name       = node.getVersionKey().getName();
            String version    = node.getVersionKey().getVersion();
            String[] parts    = name.split(":", 2);
            String groupId    = parts.length == 2 ? parts[0] : name;
            String artifactId = parts.length == 2 ? parts[1] : "";
            String scope      = "DIRECT".equals(relation) ? dep.getScope() : dep.getScope() + " (transitively)";

            try {
                int libraryId = repo.upsertLibrary(groupId, artifactId);
                int versionId = repo.upsertVersion(libraryId, version);
                repo.insertDependency(scanId, versionId, scope, relation);
            } catch (SQLException e) {
                logger.error("Failed to persist dependency: " + name + ":" + version, e);
            }

            SwingUtilities.invokeLater(() ->
                tableModel.addRow(new Object[]{groupId, artifactId, version, scope, relation})
            );
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

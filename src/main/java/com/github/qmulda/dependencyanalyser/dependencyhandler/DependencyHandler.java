package com.github.qmulda.dependencyanalyser.dependencyhandler;

import com.github.qmulda.dependencyanalyser.services.SqlQueryUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import deps_dev.v3.Api.Dependencies;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.sql.SQLException;
import java.util.List;

public class DependencyHandler {
    private static final Logger logger = Logger.getInstance(DependencyHandler.class);

    private final Project project;
    private final Component parent;
    private final DefaultTableModel tableModel;

    public DependencyHandler(Project project, Component parent, DefaultTableModel tableModel) {
        this.project = project;
        this.parent = parent;
        this.tableModel = tableModel;
    }

    public void performScan() {
        System.out.println("performScan() CALLED");
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    System.out.println("Starting dependency scan");
                    tableModel.setRowCount(0); // Clear previous results
                    // Implement Maven dependency extraction - Phase 1.4
                    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
                    System.out.println("Is Maven project: " + manager.isMavenizedProject());
                    System.out.println("Number of Maven projects: " + manager.getProjects().size());
                    for (MavenProject mp : manager.getProjects()) {
                        System.out.println("Scanning Maven project: " + mp.getName());
                        List<MavenArtifact> directDeps = mp.getDependencies();
                        System.out.println("Number of dependencies: " + directDeps.size());
                        getTransitiveDependencies(directDeps);
                    }
                } catch (Exception e) {
                    System.out.println("Error during scan:\n" +  e);
                    JOptionPane.showMessageDialog(
                            parent,
                            "Error scanning project: " + e.getMessage(),
                            "Scan Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });
        } catch (Exception e) {
            System.out.println("Failed to start scan:\n" +  e);
            JOptionPane.showMessageDialog(
                    parent,
                    "Failed to start scan: " + e.getMessage(),
                    "Scan Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
        System.out.println("Scan finished");
    }

    public void getTransitiveDependencies(List<MavenArtifact> directDeps) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            DepsDevClient client = new DepsDevClient();
            try {
                SqlQueryUtils repo = SqlQueryUtils.getInstance(project);
                String basePath = project.getBasePath() != null ? project.getBasePath() : "(unknown)";
                int projectId = repo.upsertProject(project.getName(), basePath);
                int scanId    = repo.insertScan(projectId);

                for (MavenArtifact dep : directDeps) {
                    fetchTransitivesForDep(client, dep, scanId, repo);
                }

                repo.updateLastScanned(projectId);
            } catch (SQLException e) {
                logger.error("Database error during scan", e);
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
            String scope      = "DIRECT".equals(relation) ? dep.getScope() : "transitive";

            try {
                int libraryId = repo.upsertLibrary(groupId, artifactId);
                int versionId = repo.upsertVersion(libraryId, version);
                repo.insertDependency(scanId, versionId, scope, relation);
            } catch (SQLException e) {
                logger.error("Failed to persist dependency: " + name + ":" + version, e);
            }

            SwingUtilities.invokeLater(() ->
                tableModel.addRow(new Object[]{groupId, artifactId, version, "", scope, relation})
            );
        }
    }
}

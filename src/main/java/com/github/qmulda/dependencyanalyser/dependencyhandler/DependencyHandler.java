package com.github.qmulda.dependencyanalyser.dependencyhandler;

import com.github.qmulda.dependencyanalyser.services.DatabaseService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import deps_dev.v3.Api.Dependencies;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.awt.Component;
import java.util.List;

public class DependencyHandler {
    private final Project project;
    private final Component parent;

    public DependencyHandler(Project project, Component parent) {
        this.project = project;
        this.parent = parent;
    }

    public void performScan() {
        System.out.println("performScan() CALLED");
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    System.out.println("Starting dependency scan");
                    DatabaseService dbService = DatabaseService.getInstance(project);
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
    }

    public void getTransitiveDependencies(List<MavenArtifact> directDeps) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            DepsDevClient client = new DepsDevClient();
            try {
                for (MavenArtifact dep : directDeps) {
                    fetchTransitivesForDep(client, dep);
                }
            } finally {
                client.shutdown();
            }
        });
    }

    private void fetchTransitivesForDep(DepsDevClient client, MavenArtifact dep) {
        String coords = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();
        System.out.println("Dependency: " + coords + " scope: " + dep.getScope());
        System.out.println("Fetching transitive deps for: " + coords);
        Dependencies graph = client.getDependencies(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
        if (graph == null) {
            System.out.println("  Not found in deps.dev, skipping: " + coords);
            return;
        }
        for (Dependencies.Node node : graph.getNodesList()) {
            System.out.println("  [" + node.getRelation().name() + "] "
                    + node.getVersionKey().getName() + ":" + node.getVersionKey().getVersion());
        }
    }

}

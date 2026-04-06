package com.github.qmulda.dependencyanalyser.dependencyhandler;

import com.github.qmulda.dependencyanalyser.services.DatabaseService;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.awt.Component;

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
                        System.out.println("Number of dependencies: " + mp.getDependencies().size());
                        for (MavenArtifact dep : mp.getDependencies()) {
                            System.out.println("Dependency: " + dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion() + " scope: " + dep.getScope());
                            getTransitiveDependencies();
                        }
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

    public void getTransitiveDependencies(){



    }

}

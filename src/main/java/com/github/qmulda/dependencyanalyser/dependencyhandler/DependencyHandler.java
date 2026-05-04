package com.github.qmulda.dependencyanalyser.dependencyhandler;

import com.github.qmulda.dependencyanalyser.risk.RiskTierCalculator;
import com.github.qmulda.dependencyanalyser.scan.ScannedDependency;
import com.github.qmulda.dependencyanalyser.services.CycleInfo;
import com.github.qmulda.dependencyanalyser.services.EolIndexService;
import com.github.qmulda.dependencyanalyser.services.SqlQueryUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import deps_dev.v3.Api.Dependencies;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.qmulda.dependencyanalyser.services.EolIndexService.getInstance;

public class DependencyHandler {
    private static final Logger logger = Logger.getInstance(DependencyHandler.class);

    private final Project project;
    private final Component parent;
    private final DefaultTableModel tableModel;
    private final JBLabel statusLabel;

    public DependencyHandler(Project project, Component parent, DefaultTableModel tableModel,
                             JBLabel statusLabel) {
        this.project = project;
        this.parent = parent;
        this.tableModel = tableModel;
        this.statusLabel = statusLabel;
    }

    public void performScan() {
        System.out.println("performScan() CALLED");
        statusLabel.setText("Getting direct deps...");
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
                    String groupId = root.getMavenId().getGroupId();
                    String artifactId = root.getMavenId().getArtifactId();
                    String projectId = sha256Hex(groupId + ":" + artifactId);
                    String name = root.getName();
                    if (name == null || name.isBlank()) name = artifactId;
                    String path = root.getDirectory();

                    System.out.println("project_id = " + projectId);
                    System.out.println("name       = " + name);
                    System.out.println("path       = " + path);

                    DepsDevClient client = new DepsDevClient();

                    // Collect direct deps from all Maven modules as ScannedDependency objects
                    List<ScannedDependency> directDeps = new ArrayList<>();
                    for (MavenProject mp : manager.getProjects()) {
                        System.out.println("Collecting deps from module: " + mp.getName());
                        for (var dep : mp.getDependencies()) {
                            List<String> advisoryIds = client.getPackageMetaData(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());

                            String coords = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();

                            List<String> CvesForDep = client.getCve(advisoryIds);
                            System.out.println("CVEs for " + coords + ": " + CvesForDep);

                            Optional<CycleInfo> cycleInfo = EolIndexService.getInstance()
                                    .lookupCycle(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
                            boolean isDeprecated  = cycleInfo.map(CycleInfo::isEol).orElse(false);
                            String releaseCycle   = cycleInfo.map(CycleInfo::cycle).orElse(null);
                            String eolFrom        = cycleInfo.map(CycleInfo::eolFrom).orElse(null);

                            directDeps.add(new ScannedDependency(
                                    dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                                    dep.getScope(), "DIRECT", advisoryIds, isDeprecated, releaseCycle, eolFrom
                            ));
                        }
                    }
                    System.out.println("Total direct dependencies collected: " + directDeps.size());

                    // Show direct deps in the table immediately
                    for (ScannedDependency dep : directDeps) {
                        tableModel.addRow(new Object[]{
                                dep.groupId, dep.artifactId, dep.version, dep.scope, dep.relation
                        });
                    }
                    statusLabel.setText("Found " + directDeps.size() + " direct deps. Fetching transitives...");

                    startBackgroundEnrichment(directDeps, projectId, name, path);

                } catch (Exception e) {
                    System.out.println("Error during scan:\n" + e);
                    JOptionPane.showMessageDialog(parent,
                            "Error scanning project: " + e.getMessage(),
                            "Scan Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        } catch (Exception e) {
            System.out.println("Failed to start scan:\n" + e);
            JOptionPane.showMessageDialog(parent,
                    "Failed to start scan: " + e.getMessage(),
                    "Scan Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startBackgroundEnrichment(List<ScannedDependency> directDeps,
                                           String projectId, String name, String path) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            System.out.println(">>> Background enrichment started, direct deps: " + directDeps.size());
            DepsDevClient client = new DepsDevClient();
            try {
                SqlQueryUtils utils = SqlQueryUtils.getInstance(project);

                // allDeps starts with the direct deps; transitives are appended below
                List<ScannedDependency> allDeps = new ArrayList<>(directDeps);
                int completed = 0;
                final int total = directDeps.size();

                for (ScannedDependency dep : directDeps) {
                    List<ScannedDependency> transitives = fetchTransitivesForDep(client, dep);
                    allDeps.addAll(transitives);

                    // Progressive table update so the user sees rows as they arrive
                    final List<ScannedDependency> batch = Collections.unmodifiableList(
                            new ArrayList<>(transitives));
                    SwingUtilities.invokeLater(() -> {
                        for (ScannedDependency t : batch) {
                            tableModel.addRow(new Object[]{
                                    t.groupId, t.artifactId, t.version, t.scope, t.relation
                            });
                        }
                    });

                    completed++;
                    final int done = completed;
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Enriching transitives: " + done + "/" + total + "..."));
                }

                // Compute risk tiers on the full dep list (pure computation, no UI involvement)
                RiskTierCalculator.assignTiers(allDeps);

                // Persist the entire scan in one transaction
                //TODO make sure this waits until risk tiers and isDeprecated()/containsCves() is finished calculating
                utils.persistScan(projectId, name, path, allDeps);

                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Scan complete. " + allDeps.size() + " dependencies found."));

            } catch (SQLException e) {
                logger.error("Database error during scan", e);
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Scan failed: " + e.getMessage()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                client.shutdown();
            }
        });
    }

    private List<ScannedDependency> fetchTransitivesForDep(DepsDevClient client,
                                                           ScannedDependency dep) throws IOException, InterruptedException {
        String coords = dep.groupId + ":" + dep.artifactId + ":" + dep.version;
        System.out.println("Fetching transitive deps for: " + coords);

        Dependencies graph = client.getDependencies(dep.groupId, dep.artifactId, dep.version);
        if (graph == null) {
            System.out.println("  Not found in deps.dev, skipping: " + coords);
            return Collections.emptyList();
        }

        List<ScannedDependency> result = new ArrayList<>();
        for (Dependencies.Node node : graph.getNodesList()) {
            // Skip SELF - this node is the direct dep we already have from Maven API
            if ("SELF".equals(node.getRelation().name())) continue;

            String nodeName = node.getVersionKey().getName();
            String version = node.getVersionKey().getVersion();
            String[] parts = nodeName.split(":", 2);
            String groupId = parts.length == 2 ? parts[0] : nodeName;
            String artifactId = parts.length == 2 ? parts[1] : "";

            List<String> advisoryIds = client.getPackageMetaData(dep.groupId, dep.artifactId, dep.version);
            if (advisoryIds != null) {
                List<String> CvesForDep = client.getCve(advisoryIds);
                System.out.println("CVEs for " + coords + ": " + CvesForDep);
            } else{
                System.out.println("No CVEs for " + coords);
            }

            Optional<CycleInfo> cycleInfo = EolIndexService.getInstance()
                    .lookupCycle(groupId, artifactId, version);
            boolean isDeprecated = cycleInfo.map(CycleInfo::isEol).orElse(false);
            String releaseCycle  = cycleInfo.map(CycleInfo::cycle).orElse(null);
            String eolFrom       = cycleInfo.map(CycleInfo::eolFrom).orElse(null);

            // All non-SELF nodes from deps.dev are indirect from the project's perspective
            result.add(new ScannedDependency(groupId, artifactId, version, "transitive", "INDIRECT",
                    advisoryIds, isDeprecated, releaseCycle, eolFrom));
        }
        return result;
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

    //TODO Change sample project to point a Spring Petclinic
    public boolean isDeprecated(String groupId, String artifactId, String versionString) {

        EolIndexService eolIndexService = getInstance();
        Optional<String> slug = eolIndexService.lookupEolSlug(groupId, artifactId);
        if (slug.isEmpty()) {
            java.lang.System.out.println("No EOL slug found for " + groupId + ":" + artifactId + "(EOL.date doesn't track this lib), skipping deprecation check/defaulting to 'not deprecated'");
            return false;
        }

        return eolIndexService.lookupCycle(groupId, artifactId, versionString)
                .map(cycleInfo -> {
                    java.lang.System.out.println("Matched version " + versionString + " to EOL cycle "
                            + cycleInfo.cycle() + " for " + groupId + ":" + artifactId);
                    return cycleInfo.isEol();
                })
                .orElseGet(() -> {
                    java.lang.System.out.println("No matching EOL cycle found for version " + versionString
                            + " of " + groupId + ":" + artifactId + ", defaulting to not deprecated");
                    return false;
                });
    }
}

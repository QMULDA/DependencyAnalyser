package com.github.qmulda.dependencyanalyser.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupabaseExporter {

    private static final String[] TABLE_ORDER = {"project", "library", "scan", "version", "dependency"};

    private final Project project;

    public SupabaseExporter(Project project) {
        this.project = project;
    }

    public void exportAll(Component parent) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Map<String, String> env = loadEnv();
                String supabaseUrl = env.getOrDefault("SUPABASE_URL", System.getenv("SUPABASE_URL"));
                String serviceRoleKey = env.getOrDefault("SUPABASE_SERVICE_ROLE_KEY", System.getenv("SUPABASE_SERVICE_ROLE_KEY"));

                if (supabaseUrl == null || supabaseUrl.isBlank()) {
                    showError(parent, "SUPABASE_URL not found in ~/.dependencyanalyser/.env or environment variables.");
                    return;
                }
                if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
                    showError(parent, "SUPABASE_SERVICE_ROLE_KEY not found in ~/.dependencyanalyser/.env or environment variables.");
                    return;
                }

                DatabaseService dbService = DatabaseService.getInstance(project);
                HttpClient httpClient = HttpClient.newHttpClient();

                for (String table : TABLE_ORDER) {
                    List<Map<String, Object>> rows = dbService.executeQuery("SELECT * FROM " + table);
                    if (rows.isEmpty()) {
                        System.out.println("Skipping empty table: " + table);
                        continue;
                    }

                    String json = toJsonArray(rows);
                    System.out.println("Exporting table '" + table + "' (" + rows.size() + " rows)");

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(supabaseUrl + "/rest/v1/" + table))
                            .header("apikey", serviceRoleKey)
                            .header("Authorization", "Bearer " + serviceRoleKey)
                            .header("Content-Type", "application/json")
                            .header("Prefer", "return=minimal,resolution=merge-duplicates")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("  -> " + table + ": HTTP " + response.statusCode());

                    if (response.statusCode() != 200 && response.statusCode() != 201) {
                        String body = response.body();
                        showError(parent, "Export failed for table '" + table + "': HTTP " + response.statusCode() + "\n" + body);
                        return;
                    }
                }

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(parent, "Export complete — all tables uploaded to Supabase.", "Export", JOptionPane.INFORMATION_MESSAGE)
                );

            } catch (Exception e) {
                System.out.println("Export error: " + e);
                showError(parent, "Export failed: " + e.getMessage());
            }
        });
    }

    /**
     * Loads KEY=VALUE pairs from ~/.dependencyanalyser/.env.
     * Lines starting with '#' and blank lines are ignored.
     */
    private Map<String, String> loadEnv() {
        Map<String, String> env = new HashMap<>();
        File envFile = new File(System.getProperty("user.home"), ".dependencyanalyser/.env");
        if (!envFile.exists()) {
            return env;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).strip();
                    String value = line.substring(eq + 1).strip();
                    env.put(key, value);
                }
            }
        } catch (IOException e) {
            System.out.println("Could not read .env file: " + e.getMessage());
        }
        return env;
    }

    /**
     * Serialises a list of rows (each a Map of column→value) to a JSON array string.
     */
    private String toJsonArray(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJsonObject(rows.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonObject(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(toJsonValue(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Timestamp) return "\"" + ((Timestamp) value).toInstant() + "\"";
        if (value instanceof LocalDateTime) return "\"" + value + "\"";
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void showError(Component parent, String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, message, "Export Error", JOptionPane.ERROR_MESSAGE)
        );
    }
}

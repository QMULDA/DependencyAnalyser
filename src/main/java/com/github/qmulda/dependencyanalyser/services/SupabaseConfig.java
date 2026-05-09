package com.github.qmulda.dependencyanalyser.services;

import java.util.Properties;

/**
 * Supabase connection constants.
 * THIS FILE IS GIT-IGNORED - do not commit credentials.
 * <p>
 * Fill in your values from Supabase -> Settings -> API Keys
 * (use the "Publishable and secret API keys" tab, not the legacy keys).
 */
public final class SupabaseConfig {
    private static Properties load(String basePath) {
        Properties p = new Properties();
        try (var in = new java.io.FileInputStream(basePath + "/local.properties")) {
            p.load(in);
        } catch (java.io.IOException e) {
            System.out.println("SupabaseConfig: could not load local.properties from " + basePath + ": " + e.getMessage());
        }
        return p;
    }

    public static String url(Properties p) {
        return p.getProperty("supabase.url");
    }

    public static String secretKey(Properties p) {
        return p.getProperty("supabase.secret_key");
    }

    public static Properties load(com.intellij.openapi.project.Project project) {
        return load(project.getBasePath());
    }

    public static boolean isConfigured(Properties p) {
        return url(p) != null && !url(p).isBlank()
                && secretKey(p) != null && !secretKey(p).isBlank();
    }
}
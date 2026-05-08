package com.github.qmulda.dependencyanalyser.services;

/**
 * Supabase connection constants.
 * THIS FILE IS GIT-IGNORED - do not commit credentials.
 *
 * Fill in your values from Supabase -> Settings -> API Keys
 * (use the "Publishable and secret API keys" tab, not the legacy keys).
 */
public final class SupabaseConfig {
    public static String url() { return System.getenv("SUPABASE_URL"); }
    public static String secretKey() { return System.getenv("SUPABASE_SECRET_KEY"); }
    public static boolean isConfigured() {
        return url() != null && !url().isBlank()
                && secretKey() != null && !secretKey().isBlank();
    }
}
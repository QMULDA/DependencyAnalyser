package com.github.qmulda.dependencyanalyser.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Application-level service providing Maven coordinate -> endoflife.date product slug lookups.
 * The index is loaded lazily from the bundled eol/purl-to-slug.json resource on first access.
 * Regenerate that file with: ./gradlew generatePurlIndex
 */
@Service(Service.Level.APP)
public final class EolIndexService {

    private static final Logger logger = Logger.getInstance(EolIndexService.class);

    private volatile Map<String, String> index;

    public static EolIndexService getInstance() {
        return ApplicationManager.getApplication().getService(EolIndexService.class);
    }

    public Optional<String> lookup(String groupId, String artifactId) {
        return Optional.ofNullable(getIndex().get(mavenPurl(groupId, artifactId)));
    }

    private Map<String, String> getIndex() {
        if (index == null) {
            synchronized (this) {
                if (index == null) {
                    index = loadIndex();
                }
            }
        }
        return index;
    }

    private Map<String, String> loadIndex() {
        try (var stream = getClass().getResourceAsStream("/eol/purl-to-slug.json")) {
            if (stream == null) {
                logger.warn("purl-to-slug.json not found in classpath — EOL lookups will return empty");
                return Map.of();
            }
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), type);
            return Collections.unmodifiableMap(loaded);
        } catch (Exception e) {
            logger.error("Failed to load purl-to-slug.json", e);
            return Map.of();
        }
    }

    // Mirrors PurlIndexGenerator.mavenPurl() - both must stay identical.
    // buildSrc/ code is not on the plugin runtime classpath, so the helper is duplicated here.
    private static String mavenPurl(String groupId, String artifactId) {
        return "pkg:maven/" + groupId + "/" + artifactId;
    }
}

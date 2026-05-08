package com.github.qmulda.dependencyanalyser.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application-level service for endoflife.date lookups backed by two bundled resources:
 *   eol/purl-to-slug.json  - maps pkg:maven/ PURLs to product slugs
 *   eol/eol-cycles.json    - maps product slugs to their release cycle data
 * Regenerate both files with: ./gradlew generatePurlIndex
 */
@Service(Service.Level.APP)
public final class EolIndexService {

    private static final Logger logger = Logger.getInstance(EolIndexService.class);

    private volatile Map<String, String> index;
    private volatile Map<String, List<CycleInfo>> cycles;

    public static EolIndexService getInstance() {
        return ApplicationManager.getApplication().getService(EolIndexService.class);
    }

    /** Returns the endoflife.date product slug for a Maven coordinate, if one exists. */
    public Optional<String> lookupEolSlug(String groupId, String artifactId) {
        return Optional.ofNullable(getIndex().get(mavenPurl(groupId, artifactId)));
    }

    /**
     * Returns the release cycle that best matches the given version string,
     * or empty if the library is not tracked by endoflife.date or no cycle matches.
     * Uses the raw version string (not parsed semver) so non-standard strings like
     * "2.0.0.RELEASE" still match their major cycle via prefix.
     */
    public Optional<CycleInfo> lookupCycle(String groupId, String artifactId, String version) {
        Optional<String> slug = lookupEolSlug(groupId, artifactId);
        if (slug.isEmpty()) return Optional.empty();
        List<CycleInfo> slugCycles = getOrLoadCycles().get(slug.get());
        if (slugCycles == null || slugCycles.isEmpty()) return Optional.empty();
        return matchCycle(slugCycles, version);
    }

    private Optional<CycleInfo> matchCycle(List<CycleInfo> slugCycles, String version) {
        // Sort by cycle string length descending so more specific cycles ("3.2") are
        // tried before broader ones ("3"). This ensures "2.12.4" matches "2.12", not "2".
        List<CycleInfo> sorted = new ArrayList<>(slugCycles);
        sorted.sort(Comparator.comparingInt((CycleInfo c) -> c.cycle().length()).reversed());

        for (CycleInfo c : sorted) {
            if (version.startsWith(c.cycle() + ".") || version.equals(c.cycle())) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    private Map<String, String> getIndex() {
        if (index == null) {
            synchronized (this) {
                if (index == null) index = loadIndex();
            }
        }
        return index;
    }

    private Map<String, List<CycleInfo>> getOrLoadCycles() {
        if (cycles == null) {
            synchronized (this) {
                if (cycles == null) cycles = loadCycles();
            }
        }
        return cycles;
    }

    private Map<String, String> loadIndex() {
        try (var stream = getClass().getResourceAsStream("/eol/purl-to-slug.json")) {
            if (stream == null) {
                logger.warn("purl-to-slug.json not found in classpath - EOL lookups will return empty");
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

    /*
    *  Returns all cycles in eol-cycles.json
    */
    private Map<String, List<CycleInfo>> loadCycles() {
        try (var stream = getClass().getResourceAsStream("/eol/eol-cycles.json")) {
            if (stream == null) {
                logger.warn("eol-cycles.json not found in classpath - cycle lookups will return empty");
                return Map.of();
            }
            // Parse as maps first, Gson cannot deserialise records directly
            Type type = new TypeToken<Map<String, List<Map<String, Object>>>>() {}.getType();
            Map<String, List<Map<String, Object>>> raw = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), type);

            Map<String, List<CycleInfo>> result = new HashMap<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : raw.entrySet()) {
                List<CycleInfo> cycleList = new ArrayList<>();
                for (Map<String, Object> c : entry.getValue()) {
                    cycleList.add(new CycleInfo(
                            (String)  c.get("cycle"),
                            (String)  c.get("releaseDate"),
                            Boolean.TRUE.equals(c.get("isEol")),
                            (String)  c.get("eolFrom"),
                            (String)  c.get("latestVersion")
                    ));
                }
                result.put(entry.getKey(), Collections.unmodifiableList(cycleList));
            }
            return Collections.unmodifiableMap(result);
        } catch (Exception e) {
            logger.error("Failed to load eol-cycles.json", e);
            return Map.of();
        }
    }

    // Mirrors PurlIndexGenerator.mavenPurl() - both must stay identical.
    // buildSrc/ code is not on the plugin runtime classpath, so the helper is duplicated here.
    private static String mavenPurl(String groupId, String artifactId) {
        return "pkg:maven/" + groupId + "/" + artifactId;
    }
}

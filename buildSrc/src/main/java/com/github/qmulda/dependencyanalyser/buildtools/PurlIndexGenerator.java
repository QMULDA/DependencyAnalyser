package com.github.qmulda.dependencyanalyser.buildtools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PurlIndexGenerator {

    // Pin to a specific upstream commit for deterministic regeneration.
    // Re-pinning to a newer SHA is a one-line change — reviewable alongside the resulting diff.
    private static final String COMMIT_SHA = "8ed3c7c05c37f05fea8253f7dee513931ee128e5";

    private static final String TREES_URL =
            "https://api.github.com/repos/endoflife-date/endoflife.date/git/trees/"
                    + COMMIT_SHA + "?recursive=1";
    private static final String RAW_URL_TEMPLATE =
            "https://raw.githubusercontent.com/endoflife-date/endoflife.date/" + COMMIT_SHA + "/";

    public static String mavenPurl(String groupId, String artifactId) {
        return "pkg:maven/" + groupId + "/" + artifactId;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Pinned SHA: " + COMMIT_SHA);

        String projectRoot = args.length > 0 ? args[0] : System.getProperty("user.dir");
        Path outputPath = Path.of(projectRoot, "src", "main", "resources", "eol", "purl-to-slug.json");

        HttpClient client = HttpClient.newHttpClient();
        String token = System.getenv("GITHUB_TOKEN");

        List<Map<String, Object>> productFiles = fetchProductFiles(client, token);
        System.out.println("Found " + productFiles.size() + " product .md files");

        Map<String, String> index = new HashMap<>();
        int processed = 0;
        for (Map<String, Object> entry : productFiles) {
            String path = (String) entry.get("path");
            String slug = path.substring("products/".length(), path.length() - ".md".length());

            try {
                String content = fetchRaw(client, token, RAW_URL_TEMPLATE + path);
                if (content == null) continue;

                Map<String, String> purls = parsePurls(content, slug);
                for (Map.Entry<String, String> e : purls.entrySet()) {
                    if (index.containsKey(e.getKey())) {
                        String existing = index.get(e.getKey());
                        String winner = existing.compareTo(slug) <= 0 ? existing : slug;
                        System.err.println("WARN: duplicate PURL " + e.getKey()
                                + " in [" + existing + ", " + slug + "] — keeping " + winner);
                        index.put(e.getKey(), winner);
                    } else {
                        index.put(e.getKey(), e.getValue());
                    }
                }
                processed++;
            } catch (Exception ex) {
                System.err.println("WARN: failed to process " + path + ": " + ex.getMessage());
            }
        }
        System.out.println("Processed " + processed + "/" + productFiles.size()
                + " files, " + index.size() + " maven PURLs found");

        TreeMap<String, String> sorted = new TreeMap<>(index);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(sorted) + "\n";

        Files.createDirectories(outputPath.getParent());
        Path tmp = outputPath.resolveSibling("purl-to-slug.json.tmp");
        Files.writeString(tmp, json);
        try {
            Files.move(tmp, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("Wrote " + sorted.size() + " entries to " + outputPath);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fetchProductFiles(HttpClient client, String token) throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(TREES_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PurlIndexGenerator/1.0");
        if (token != null && !token.isBlank()) {
            rb.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Trees API returned HTTP " + resp.statusCode()
                    + " — set GITHUB_TOKEN if hitting rate limits");
        }

        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> body = gson.fromJson(resp.body(), type);

        if (Boolean.TRUE.equals(body.get("truncated"))) {
            throw new RuntimeException("Trees API response was truncated — index would be incomplete. "
                    + "Re-pin to a newer SHA and re-run.");
        }

        List<Map<String, Object>> tree = (List<Map<String, Object>>) body.get("tree");
        return tree.stream()
                .filter(e -> "blob".equals(e.get("type")))
                .filter(e -> {
                    String p = (String) e.get("path");
                    return p != null && p.startsWith("products/") && p.endsWith(".md");
                })
                .toList();
    }

    private static String fetchRaw(HttpClient client, String token, String url)
            throws IOException, InterruptedException {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "PurlIndexGenerator/1.0");
        if (token != null && !token.isBlank()) {
            rb.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            System.err.println("WARN: HTTP " + resp.statusCode() + " for " + url + " — skipping");
            return null;
        }
        return resp.body();
    }

    // Package-private for unit testing. Parses the YAML frontmatter of a product file and
    // returns all pkg:maven/ PURLs mapped to the given slug.
    static Map<String, String> parsePurls(String fileContent, String slug) {
        Map<String, String> result = new HashMap<>();

        if (!fileContent.startsWith("---")) {
            System.err.println("WARN: " + slug + " does not start with '---' — skipping");
            return result;
        }

        // Split on bare '---' lines; parts[0] is empty, parts[1] is frontmatter, parts[2]+ is body
        String[] parts = fileContent.split("(?m)^---\\s*$", 3);
        if (parts.length < 2) {
            System.err.println("WARN: " + slug + " missing closing frontmatter delimiter — skipping");
            return result;
        }

        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object parsed = yaml.load(parts[1]);
            if (!(parsed instanceof Map<?, ?> frontmatter)) return result;

            Object identifiersObj = frontmatter.get("identifiers");
            if (!(identifiersObj instanceof List<?> identifiers)) return result;

            for (Object item : identifiers) {
                if (!(item instanceof Map<?, ?> entry)) continue;
                Object purlObj = entry.get("purl");
                if (purlObj instanceof String purl && purl.startsWith("pkg:maven/")) {
                    result.put(purl, slug);
                }
            }
        } catch (Exception e) {
            System.err.println("WARN: YAML parse error for " + slug + ": " + e.getMessage());
        }

        return result;
    }
}

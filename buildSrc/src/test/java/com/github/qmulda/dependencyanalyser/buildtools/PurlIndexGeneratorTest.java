package com.github.qmulda.dependencyanalyser.buildtools;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;

public class PurlIndexGeneratorTest {

    private static String loadFixture(String name) throws IOException {
        String path = "/fixtures/" + name;
        try (InputStream is = PurlIndexGeneratorTest.class.getResourceAsStream(path)) {
            assertNotNull("Fixture not found: " + path, is);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void singleMavenPurl_springBoot() throws IOException {
        Map<String, String> result = PurlIndexGenerator.parsePurls(loadFixture("spring-boot.md"), "spring-boot");

        assertEquals(3, result.size());
        assertTrue(result.containsKey("pkg:maven/org.springframework.boot/spring-boot"));
        assertTrue(result.containsKey("pkg:maven/org.springframework.boot/spring-boot-starter"));
        assertTrue(result.containsKey("pkg:maven/org.springframework.boot/spring-boot-starter-web"));
        result.values().forEach(slug -> assertEquals("spring-boot", slug));
    }

    @Test
    public void multipleMavenPurls_log4j() throws IOException {
        Map<String, String> result = PurlIndexGenerator.parsePurls(loadFixture("log4j.md"), "log4j");

        assertEquals(2, result.size());
        assertTrue(result.containsKey("pkg:maven/org.apache.logging.log4j/log4j-core"));
        assertTrue(result.containsKey("pkg:maven/log4j/log4j"));
        result.values().forEach(slug -> assertEquals("log4j", slug));
    }

    @Test
    public void noMavenPurls_amazonLinux() throws IOException {
        Map<String, String> result = PurlIndexGenerator.parsePurls(loadFixture("amazon-linux.md"), "amazon-linux");

        assertTrue("Expected zero entries for a product with no pkg:maven/ identifiers", result.isEmpty());
    }

    @Test
    public void noIdentifiersField_returnsEmpty() {
        String content = "---\ntitle: Some Product\ncategory: os\n---\nBody text.\n";
        Map<String, String> result = PurlIndexGenerator.parsePurls(content, "some-product");

        assertTrue("Expected empty map when identifiers field is absent", result.isEmpty());
    }

    @Test
    public void malformedYaml_returnsEmptyWithoutThrowing() {
        String content = "---\ntitle: Bad Product\n  invalid: [unclosed\n---\nBody.\n";
        Map<String, String> result = PurlIndexGenerator.parsePurls(content, "bad-product");

        assertTrue("Expected empty map for malformed YAML", result.isEmpty());
    }
}

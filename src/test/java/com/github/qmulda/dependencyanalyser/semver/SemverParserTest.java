package com.github.qmulda.dependencyanalyser.semver;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class SemverParserTest {

    // Valid inputs
    @Test
    public void parseStandardThreePart() {
        Optional<Semver> result = SemverParser.parse("2.16.1");
        assertTrue(result.isPresent());
        Semver sv = result.get();
        assertEquals(2, sv.major());
        assertEquals(16, sv.minor());
        assertEquals(1, sv.patch());
    }

    @Test
    public void parseStripsSnapshotSuffix() {
        Optional<Semver> result = SemverParser.parse("3.2.0-SNAPSHOT");
        assertTrue(result.isPresent());
        Semver sv = result.get();
        assertEquals(3, sv.major());
        assertEquals(2, sv.minor());
        assertEquals(0, sv.patch());
    }

    @Test
    public void parseTwoPartDefaultsPatchToZero() {
        // 1.5-M3 → strip "-M3" → "1.5" → patch defaults to 0
        Optional<Semver> result = SemverParser.parse("1.5-M3");
        assertTrue(result.isPresent());
        Semver sv = result.get();
        assertEquals(1, sv.major());
        assertEquals(5, sv.minor());
        assertEquals(0, sv.patch());
    }

    @Test
    public void parseBuildMetadataStripped() {
        Optional<Semver> result = SemverParser.parse("1.0.0+build.123");
        assertTrue(result.isPresent());
        Semver sv = result.get();
        assertEquals(1, sv.major());
        assertEquals(0, sv.minor());
        assertEquals(0, sv.patch());
    }

    @Test
    public void parsePreserveOriginalString() {
        Optional<Semver> result = SemverParser.parse("3.2.0-SNAPSHOT");
        assertTrue(result.isPresent());
        assertEquals("3.2.0-SNAPSHOT", result.get().original());
    }

    @Test
    public void parseTwoPartNoSuffix() {
        Optional<Semver> result = SemverParser.parse("2.7");
        assertTrue(result.isPresent());
        assertEquals(2, result.get().major());
        assertEquals(7, result.get().minor());
        assertEquals(0, result.get().patch());
    }

    // Invalid inputs
    @Test
    public void rejectSpringDotReleaseQualifier() {
        // 2.0.0.RELEASE has 4 dot-separated segments — reject
        assertFalse(SemverParser.parse("2.0.0.RELEASE").isPresent());
    }

    @Test
    public void rejectCalendarVersion() {
        // 2023 > 999 — calendar version, not semver
        assertFalse(SemverParser.parse("2023.0.1").isPresent());
    }

    @Test
    public void rejectSingleSegment() {
        assertFalse(SemverParser.parse("1").isPresent());
    }

    @Test
    public void rejectNull() {
        assertFalse(SemverParser.parse(null).isPresent());
    }

    @Test
    public void rejectBlank() {
        assertFalse(SemverParser.parse("   ").isPresent());
    }

    @Test
    public void rejectNonNumericSegment() {
        assertFalse(SemverParser.parse("abc.1.0").isPresent());
    }

    // Comparator correctness
    @Test
    public void compareCorrectlyAvoidingLexicographicTrap() {
        // "2.10.0" > "2.9.0" numerically; lexicographic order would reverse this
        Semver v2_10 = SemverParser.parse("2.10.0").orElseThrow();
        Semver v2_9  = SemverParser.parse("2.9.0").orElseThrow();
        assertTrue(v2_10.compareTo(v2_9) > 0);
    }

    @Test
    public void compareMajorBeatsMinor() {
        Semver v2_0_0 = SemverParser.parse("2.0.0").orElseThrow();
        Semver v1_99_99 = SemverParser.parse("1.99.99").orElseThrow();
        assertTrue(v2_0_0.compareTo(v1_99_99) > 0);
    }
}

package com.github.qmulda.dependencyanalyser.semver;

import java.util.Optional;

public final class SemverParser {

    private SemverParser() {}

    public static Optional<Semver> parse(String versionString) {
        if (versionString == null || versionString.isBlank()) {
            return Optional.empty();
        }

        String s = versionString.trim();

        // Strip pre-release suffix (e.g. -SNAPSHOT, -M3, -RC1)
        int dashIdx = s.indexOf('-');
        if (dashIdx >= 0) s = s.substring(0, dashIdx);

        // Strip build metadata (e.g. +build.123)
        int plusIdx = s.indexOf('+');
        if (plusIdx >= 0) s = s.substring(0, plusIdx);

        String[] parts = s.split("\\.");

        // 4+ segments: Maven qualifiers like 2.0.7.RELEASE / 6.6.29.Final, or 4-part numeric
        // versions like 10.0.0.1 — use only the first 3 numeric parts.
        if (parts.length > 3) parts = new String[]{parts[0], parts[1], parts[2]};

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;

            // Reject calendar versions (e.g. 2023.0.1 - 2023 > 999).
            if (major > 999 || minor > 999 || patch > 999) {
                System.out.println("Rejecting version with component > 999: " + versionString);
                return Optional.empty();
            }

            return Optional.of(new Semver(major, minor, patch, versionString));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

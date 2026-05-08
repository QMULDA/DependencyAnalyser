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
        if (parts.length < 2) {
            System.out.println("Rejecting version with less than 2 segments: " + versionString);
            return Optional.empty();
        }
        // More than 3 segments means a non-standard qualifier like 2.0.0.RELEASE - reject
        if (parts.length > 3) {
            System.out.println("Rejecting version with more than 3 segments: " + versionString);
            return Optional.empty();
        }

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
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

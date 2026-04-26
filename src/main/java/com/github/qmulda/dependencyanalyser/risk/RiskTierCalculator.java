package com.github.qmulda.dependencyanalyser.risk;

import com.github.qmulda.dependencyanalyser.scan.ScannedDependency;
import com.github.qmulda.dependencyanalyser.semver.Semver;
import com.github.qmulda.dependencyanalyser.semver.SemverParser;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RiskTierCalculator {

    private static final Logger logger = Logger.getInstance(RiskTierCalculator.class);

    private RiskTierCalculator() {}

    public static void assignTiers(List<ScannedDependency> deps) {
        Map<String, List<ScannedDependency>> byLibrary = new HashMap<>();
        for (ScannedDependency dep : deps) {
            String key = dep.groupId + ":" + dep.artifactId;
            byLibrary.computeIfAbsent(key, k -> new ArrayList<>()).add(dep);
        }

        for (Map.Entry<String, List<ScannedDependency>> entry : byLibrary.entrySet()) {
            List<ScannedDependency> group = entry.getValue();
            List<Semver> parsed = new ArrayList<>();

            // Parsing may fail for some versions, so wrap in Optional
            for (ScannedDependency dep : group) {
                Optional<Semver> sv = SemverParser.parse(dep.version);
                sv.ifPresent(parsed::add);
            }

            // If no versions could be parsed, mark as high risk
            if (parsed.isEmpty()) {
                group.forEach(dep -> dep.riskTier = RiskTier.HIGH);
                continue;
            }

            Semver newest = Collections.max(parsed);

            
            for (ScannedDependency dep : group) {
                Optional<Semver> sv = SemverParser.parse(dep.version);
                if (sv.isEmpty()) {
                    dep.riskTier = RiskTier.HIGH;
                } else if (sv.get().compareTo(newest) == 0) {
                    // if they are the same version, assign NONE risk
                    dep.riskTier = RiskTier.NONE;
                } else {
                    dep.riskTier = classifyDifference(sv.get(), newest);
                }
            }
        }

        logDistribution(deps);
    }

    private static RiskTier classifyDifference(Semver a, Semver newest) {
        if (a.major() != newest.major()) return RiskTier.HIGH;
        if (a.minor() != newest.minor()) return RiskTier.MEDIUM;
        if (a.patch() != newest.patch()) return RiskTier.LOW;
        return RiskTier.NONE;
    }

    private static void logDistribution(List<ScannedDependency> deps) {
        int none = 0, low = 0, medium = 0, high = 0;
        for (ScannedDependency dep : deps) {
            if (dep.riskTier == null) continue;
            switch (dep.riskTier) {
                case NONE -> none++;
                case LOW -> low++;
                case MEDIUM -> medium++;
                case HIGH -> high++;
            }
        }
        logger.info(String.format("Risk tiers: NONE=%d, LOW=%d, MEDIUM=%d, HIGH=%d (%d deps)",
                none, low, medium, high, deps.size()));
        System.out.printf("Risk tiers: NONE=%d, LOW=%d, MEDIUM=%d, HIGH=%d (%d deps)%n",
                none, low, medium, high, deps.size());
    }
}

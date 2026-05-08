package com.github.qmulda.dependencyanalyser.scan;

import com.github.qmulda.dependencyanalyser.risk.RiskTier;

import java.util.List;

public class ScannedDependency {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String scope;
    public final List<String> advisoryIds;
    public final boolean isDeprecated;
    public final boolean containsCves;

    // "DIRECT" or "INDIRECT" - deps.dev SELF is collapsed to DIRECT
    public final String relation;

    // Null if no matching endoflife.date cycle was found
    public final String releaseCycle;

    // ISO date string (e.g. "2024-11-29"), null if cycle is still supported / not matched
    public final String eolFrom;

    // Null until RiskTierCalculator.assignTiers() runs
    public RiskTier riskTier;

    public ScannedDependency(String groupId, String artifactId, String version,
                             String scope, String relation, List<String> advisoryIds,
                             boolean isDeprecated, String releaseCycle, String eolFrom) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
        this.relation = "SELF".equals(relation) ? "DIRECT" : relation;
        this.advisoryIds = advisoryIds;
        this.isDeprecated = isDeprecated;
        this.containsCves = !advisoryIds.isEmpty();
        this.releaseCycle = releaseCycle;
        this.eolFrom = eolFrom;
    }
}

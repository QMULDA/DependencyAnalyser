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

    // Null until RiskTierCalculator.assignTiers() runs
    public RiskTier riskTier;


    public ScannedDependency(String groupId, String artifactId, String version,
                             String scope, String relation, List<String> advisoryIds, boolean isDeprecated) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
        this.relation = "SELF".equals(relation) ? "DIRECT" : relation;
        this.advisoryIds = advisoryIds;
        this.isDeprecated = isDeprecated;
        this.containsCves = !advisoryIds.isEmpty();
    }
}

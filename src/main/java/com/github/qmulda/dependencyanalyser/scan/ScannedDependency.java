package com.github.qmulda.dependencyanalyser.scan;

import com.github.qmulda.dependencyanalyser.risk.RiskTier;

public class ScannedDependency {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String scope;

    // "DIRECT" or "INDIRECT" - deps.dev SELF is collapsed to DIRECT
    public final String relation;

    // Null until RiskTierCalculator.assignTiers() runs
    public RiskTier riskTier;

    public ScannedDependency(String groupId, String artifactId, String version,
                             String scope, String relation) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
        this.relation = "SELF".equals(relation) ? "DIRECT" : relation;
    }
}

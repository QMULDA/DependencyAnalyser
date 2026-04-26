package com.github.qmulda.dependencyanalyser.risk;

import com.github.qmulda.dependencyanalyser.scan.ScannedDependency;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RiskTierCalculatorTest {

    private static ScannedDependency dep(String groupId, String artifactId, String version) {
        return new ScannedDependency(groupId, artifactId, version, "compile", "DIRECT");
    }

    // Case 1: single version -> NONE 
    @Test
    public void singleVersionIsNone() {
        List<ScannedDependency> deps = Collections.singletonList(
                dep("org.example", "lib", "1.0.0")
        );
        RiskTierCalculator.assignTiers(deps);
        assertEquals(RiskTier.NONE, deps.get(0).riskTier);
    }

    // Case 2: two versions, minor apart -> MEDIUM/NONE
    @Test
    public void twoVersionsMinorApartGivesMediumAndNone() {
        ScannedDependency older = dep("org.example", "lib", "2.15.0");
        ScannedDependency newer = dep("org.example", "lib", "2.16.1");
        RiskTierCalculator.assignTiers(Arrays.asList(older, newer));
        assertEquals(RiskTier.MEDIUM, older.riskTier);
        assertEquals(RiskTier.NONE, newer.riskTier);
    }

    // Case 3: major difference -> HIGH/NONE
    @Test
    public void majorVersionDifferenceGivesHighAndNone() {
        ScannedDependency old = dep("org.example", "lib", "1.0.0");
        ScannedDependency latest = dep("org.example", "lib", "2.0.0");
        RiskTierCalculator.assignTiers(Arrays.asList(old, latest));
        assertEquals(RiskTier.HIGH, old.riskTier);
        assertEquals(RiskTier.NONE, latest.riskTier);
    }

    // Case 4: patch difference -> LOW/NONE
    @Test
    public void patchVersionDifferenceGivesLowAndNone() {
        ScannedDependency older = dep("org.example", "lib", "2.16.0");
        ScannedDependency newer = dep("org.example", "lib", "2.16.1");
        RiskTierCalculator.assignTiers(Arrays.asList(older, newer));
        assertEquals(RiskTier.LOW, older.riskTier);
        assertEquals(RiskTier.NONE, newer.riskTier);
    }

    // Case 5: unparseable version only -> HIGH
    @Test
    public void unparseableVersionOnlyGivesHigh() {
        ScannedDependency dep = dep("org.springframework", "spring-core", "2.0.0.RELEASE");
        RiskTierCalculator.assignTiers(Collections.singletonList(dep));
        assertEquals(RiskTier.HIGH, dep.riskTier);
    }

    // Case 6: two independent libraries computed separately
    @Test
    public void twoLibrariesAreIndependent() {
        ScannedDependency libAOld = dep("org.example", "lib-a", "1.0.0");
        ScannedDependency libANew = dep("org.example", "lib-a", "2.0.0");
        ScannedDependency libBOnly = dep("org.example", "lib-b", "3.5.1");

        RiskTierCalculator.assignTiers(Arrays.asList(libAOld, libANew, libBOnly));

        assertEquals(RiskTier.HIGH, libAOld.riskTier);    // major behind in lib-a
        assertEquals(RiskTier.NONE, libANew.riskTier);    // newest in lib-a
        assertEquals(RiskTier.NONE, libBOnly.riskTier);   // only version of lib-b
    }
}

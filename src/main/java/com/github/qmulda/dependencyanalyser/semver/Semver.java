package com.github.qmulda.dependencyanalyser.semver;

public record Semver(int major, int minor, int patch, String original) implements Comparable<Semver> {

    @Override
    public int compareTo(Semver other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        return Integer.compare(this.patch, other.patch);
    }
}

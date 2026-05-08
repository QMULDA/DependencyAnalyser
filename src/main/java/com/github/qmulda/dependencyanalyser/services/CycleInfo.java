package com.github.qmulda.dependencyanalyser.services;

/**
 * One release cycle entry from endoflife.date, as bundled in eol-cycles.json.
 * eolFrom is null when the cycle is still actively supported (isEol == false).
 */
public record CycleInfo(
        String cycle,
        String releaseDate,
        boolean isEol,
        String eolFrom,
        String latestVersion
) {}

package com.github.qmulda.dependencyanalyser.dependencyhandler;

import deps_dev.v3.InsightsGrpc;
import deps_dev.v3.Api.Dependencies;
import deps_dev.v3.Api.GetDependenciesRequest;
import deps_dev.v3.Api.GetVersionRequest;
import deps_dev.v3.Api.VersionKey;
import deps_dev.v3.Api.Version;
import deps_dev.v3.Api.System;
import deps_dev.v3.Api.Advisory;
import deps_dev.v3.Api.AdvisoryKey;
import deps_dev.v3.Api.GetAdvisoryRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.net.http.HttpClient.newHttpClient;

public class DepsDevClient {

    private static final String HOST = "api.deps.dev";
    private static final int PORT = 443;

    private final ManagedChannel channel;
    private final InsightsGrpc.InsightsBlockingStub stub;
    private final Map<String, Dependencies> dependencyCache = new HashMap<>();
    private final Map<String, List<String>> versionCache = new HashMap<>();
    private final Map<String, List<String>> CveCache = new HashMap<>();

    public DepsDevClient() {
        this.channel = ManagedChannelBuilder
                .forAddress(HOST, PORT)
                .useTransportSecurity()
                .build();
        this.stub = InsightsGrpc.newBlockingStub(channel);
    }

    /**
     * Returns the resolved dependency graph for a Maven artifact.
     * Results are cached by "groupId:artifactId:version" key.
     * Returns null if the package is not found in deps.dev.
     *
     * @param groupId    Maven group ID
     * @param artifactId Maven artifact ID
     * @param version    Maven version string
     * @return Dependencies graph, or null if not found / on error
     */
    public Dependencies getDependencies(String groupId, String artifactId, String version) {
        String cacheKey = groupId + ":" + artifactId + ":" + version;
        if (dependencyCache.containsKey(cacheKey)) {
            return dependencyCache.get(cacheKey);
        }

        VersionKey vk = VersionKey.newBuilder()
                .setSystem(System.MAVEN)
                .setName(groupId + ":" + artifactId)
                .setVersion(version)
                .build();
        GetDependenciesRequest request = GetDependenciesRequest.newBuilder()
                .setVersionKey(vk)
                .build();

        int attempts = 6;
        long backoffMs = 500;
        while (attempts-- > 0) {
            try {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                Dependencies dependencies = stub.getDependencies(request);
                dependencyCache.put(cacheKey, dependencies);
                return dependencies;
            } catch (StatusRuntimeException e) {
                Status.Code code = e.getStatus().getCode();
                boolean isRateLimited = code == Status.Code.UNAVAILABLE || code == Status.Code.RESOURCE_EXHAUSTED;
                if (isRateLimited && attempts > 0) {
                    java.lang.System.out.println("deps.dev " + code + " for " + cacheKey + ", retrying in " + backoffMs + "ms (" + attempts + " left)...");
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    backoffMs *= 2;
                } else if (isRateLimited) {
                    java.lang.System.out.println("deps.dev rate limited for " + cacheKey + " after retries, giving up.");
                    return null;
                } else {
                    java.lang.System.out.println("deps.dev error for " + cacheKey + ": " + e.getStatus());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Returns returns information about a specific package version, including
     * its licenses and any security advisories known to affect it.
     * Results are cached by "groupId:artifactId:version" key.
     * Returns null if the package is not found in deps.dev.
     *
     * @param groupId    Maven group ID
     * @param artifactId Maven artifact ID
     * @param versionString    Maven version string
     * @return Version, or null if not found / on error
     */
    public List<String> getPackageMetaData(String groupId, String artifactId, String versionString) {

        String cacheKey = groupId + ":" + artifactId + ":" + versionString;
        if (versionCache.containsKey(cacheKey)) {
            return versionCache.get(cacheKey);
        }

        VersionKey vk = VersionKey.newBuilder()
                .setSystem(System.MAVEN)
                .setName(groupId + ":" + artifactId)
                .setVersion(versionString)
                .build();
        GetVersionRequest request = GetVersionRequest.newBuilder()
                .setVersionKey(vk)
                .build();

        int attempts = 6;
        long backoffMs = 500;
        while (attempts-- > 0) {
            try {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                Version version = stub.getVersion(request);

                List<String> advisoryIds = version.getAdvisoryKeysList().stream()
                        .map(AdvisoryKey::getId)
                        .toList();
                versionCache.put(cacheKey, advisoryIds);
                return advisoryIds;
            } catch (StatusRuntimeException e) {
                Status.Code code = e.getStatus().getCode();
                boolean isRateLimited = code == Status.Code.UNAVAILABLE || code == Status.Code.RESOURCE_EXHAUSTED;
                if (isRateLimited && attempts > 0) {
                    java.lang.System.out.println("deps.dev " + code + " for " + cacheKey + ", retrying in " + backoffMs + "ms (" + attempts + " left)...");
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    backoffMs *= 2;
                } else if (isRateLimited) {
                    java.lang.System.out.println("deps.dev rate limited for " + cacheKey + " after retries, giving up.");
                    return null;
                } else {
                    java.lang.System.out.println("deps.dev error for " + cacheKey + ": " + e.getStatus());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Returns returns information about CVE
     *
     * @param advisoryKeys    Maven group ID
     * @return advisoryKeys.id, or null if not found / on error
     */
    public List<String> getCve(List<String> advisoryKeys) {
        List<String> CvesForDep = new ArrayList<>(List.of());
        for(String advisoryKey : advisoryKeys) {
            if (CveCache.containsKey(advisoryKey)) {
                return CveCache.get(advisoryKey);
            }

            AdvisoryKey ak = AdvisoryKey.newBuilder()
                    .setId(advisoryKey)
                    .build();

            GetAdvisoryRequest request = GetAdvisoryRequest.newBuilder()
                    .setAdvisoryKey(ak)
                    .build();

            int attempts = 6;
            long backoffMs = 500;
            while (attempts-- > 0) {
                try {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    Advisory cve = stub.getAdvisory(request);
                    CveCache.put(advisoryKey, cve.getAliasesList());
                    if (cve.getAliasesList().isEmpty()) {
                        CvesForDep.add(advisoryKey);
                    } else {
                    CvesForDep.add(cve.getAliases(0));
                    }
                } catch (StatusRuntimeException e) {
                    Status.Code code = e.getStatus().getCode();
                    boolean isRateLimited = code == Status.Code.UNAVAILABLE || code == Status.Code.RESOURCE_EXHAUSTED;
                    if (isRateLimited && attempts > 0) {
                        java.lang.System.out.println("deps.dev " + code + " for " + advisoryKeys + ", retrying in " + backoffMs + "ms (" + attempts + " left)...");
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        backoffMs *= 2;
                    } else if (isRateLimited) {
                        java.lang.System.out.println("deps.dev rate limited for " + advisoryKeys + " after retries, giving up.");
                        return null;
                    } else {
                        java.lang.System.out.println("deps.dev error for " + advisoryKeys + ": " + e.getStatus());
                        return null;
                    }
                }
            }
        }
        return CvesForDep;
    }

    //TODO convert this to use eol.date. Change sample project to point a Spring Petclinic
    public boolean isDeprecated(String groupId, String artifactId, String versionString) throws IOException, InterruptedException {

        HttpClient httpClient = newHttpClient();
        String url = "https://api.deps.dev/v3/systems/maven/packages/" + groupId + "%3A" + artifactId + "/versions/" + versionString;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        boolean isDeprecated = !resp.body().contains("isDeprecated\":false");
        return isDeprecated;
    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

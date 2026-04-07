package com.github.qmulda.dependencyanalyser.dependencyhandler;

import deps_dev.v3.InsightsGrpc;
import deps_dev.v3.Api.Dependencies;
import deps_dev.v3.Api.GetDependenciesRequest;
import deps_dev.v3.Api.VersionKey;
import deps_dev.v3.Api.System;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DepsDevClient {

    private static final String HOST = "api.deps.dev";
    private static final int PORT = 443;

    private final ManagedChannel channel;
    private final InsightsGrpc.InsightsBlockingStub stub;
    private final Map<String, Dependencies> cache = new HashMap<>();

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
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        try {
            VersionKey vk = VersionKey.newBuilder()
                    .setSystem(System.MAVEN)
                    .setName(groupId + ":" + artifactId)
                    .setVersion(version)
                    .build();

            GetDependenciesRequest request = GetDependenciesRequest.newBuilder()
                    .setVersionKey(vk)
                    .build();

            Dependencies dependencies = stub.getDependencies(request);
            cache.put(cacheKey, dependencies);
            return dependencies;
        } catch (StatusRuntimeException e) {
            java.lang.System.out.println("deps.dev gRPC error for " + cacheKey + ": " + e.getStatus());
            return null;
        }
    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

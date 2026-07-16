package com.jvibe.monitor;

import io.avaje.jsonb.Json;

@Json
public record JvmMetrics(
    long heapUsedBytes,
    long heapCommittedBytes,
    long nonHeapUsedBytes,
    long uptimeMillis,
    long gcCount,
    long gcTimeMillis,
    int threadCount,
    int daemonThreadCount,
    int runnableThreads,
    int blockedThreads,
    int waitingThreads,
    int timedWaitingThreads) {
}

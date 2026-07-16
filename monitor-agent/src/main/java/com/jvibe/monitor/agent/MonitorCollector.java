package com.jvibe.monitor.agent;

import com.jvibe.monitor.JvmMetrics;
import com.jvibe.monitor.MonitorRegistration;
import com.jvibe.monitor.MonitorSnapshot;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;

public final class MonitorCollector {
  private final MonitorConfig config;
  private final long startedAt = System.currentTimeMillis();

  public MonitorCollector(MonitorConfig config) {
    this.config = config;
  }

  public MonitorRegistration registration() {
    var runtime = ManagementFactory.getRuntimeMXBean();
    return new MonitorRegistration(
        config.serviceId(),
        config.serviceName(),
        config.serviceUrl(),
        MonitorConfig.hostName(),
        System.getProperty("java.version"),
        runtime.getName(),
        Instant.ofEpochMilli(startedAt).toString());
  }

  public MonitorSnapshot snapshot() {
    return new MonitorSnapshot(
        Instant.now().toString(),
        ManagementFactory.getRuntimeMXBean().getName(),
        System.getProperty("java.version"),
        MonitorConfig.hostName(),
        jvmMetrics());
  }

  private JvmMetrics jvmMetrics() {
    var memory = ManagementFactory.getMemoryMXBean();
    var heap = memory.getHeapMemoryUsage();
    var nonHeap = memory.getNonHeapMemoryUsage();
    var threads = ManagementFactory.getThreadMXBean();
    var states = threadStates(threads);
    return new JvmMetrics(
        heap.getUsed(),
        heap.getCommitted(),
        nonHeap.getUsed(),
        ManagementFactory.getRuntimeMXBean().getUptime(),
        gcCount(),
        gcTimeMillis(),
        threads.getThreadCount(),
        threads.getDaemonThreadCount(),
        states.runnable(),
        states.blocked(),
        states.waiting(),
        states.timedWaiting());
  }

  private static long gcCount() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(GarbageCollectorMXBean::getCollectionCount)
        .filter(value -> value > 0)
        .sum();
  }

  private static long gcTimeMillis() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(GarbageCollectorMXBean::getCollectionTime)
        .filter(value -> value > 0)
        .sum();
  }

  private static ThreadStates threadStates(ThreadMXBean threads) {
    var runnable = 0;
    var blocked = 0;
    var waiting = 0;
    var timedWaiting = 0;
    for (ThreadInfo info : threads.getThreadInfo(threads.getAllThreadIds(), 0)) {
      if (info == null) {
        continue;
      }
      switch (info.getThreadState()) {
        case RUNNABLE -> runnable++;
        case BLOCKED -> blocked++;
        case WAITING -> waiting++;
        case TIMED_WAITING -> timedWaiting++;
        default -> {
        }
      }
    }
    return new ThreadStates(runnable, blocked, waiting, timedWaiting);
  }

  private record ThreadStates(int runnable, int blocked, int waiting, int timedWaiting) {}
}

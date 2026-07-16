package com.jvibe.monitor.agent;

import java.util.concurrent.atomic.AtomicReference;

public final class JvibeMonitor {
  private static final AtomicReference<MonitorReporter> REPORTER = new AtomicReference<>();

  private JvibeMonitor() {}

  public static boolean start() {
    return start(MonitorConfig.load());
  }

  public static boolean start(MonitorConfig config) {
    var reporter = new MonitorReporter(config);
    if (!REPORTER.compareAndSet(null, reporter)) {
      return false;
    }
    reporter.start();
    return true;
  }

  public static void stop() {
    var reporter = REPORTER.getAndSet(null);
    if (reporter != null) {
      reporter.close();
    }
  }
}

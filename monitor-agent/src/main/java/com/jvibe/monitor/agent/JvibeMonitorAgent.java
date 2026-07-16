package com.jvibe.monitor.agent;

import java.lang.instrument.Instrumentation;

public final class JvibeMonitorAgent {
  private JvibeMonitorAgent() {}

  public static void premain(String agentArgs, Instrumentation instrumentation) {
    JvibeMonitor.start();
  }

  public static void agentmain(String agentArgs, Instrumentation instrumentation) {
    JvibeMonitor.start();
  }
}

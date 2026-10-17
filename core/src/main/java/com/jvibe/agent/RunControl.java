package com.jvibe.agent;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One run's cancellation, deadline, and currently owned provider response. */
public final class RunControl implements AutoCloseable {
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final AtomicReference<AutoCloseable> response = new AtomicReference<>();
  private final Duration maxRuntime;
  private final Duration toolTimeout;
  private volatile String reason = "Run cancelled";
  private volatile Thread owner;
  private Thread timer;
  private boolean started;

  public RunControl() { this(Duration.ofMinutes(10), Duration.ofSeconds(120)); }

  public RunControl(Duration maxRuntime, Duration toolTimeout) {
    if (maxRuntime.isNegative() || maxRuntime.isZero() || toolTimeout.isNegative() || toolTimeout.isZero()) {
      throw new IllegalArgumentException("Run and tool timeouts must be positive");
    }
    this.maxRuntime = maxRuntime;
    this.toolTimeout = toolTimeout;
  }

  public AtomicBoolean cancelled() { return cancelled; }
  public Duration toolTimeout() { return toolTimeout; }
  public String reason() { return reason; }

  synchronized void start() {
    if (started) return;
    started = true;
    owner = Thread.currentThread();
    if (cancelled.get()) return;
    timer = Thread.ofVirtual().name("jvibe-run-deadline").start(() -> {
      try {
        Thread.sleep(maxRuntime);
        cancel("Run timed out after " + maxRuntime.toSeconds() + "s");
      } catch (InterruptedException ignored) { }
    });
  }

  public void cancel() { cancel("Run cancelled"); }

  private synchronized void cancel(String message) {
    if (cancelled.get()) return;
    reason = message;
    cancelled.set(true);
    var thread = owner;
    if (thread != null && thread != Thread.currentThread()) thread.interrupt();
    // Closing an HTTP body can block briefly; never do that on Swing's event thread.
    Thread.ofVirtual().name("jvibe-response-close").start(this::closeResponse);
  }

  void track(AutoCloseable resource) {
    response.set(resource);
    if (cancelled.get()) closeResponse();
  }

  void closeResponse() {
    var resource = response.getAndSet(null);
    if (resource != null) {
      try { resource.close(); } catch (Exception ignored) { }
    }
  }

  @Override public synchronized void close() {
    owner = null;
    if (timer != null) timer.interrupt();
    closeResponse();
  }
}

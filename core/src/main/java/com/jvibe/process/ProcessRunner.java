package com.jvibe.process;

import jakarta.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs commands with bounded output, concurrent pipe handling, and process-tree cleanup. */
@Singleton
public final class ProcessRunner {
  private static final int MAX_CAPTURE_BYTES = 4 * 1024 * 1024;
  private static final long POLL_MILLIS = 25;

  public ProcessResult run(List<String> command, Path directory, byte[] input,
      Duration timeout, AtomicBoolean cancelled) throws IOException, InterruptedException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Command interrupted");
    if (cancelled.get()) return new ProcessResult(-1, "", "", ProcessResult.Status.CANCELLED);
    if (timeout.isNegative() || timeout.isZero()) return new ProcessResult(124, "", "", ProcessResult.Status.TIMED_OUT);
    var process = new ProcessBuilder(command).directory(directory.toFile()).start();
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    var stdout = new Capture();
    var stderr = new Capture();
    var descendants = new LinkedHashSet<ProcessHandle>();
    var readOut = executor.submit(() -> { stdout.read(process.getInputStream()); return null; });
    var readErr = executor.submit(() -> { stderr.read(process.getErrorStream()); return null; });
    var write = executor.submit(() -> {
      try (var stream = process.getOutputStream()) { stream.write(input); }
      return null;
    });
    long deadline = System.nanoTime() + timeout.toNanos();
    var status = ProcessResult.Status.EXITED;
    try {
      while (true) {
        process.descendants().forEach(descendants::add);
        if (cancelled.get()) { status = ProcessResult.Status.CANCELLED; break; }
        if (System.nanoTime() >= deadline) { status = ProcessResult.Status.TIMED_OUT; break; }
        if (!process.isAlive() && readOut.isDone() && readErr.isDone() && write.isDone()) break;
        Thread.sleep(POLL_MILLIS);
      }
      if (status != ProcessResult.Status.EXITED) terminate(process, descendants);
      else {
        check(readOut);
        check(readErr);
        // A rejected command may close stdin early; its exit status/error is the useful diagnostic.
        if (process.exitValue() == 0) check(write);
      }
      return new ProcessResult(status == ProcessResult.Status.TIMED_OUT ? 124
          : status == ProcessResult.Status.CANCELLED ? -1 : process.exitValue(),
          stdout.text(), stderr.text(), status);
    } finally {
      terminate(process, descendants);
      close(process.getOutputStream());
      close(process.getInputStream());
      close(process.getErrorStream());
      executor.shutdownNow();
    }
  }

  private static void check(Future<?> future) throws IOException, InterruptedException {
    try { future.get(); }
    catch (java.util.concurrent.ExecutionException e) {
      throw new IOException("Unable to handle command stream", e.getCause());
    }
  }

  private static void terminate(Process process, java.util.Set<ProcessHandle> descendants) {
    process.descendants().forEach(descendants::add);
    descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
    process.destroy();
    try { process.waitFor(200, TimeUnit.MILLISECONDS); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    if (process.isAlive()) process.destroyForcibly();
  }

  private static void close(java.io.Closeable stream) {
    try { stream.close(); } catch (IOException ignored) { }
  }

  private static final class Capture {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private boolean truncated;

    void read(InputStream stream) throws IOException {
      byte[] buffer = new byte[8192];
      for (int count; (count = stream.read(buffer)) != -1;) {
        synchronized (this) {
          int remaining = MAX_CAPTURE_BYTES - bytes.size();
          bytes.write(buffer, 0, Math.min(remaining, count));
          truncated |= count > remaining;
        }
      }
    }

    synchronized String text() {
      return bytes.toString(StandardCharsets.UTF_8) + (truncated ? "\n[output truncated at 4 MiB]" : "");
    }
  }
}

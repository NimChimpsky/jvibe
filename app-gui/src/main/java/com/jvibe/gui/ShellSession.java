package com.jvibe.gui;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ShellSession implements TtyConnector, AutoCloseable {
  private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(1);

  private final Path workingDirectory;
  private final List<String> command;
  private final int initialColumns;
  private final int initialRows;
  private volatile PtyProcess process;
  private volatile InputStreamReader reader;
  private volatile OutputStream writer;

  ShellSession(Path workingDirectory, List<String> command) {
    this(workingDirectory, command, 80, 24);
  }

  ShellSession(Path workingDirectory, List<String> command, int columns, int rows) {
    this.workingDirectory = workingDirectory;
    this.command = List.copyOf(command);
    this.initialColumns = Math.max(20, columns);
    this.initialRows = Math.max(4, rows);
  }

  synchronized void start() throws IOException {
    if (isConnected()) {
      return;
    }
    var environment = new HashMap<>(System.getenv());
    environment.put("TERM", "xterm-256color");
    environment.put("COLORTERM", "truecolor");
    process = new PtyProcessBuilder()
        .setCommand(command.toArray(String[]::new))
        .setEnvironment(environment)
        .setDirectory(workingDirectory.toString())
        .setRedirectErrorStream(true)
        .setInitialColumns(initialColumns)
        .setInitialRows(initialRows)
        .start();
    reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
    writer = process.getOutputStream();
  }

  void writeLine(String line) throws IOException {
    write(line + "\n");
  }

  @Override
  public int read(char[] buffer, int offset, int length) throws IOException {
    var currentReader = reader;
    if (currentReader == null) {
      return -1;
    }
    return currentReader.read(buffer, offset, length);
  }

  @Override
  public synchronized void write(byte[] bytes) throws IOException {
    if (writer == null) {
      return;
    }
    writer.write(bytes);
    writer.flush();
  }

  @Override
  public void write(String string) throws IOException {
    write(string.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public synchronized boolean isConnected() {
    return process != null && process.isAlive();
  }

  @Override
  public synchronized void resize(TermSize termSize) {
    if (process != null && process.isAlive()) {
      process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
    }
  }

  synchronized WinSize windowSize() throws IOException {
    if (process == null) {
      return new WinSize(initialColumns, initialRows);
    }
    return process.getWinSize();
  }

  @Override
  public synchronized int waitFor() throws InterruptedException {
    return process == null ? 0 : process.waitFor();
  }

  @Override
  public synchronized boolean ready() throws IOException {
    return reader != null && reader.ready();
  }

  @Override
  public String getName() {
    return String.join(" ", command);
  }

  @Override
  public synchronized void close() {
    if (process == null) {
      return;
    }
    var stopped = process;
    process = null;
    reader = null;
    writer = null;
    terminate(stopped);
  }

  private void terminate(PtyProcess target) {
    target.destroy();
    try {
      if (!target.waitFor(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
        target.destroyForcibly();
        target.waitFor(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      target.destroyForcibly();
    }
  }
}

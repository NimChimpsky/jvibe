package com.jvibe.git;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import jakarta.inject.Singleton;

@Singleton
public final class GitService {
  private final com.jvibe.process.ProcessRunner processes;

  public GitService(com.jvibe.process.ProcessRunner processes) {
    this.processes = processes;
  }

  public GitCommandResult run(Path workspace, String... args) throws IOException, InterruptedException {
    return run(workspace, new byte[0], Duration.ofSeconds(60), new java.util.concurrent.atomic.AtomicBoolean(), args);
  }

  public GitCommandResult run(Path workspace, byte[] input, Duration timeout,
      java.util.concurrent.atomic.AtomicBoolean cancelled, String... args) throws IOException, InterruptedException {
    var command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    var result = processes.run(java.util.List.of(command), workspace, input, timeout, cancelled);
    var error = result.stderr() + switch (result.status()) {
      case EXITED -> "";
      case CANCELLED -> "\ngit command cancelled";
      case TIMED_OUT -> "\ngit command timed out";
    };
    return new GitCommandResult(result.exitCode(), result.stdout(), error);
  }
}

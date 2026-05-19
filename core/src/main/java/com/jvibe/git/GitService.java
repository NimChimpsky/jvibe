package com.jvibe.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import jakarta.inject.Singleton;

@Singleton
public final class GitService {
  public GitCommandResult run(Path workspace, String... args) throws IOException, InterruptedException {
    var command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    var process = new ProcessBuilder(command).directory(workspace.toFile()).start();
    var finished = process.waitFor(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      return new GitCommandResult(124, "", "git command timed out");
    }
    var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new GitCommandResult(process.exitValue(), stdout, stderr);
  }
}

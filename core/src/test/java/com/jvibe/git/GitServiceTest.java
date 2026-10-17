package com.jvibe.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitServiceTest {
  @TempDir
  Path temp;

  @Test
  void drainsLargeCommandOutputWithoutBlocking() throws Exception {
    assumeTrue(new ProcessBuilder("git", "--version").start().waitFor() == 0);
    var git = new GitService(new com.jvibe.process.ProcessRunner());
    assertEquals(0, git.run(temp, "init").exitCode());
    var file = temp.resolve("large.txt");
    Files.writeString(file, "before\n");
    assertEquals(0, git.run(temp, "add", "large.txt").exitCode());
    assertEquals(0, git.run(
        temp,
        "-c", "user.name=JVibe Test",
        "-c", "user.email=test@jvibe.invalid",
        "commit", "-m", "fixture").exitCode());
    Files.writeString(file, "changed-line\n".repeat(20_000));

    var diff = git.run(temp, "diff", "--", "large.txt");

    assertEquals(0, diff.exitCode());
    assertTrue(diff.stdout().length() > 100_000);
  }
}

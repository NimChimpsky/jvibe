package com.jvibe.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceTest {
  @TempDir java.nio.file.Path home;

  @Test
  void bootstrapCreatesDefaultFiles() throws Exception {
    var paths = JvibePaths.under(home);
    new ConfigService(paths).bootstrap();

    assertTrue(Files.exists(paths.config()));
    assertTrue(Files.exists(paths.providers()));
    assertTrue(Files.exists(paths.systemPrompt()));
    assertTrue(Files.isDirectory(paths.sessions()));
    assertTrue(Files.isDirectory(paths.tools()));
    assertTrue(Files.isDirectory(paths.logs()));
  }
}

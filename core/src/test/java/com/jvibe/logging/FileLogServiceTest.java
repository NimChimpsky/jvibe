package com.jvibe.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLogServiceTest {
  @TempDir Path home;

  @Test
  void loggingDoesNotRewriteConfigurationOrPrompts() throws Exception {
    var paths = JvibePaths.under(home);
    var config = new ConfigService(paths);
    config.bootstrap();
    Files.writeString(paths.chatPrompt(), "sentinel");
    var originalConfig = Files.readString(paths.config());
    var service = new FileLogService(config);
    service.event("INFO", "message");
    assertTrue(service.readAll().contains("message"));
    assertEquals("sentinel", Files.readString(paths.chatPrompt()));
    assertEquals(originalConfig, Files.readString(paths.config()));
  }

  @Test
  void writesEventsPromptsAndResponsesToJvibeLog() throws Exception {
    var service = new FileLogService(new ConfigService(JvibePaths.under(home)));

    service.event("INFO", "Started");
    service.prompt("jvibe", "jvibe-coder", "hello");
    service.assistant("jvibe", "jvibe-coder", "hi there");

    var log = Files.readString(JvibePaths.under(home).home().resolve("jvibe.log"));
    assertTrue(log.contains("INFO"));
    assertTrue(log.contains("Started"));
    assertTrue(log.contains("PROMPT jvibe/jvibe-coder"));
    assertTrue(log.contains("hello"));
    assertTrue(log.contains("ASSISTANT jvibe/jvibe-coder"));
    assertTrue(log.contains("hi there"));
  }

}

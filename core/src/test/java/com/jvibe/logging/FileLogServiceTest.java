package com.jvibe.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLogServiceTest {
  @TempDir Path home;

  @Test
  void writesEventsPromptsAndResponsesToJvibeLog() throws Exception {
    var service = new FileLogService(new ConfigService(JvibePaths.under(home)));

    service.event("INFO", "Started");
    service.prompt("deepseek", "deepseek-v4-flash", "hello");
    service.assistant("deepseek", "deepseek-v4-flash", "hi there");

    var log = Files.readString(JvibePaths.under(home).home().resolve("jvibe.log"));
    assertTrue(log.contains("INFO"));
    assertTrue(log.contains("Started"));
    assertTrue(log.contains("PROMPT deepseek/deepseek-v4-flash"));
    assertTrue(log.contains("hello"));
    assertTrue(log.contains("ASSISTANT deepseek/deepseek-v4-flash"));
    assertTrue(log.contains("hi there"));
  }
}

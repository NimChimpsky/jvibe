package com.jvibe.logging;

import com.jvibe.config.ConfigService;
import com.jvibe.session.Mode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import jakarta.inject.Singleton;

@Singleton
public final class FileLogService {
  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
  private final ConfigService configService;

  public FileLogService(ConfigService configService) {
    this.configService = configService;
  }

  public void event(String level, String message) {
    write("%s  %-5s  %s%n".formatted(timestamp(), level, clean(message)));
  }

  public void prompt(Mode mode, String text) {
    block("PROMPT " + mode.label(), text);
  }

  public void assistant(Mode mode, String text) {
    block("ASSISTANT " + mode.label(), text);
  }

  public Path logFile() {
    return configService.paths().log();
  }

  private void block(String title, String text) {
    write("""
        %s  %s
        %s
        %n""".formatted(timestamp(), title, clean(text)));
  }

  private synchronized void write(String text) {
    try {
      var file = logFile();
      Files.createDirectories(file.getParent());
      Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException ignored) {
      // File logging must never break the GUI.
    }
  }

  private static String timestamp() {
    return OffsetDateTime.now().format(TIMESTAMP);
  }

  private static String clean(String value) {
    return value == null ? "" : value.stripTrailing();
  }
}

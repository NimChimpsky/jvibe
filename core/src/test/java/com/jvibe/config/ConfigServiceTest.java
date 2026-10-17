package com.jvibe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.api.Thinking;
import com.jvibe.session.Mode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceTest {
  @TempDir Path home;

  @Test
  void defaultsWithoutAFileAndCreatesOnlyTheScratchDirectory() throws Exception {
    var paths = JvibePaths.under(home);
    var service = new ConfigService(paths);
    service.bootstrap();

    assertTrue(Files.isDirectory(paths.tmp()));
    assertFalse(Files.exists(paths.config()));
    assertEquals("DRACULA", service.config().theme());
    assertEquals(Thinking.MEDIUM, service.config().thinkingLevel());
    assertEquals(ModeSettings.DEFAULT, service.config().mode(Mode.SERVERS));
  }

  @Test
  void settingsRoundTripPerMode() throws Exception {
    var service = new ConfigService(JvibePaths.under(home));
    service.updateConfig(current -> current.withProjectPaths(List.of("/project")).withTheme("DARCULA"));
    service.updateConfig(current -> current.withAssistant(Thinking.HIGH, 30, Map.of(
        Mode.CHAT, new ModeSettings("Use Australian English.", false),
        Mode.DATABASES, new ModeSettings("Oracle 19c.", true))));
    service.updateConfig(current -> current.withMainTabOrder(List.of("build", "chat")));

    var saved = service.config();
    assertEquals("DARCULA", saved.theme());
    assertEquals(30, saved.maxToolIterations());
    assertEquals(Thinking.HIGH, saved.thinkingLevel());
    assertEquals("Use Australian English.", saved.mode(Mode.CHAT).instructions());
    assertTrue(saved.mode(Mode.DATABASES).askBeforeRunning());
    assertFalse(saved.mode(Mode.BUILD).askBeforeRunning());
    assertEquals(List.of("/project"), saved.projectPaths());
    assertEquals(List.of("build", "chat"), saved.mainTabOrder());
  }

  @Test
  void legacySettingsAreIgnored() {
    var config = JvibeConfig.fromJson(
        "{\"customInstructions\":\"old\",\"defaultProvider\":\"other\",\"maxToolIterations\":7}");
    assertEquals(7, config.maxToolIterations());
    assertFalse(config.toJson().contains("customInstructions"));
    assertFalse(config.toJson().contains("defaultProvider"));
  }
}

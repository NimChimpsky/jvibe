package com.jvibe.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceTest {
  @TempDir Path home;

  @Test void bootstrapsOnlyCurrentModePromptsAndDefaultTabs() throws Exception {
    var paths = JvibePaths.under(home);
    var service = new ConfigService(paths);
    service.bootstrap();
    assertTrue(Files.exists(paths.chatPrompt()));
    assertTrue(Files.exists(paths.workspacePrompt()));
    assertTrue(Files.exists(paths.remoteSshPrompt()));
    assertTrue(Files.exists(paths.jdbcPrompt()));
    assertEquals(List.of("chat", "build", "servers", "databases", "jvibe"), service.config().mainTabOrder());
    assertEquals("DRACULA", service.config().theme());
    assertTrue(service.chatPrompt().contains("Never create, edit, delete"));
    assertTrue(service.workspacePrompt().contains("Git repository"));
    assertFalse(service.chatPrompt().contains("File Output"));
  }

  @Test void settingsRoundTripAndUpdatesPreserveProjects() throws Exception {
    var service = new ConfigService(JvibePaths.under(home));
    service.saveConfig(JvibeConfig.defaults().withProjectPaths(List.of("/project"))
        .withTheme("DARCULA").withMaxToolIterations(30).withCustomInstructions("Use Australian English."));
    service.updateConfig(current -> current.withMainTabOrder(List.of("build", "chat")));
    var saved = service.config();
    assertEquals("DARCULA", saved.theme());
    assertEquals(30, saved.maxToolIterations());
    assertEquals(List.of("/project"), saved.projectPaths());
    assertEquals(List.of("build", "chat"), saved.mainTabOrder());
    assertTrue(service.chatPrompt().contains("Use Australian English."));
    assertTrue(service.workspacePrompt().contains("Use Australian English."));
    assertTrue(service.chatPrompt().contains("Never create, edit, delete"));
  }

  @Test void removedSettingsAreNotRetainedInSchema() {
    var config = JvibeConfig.fromJson("{\"defaultProvider\":\"other\",\"monitoringEnabled\":true,\"gatewayPort\":9999}");
    assertFalse(config.toJson().contains("defaultProvider"));
    assertFalse(config.toJson().contains("monitoring"));
    assertFalse(config.toJson().contains("gateway"));
  }
}

package com.jvibe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    assertTrue(Files.exists(paths.chatPrompt()));
    assertTrue(Files.exists(paths.workspacePrompt()));
    assertTrue(Files.isDirectory(paths.sessions()));
    assertTrue(Files.isDirectory(paths.tools()));
    assertTrue(Files.isDirectory(paths.logs()));
    assertTrue(Files.isDirectory(paths.tmp()));
  }

  @Test
  void savesAndLoadsGeneralConfig() throws Exception {
    var service = new ConfigService(JvibePaths.under(home));
    var config = new JvibeConfig("openai", "gpt-test", 4, "DRACULA", "supervised", false, "127.0.0.1", 8737, java.util.List.of());

    service.saveConfig(config);

    var saved = service.config();
    assertEquals("DRACULA", saved.theme());
    assertEquals(4, saved.maxToolIterations());
  }

  @Test
  void savesAndLoadsAutonomousConfig() throws Exception {
    var service = new ConfigService(JvibePaths.under(home));
    var config = new JvibeConfig(
        "openai",
        "gpt-test",
        4,
        "DRACULA",
        "autonomous",
        true,
        "0.0.0.0",
        9999,
        java.util.List.of(new com.jvibe.deployment.DeploymentProfile(
            "prod",
            new com.jvibe.deployment.DeploymentTarget("ssh", "example.com", "deploy", "/opt/jvibe", "/srv/app"),
            "openai",
            "gpt",
            "autonomous",
            true,
            "127.0.0.1",
            8737)));

    service.saveConfig(config);

    var saved = service.config();
    assertEquals("autonomous", saved.autonomyMode());
    assertEquals(true, saved.gatewayEnabled());
    assertEquals(9999, saved.gatewayPort());
    assertEquals("prod", saved.deploymentProfiles().getFirst().name());
  }

  @Test
  void bootstrapAlwaysWritesBuiltInPrompts() throws Exception {
    var paths = JvibePaths.under(home);
    Files.createDirectories(paths.chatPrompt().getParent());
    Files.writeString(paths.chatPrompt(), "custom chat");
    Files.writeString(paths.workspacePrompt(), "custom workspace");
    var service = new ConfigService(paths);

    service.bootstrap();

    assertTrue(service.chatPrompt().contains("under ~/.jvibe/tmp"));
    assertTrue(service.chatPrompt().contains("relative output path"));
    assertTrue(service.workspacePrompt().contains("workspace root"));
    assertTrue(service.workspacePrompt().contains("create summary.md"));
  }

  @Test
  void bootstrapDeletesLegacySystemPrompt() throws Exception {
    var paths = JvibePaths.under(home);
    Files.createDirectories(paths.home().resolve("prompts"));
    Files.writeString(paths.home().resolve("prompts").resolve("system.md"), "legacy");

    new ConfigService(paths).bootstrap();

    assertTrue(Files.notExists(paths.home().resolve("prompts").resolve("system.md")));
  }

  @Test
  void oldConfigWithoutThemeUsesDefaultTheme() {
    var config = JvibeConfig.fromJson("""
        {"defaultProvider":"openai","defaultModel":"gpt","maxToolIterations":5}
        """);

    assertEquals("FLATLAF_DARK", config.theme());
  }

  @Test
  void bootstrapRewritesConfigWithoutRetiredApprovalFields() throws Exception {
    var paths = JvibePaths.under(home);
    Files.createDirectories(paths.config().getParent());
    Files.writeString(paths.config(), """
        {"defaultProvider":"openai","defaultModel":"gpt","requireApproval":true,"allowedHighRiskTools":["*"],"maxToolIterations":5,"theme":"ONE_DARK"}
        """);

    new ConfigService(paths).bootstrap();

    var rewritten = Files.readString(paths.config());
    assertTrue(!rewritten.contains("requireApproval"), rewritten);
    assertTrue(!rewritten.contains("allowedHighRiskTools"), rewritten);
    assertEquals("ONE_DARK", JvibeConfig.fromJson(rewritten).theme());
  }
}

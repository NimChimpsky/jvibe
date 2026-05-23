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
    assertTrue(Files.exists(paths.systemPrompt()));
    assertTrue(Files.isDirectory(paths.sessions()));
    assertTrue(Files.isDirectory(paths.tools()));
    assertTrue(Files.isDirectory(paths.logs()));
    assertTrue(Files.isDirectory(paths.tmp()));
  }

  @Test
  void savesAndLoadsGeneralConfig() throws Exception {
    var service = new ConfigService(JvibePaths.under(home));
    var config = new JvibeConfig("openai", "gpt-test", false, 4, "DRACULA", "supervised", java.util.List.of(), false, "127.0.0.1", 8737, java.util.List.of());

    service.saveConfig(config);

    var saved = service.config();
    assertEquals("DRACULA", saved.theme());
    assertEquals(false, saved.requireApproval());
    assertEquals(4, saved.maxToolIterations());
  }

  @Test
  void savesAndLoadsAutonomousConfig() throws Exception {
    var service = new ConfigService(JvibePaths.under(home));
    var config = new JvibeConfig(
        "openai",
        "gpt-test",
        false,
        4,
        "DRACULA",
        "autonomous",
        java.util.List.of("*"),
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
    assertEquals(java.util.List.of("*"), saved.allowedHighRiskTools());
    assertEquals(true, saved.gatewayEnabled());
    assertEquals(9999, saved.gatewayPort());
    assertEquals("prod", saved.deploymentProfiles().getFirst().name());
  }

  @Test
  void savesAndLoadsSystemPrompt() throws Exception {
    var service = new ConfigService(JvibePaths.under(home));

    service.saveSystemPrompt("custom prompt");

    assertEquals("custom prompt", service.systemPrompt());
  }

  @Test
  void oldConfigWithoutThemeUsesDefaultTheme() {
    var config = JvibeConfig.fromJson("""
        {"defaultProvider":"openai","defaultModel":"gpt","requireApproval":true,"maxToolIterations":5}
        """);

    assertEquals("FLATLAF_DARK", config.theme());
  }
}

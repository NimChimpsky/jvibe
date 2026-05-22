package com.jvibe.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.JvibePaths;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderConfigServiceTest {
  @TempDir java.nio.file.Path home;

  @Test
  void savesAndLoadsProviderSettings() {
    var service = new ProviderConfigService(JvibePaths.under(home));
    service.save(new ProviderSettings("openai", "https://api.openai.com/v1", "sk-test-secret", "gpt-5.4", "high", true));

    var settings = service.settings("openai");

    assertEquals("sk-test-secret", settings.apiKey());
    assertEquals("gpt-5.4", settings.selectedModel());
    assertEquals("high", settings.thinking());
    assertTrue(settings.enabled());
  }

  @Test
  void bootstrapsDefaultProviders() {
    var service = new ProviderConfigService(JvibePaths.under(home));

    var config = service.load();

    assertFalse(config.providers().isEmpty());
    assertTrue(config.providers().stream().anyMatch(provider -> provider.provider().equals("deepseek")));
    assertTrue(config.providers().stream().anyMatch(provider -> provider.provider().equals("qwen")));
    assertTrue(config.providers().stream().anyMatch(provider -> provider.provider().equals("openrouter")));
    assertTrue(Files.exists(JvibePaths.under(home).providers()));
  }

  @Test
  void qwenRegionBaseUrlPersists() {
    var service = new ProviderConfigService(JvibePaths.under(home));
    service.save(new ProviderSettings("qwen", QwenProviderClient.CHINA_BASE_URL, "key", "qwen3-coder-plus", "on", true));

    assertEquals(QwenProviderClient.CHINA_BASE_URL, service.settings("qwen").baseUrl());
  }
}

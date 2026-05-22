package com.jvibe.provider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.agent.AgentRunner;
import com.jvibe.session.SessionFactory;
import com.jvibe.tool.ToolRegistryFactory;
import io.avaje.inject.BeanScope;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderDiTest {
  @Test
  void coreProviderGraphIsInjectable() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(ProviderRegistry.class));
      assertNotNull(scope.get(ProviderConfigService.class));
      assertNotNull(scope.get(AgentRunner.class));
      assertNotNull(scope.get(ToolRegistryFactory.class));
      assertNotNull(scope.get(SessionFactory.class));
    }
  }

  @Test
  void registryReceivesAllProviderClientBeans() {
    try (var scope = BeanScope.builder().build()) {
      var providerIds = scope.get(ProviderRegistry.class).clients().stream()
          .map(ProviderClient::providerId)
          .collect(java.util.stream.Collectors.toSet());

      assertTrue(providerIds.containsAll(Set.of("local", "openai", "anthropic", "mistral", "deepseek", "qwen", "openrouter")));
    }
  }
}

package com.jvibe.provider;

import com.jvibe.config.JvibePaths;
import com.jvibe.config.SecretRedactor;
import io.avaje.jsonb.Jsonb;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Singleton;

@Singleton
public final class ProviderConfigService {
  private static final Jsonb JSONB = Jsonb.instance();
  private final JvibePaths paths;
  private final Map<String, ProviderDefinition> definitions;

  public ProviderConfigService(JvibePaths paths) {
    this.paths = paths;
    this.definitions = defaultDefinitions();
  }

  public List<ProviderDefinition> definitions() {
    return definitions.values().stream().toList();
  }

  public Optional<ProviderDefinition> definition(String provider) {
    return Optional.ofNullable(definitions.get(provider));
  }

  public ProvidersConfig load() {
    bootstrap();
    try {
      var config = JSONB.type(ProvidersConfig.class).fromJson(Files.readString(paths.providers()));
      return mergeDefaults(config);
    } catch (Exception e) {
      return mergeDefaults(new ProvidersConfig(List.of()));
    }
  }

  public ProviderSettings settings(String provider) {
    return load().providers().stream()
        .filter(settings -> settings.provider().equals(provider))
        .findFirst()
        .orElseGet(() -> defaultSettings(definitions.get(provider)));
  }

  public ProviderSettings effectiveSettings(String provider) {
    var settings = settings(provider);
    if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
      return settings;
    }
    var definition = definitions.get(provider);
    if (definition == null || definition.apiKeyEnv() == null || definition.apiKeyEnv().isBlank()) {
      return settings;
    }
    var env = System.getenv(definition.apiKeyEnv());
    return env == null || env.isBlank() ? settings : settings.withApiKey(env);
  }

  public void save(ProviderSettings updated) {
    bootstrap();
    var providers = new LinkedHashMap<String, ProviderSettings>();
    for (var settings : load().providers()) {
      providers.put(settings.provider(), settings);
    }
    providers.put(updated.provider(), normalise(updated));
    write(new ProvidersConfig(providers.values().stream().sorted(Comparator.comparing(ProviderSettings::provider)).toList()));
  }

  public ProviderSettings masked(ProviderSettings settings) {
    return settings.withApiKey(SecretRedactor.redact(settings.apiKey()));
  }

  public String defaultProvider() {
    return load().providers().stream()
        .filter(ProviderSettings::enabled)
        .findFirst()
        .map(ProviderSettings::provider)
        .orElse("local");
  }

  private ProvidersConfig mergeDefaults(ProvidersConfig config) {
    var providers = new LinkedHashMap<String, ProviderSettings>();
    for (var definition : definitions.values()) {
      providers.put(definition.id(), defaultSettings(definition));
    }
    for (var settings : config.providers()) {
      if (definitions.containsKey(settings.provider())) {
        providers.put(settings.provider(), normalise(settings));
      }
    }
    return new ProvidersConfig(new ArrayList<>(providers.values()));
  }

  private ProviderSettings normalise(ProviderSettings settings) {
    var definition = definitions.get(settings.provider());
    if (definition == null) {
      return settings;
    }
    var baseUrl = blank(settings.baseUrl()) ? definition.baseUrl() : settings.baseUrl();
    var model = blank(settings.selectedModel()) ? definition.defaultModel() : settings.selectedModel();
    var thinking = blank(settings.thinking()) ? definition.thinkingOptions().getFirst().id() : settings.thinking();
    return new ProviderSettings(settings.provider(), baseUrl, settings.apiKey(), model, thinking, settings.enabled());
  }

  private ProviderSettings defaultSettings(ProviderDefinition definition) {
    if (definition == null) {
      return new ProviderSettings("local", "", "", "echo", "none", true);
    }
    var thinking = definition.thinkingOptions().isEmpty() ? "none" : definition.thinkingOptions().getFirst().id();
    return new ProviderSettings(definition.id(), definition.baseUrl(), "", definition.defaultModel(), thinking, "local".equals(definition.id()));
  }

  private void bootstrap() {
    try {
      Files.createDirectories(paths.providers().getParent());
      if (Files.notExists(paths.providers())) {
        write(mergeDefaults(new ProvidersConfig(List.of())));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to bootstrap provider config", e);
    }
  }

  private void write(ProvidersConfig config) {
    try {
      Files.writeString(paths.providers(), JSONB.toJsonPretty(config));
      try {
        Files.setPosixFilePermissions(paths.providers(), Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE));
      } catch (UnsupportedOperationException ignored) {
        // Windows and some filesystems do not support POSIX permissions.
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to save provider config", e);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static Map<String, ProviderDefinition> defaultDefinitions() {
    var openAiThinking = List.of(
        ThinkingOption.of("none", "None"),
        ThinkingOption.of("low", "Low"),
        ThinkingOption.of("medium", "Medium"),
        ThinkingOption.of("high", "High"),
        ThinkingOption.of("xhigh", "Extra High"));
    var basic = List.of(ThinkingOption.of("none", "None"));
    var highOnly = List.of(ThinkingOption.of("none", "None"), ThinkingOption.of("high", "High"));
    var deepSeek = List.of(
        ThinkingOption.of("disabled", "Thinking Off"),
        ThinkingOption.of("high", "Thinking High"),
        ThinkingOption.of("max", "Thinking Max"));
    var qwenThinking = List.of(
        ThinkingOption.of("off", "Off"),
        ThinkingOption.of("on", "On"));
    var jvibeThinking = List.of(
        ThinkingOption.of("none", "None"),
        ThinkingOption.of("low", "Low"),
        ThinkingOption.of("medium", "Medium"),
        ThinkingOption.of("high", "High"));

    var definitions = new LinkedHashMap<String, ProviderDefinition>();
    definitions.put("local", new ProviderDefinition("local", "Local Echo", "", "", "echo", basic));
    definitions.put("jvibe", new ProviderDefinition("jvibe", "Jvibe", JvibeProviderClient.BASE_URL, "JVIBE_API_KEY", "jvibe-coder", jvibeThinking));
    definitions.put("openai", new ProviderDefinition("openai", "OpenAI", "https://api.openai.com/v1", "OPENAI_API_KEY", "gpt-5.4", openAiThinking));
    definitions.put("anthropic", new ProviderDefinition("anthropic", "Anthropic", "https://api.anthropic.com/v1", "ANTHROPIC_API_KEY", "claude-sonnet-4-20250514", List.of(
        ThinkingOption.of("off", "Off"),
        new ThinkingOption("1024", "1K budget", Map.of("budget_tokens", 1024)),
        new ThinkingOption("4096", "4K budget", Map.of("budget_tokens", 4096)),
        new ThinkingOption("10000", "10K budget", Map.of("budget_tokens", 10000)))));
    definitions.put("mistral", new ProviderDefinition("mistral", "Mistral", "https://api.mistral.ai/v1", "MISTRAL_API_KEY", "mistral-large-latest", highOnly));
    definitions.put("deepseek", new ProviderDefinition("deepseek", "DeepSeek", "https://api.deepseek.com", "DEEPSEEK_API_KEY", "deepseek-v4-flash", deepSeek));
    definitions.put("qwen", new ProviderDefinition("qwen", "Qwen", QwenProviderClient.INTERNATIONAL_BASE_URL, "DASHSCOPE_API_KEY", "qwen3-coder-plus", qwenThinking));
    return java.util.Collections.unmodifiableMap(definitions);
  }
}

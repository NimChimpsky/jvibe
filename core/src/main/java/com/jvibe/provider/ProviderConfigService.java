package com.jvibe.provider;

import com.jvibe.config.JvibePaths;
import com.jvibe.config.AtomicFiles;
import com.jvibe.config.SecretRedactor;
import io.avaje.jsonb.Jsonb;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
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

  public synchronized ProvidersConfig load() {
    bootstrap();
    try {
      var config = JSONB.type(ProvidersConfig.class).fromJson(Files.readString(paths.providers()));
      return mergeDefaults(config);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read provider settings: " + paths.providers(), e);
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
    saveAll(List.of(updated));
  }

  public synchronized void saveAll(List<ProviderSettings> updates) {
    for (var updated : updates) {
      if (!definitions.containsKey(updated.provider())) {
        throw new IllegalArgumentException("Unknown provider: " + updated.provider());
      }
    }
    bootstrap();
    var providers = new LinkedHashMap<String, ProviderSettings>();
    for (var settings : load().providers()) {
      providers.put(settings.provider(), settings);
    }
    updates.forEach(updated -> providers.put(updated.provider(), normalise(updated)));
    write(new ProvidersConfig(List.copyOf(providers.values())));
  }

  public ProviderSettings masked(ProviderSettings settings) {
    return settings.withApiKey(SecretRedactor.redact(settings.apiKey()));
  }

  public String defaultProvider() { return "jvibe"; }

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
    var baseUrl = definition.baseUrl();
    var model = blank(settings.selectedModel()) ? definition.defaultModel() : settings.selectedModel();
    var thinking = definition.thinkingOptions().stream().map(ThinkingOption::id)
        .filter(id -> id.equals(settings.thinking())).findFirst().orElse("none");
    return new ProviderSettings(settings.provider(), baseUrl, settings.apiKey(), model, thinking, true);
  }

  private ProviderSettings defaultSettings(ProviderDefinition definition) {
    if (definition == null) {
      throw new IllegalArgumentException("Unknown provider definition");
    }
    var thinking = definition.thinkingOptions().isEmpty() ? "none" : definition.thinkingOptions().getFirst().id();
    return new ProviderSettings(definition.id(), definition.baseUrl(), "", definition.defaultModel(), thinking, "jvibe".equals(definition.id()));
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
      AtomicFiles.writeString(paths.providers(), JSONB.toJsonPretty(config));
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
    return Map.of("jvibe", new ProviderDefinition("jvibe", "JVibe",
        JvibeProviderClient.BASE_URL, "JVIBE_API_KEY", "jvibe-coder",
        List.of(ThinkingOption.of("none", "None"), ThinkingOption.of("low", "Low"),
            ThinkingOption.of("medium", "Medium"), ThinkingOption.of("high", "High"))));
  }
}

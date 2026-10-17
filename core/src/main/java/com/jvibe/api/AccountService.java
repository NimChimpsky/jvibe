package com.jvibe.api;

import com.jvibe.config.AtomicFiles;
import com.jvibe.config.JvibePaths;
import io.avaje.jsonb.Jsonb;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** Persists the JVibe account. {@code JVIBE_API_KEY} overrides the stored key when set. */
@Singleton
public final class AccountService {
  static final String API_KEY_ENV = "JVIBE_API_KEY";

  private final JvibePaths paths;
  private final String environmentKey;

  public AccountService(JvibePaths paths) {
    this(paths, System.getenv(API_KEY_ENV));
  }

  AccountService(JvibePaths paths, String environmentKey) {
    this.paths = paths;
    this.environmentKey = environmentKey == null ? "" : environmentKey.trim();
  }

  public synchronized Account account() {
    try {
      if (Files.exists(paths.account())) {
        return Jsonb.instance().type(Account.class).fromJson(Files.readString(paths.account()));
      }
      return migrateLegacyProviders();
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read " + paths.account(), e);
    }
  }

  public synchronized void save(Account account) {
    try {
      AtomicFiles.writePrivate(paths.account(), Jsonb.instance().toJsonPretty(account));
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to save " + paths.account(), e);
    }
  }

  /** The key used for requests: the environment override, else the stored key. */
  public String apiKey() {
    return environmentKey.isBlank() ? account().apiKey() : environmentKey;
  }

  public boolean usesEnvironmentKey() {
    return !environmentKey.isBlank();
  }

  /** Earlier versions stored the key in providers.json; move it once and delete that file. */
  private Account migrateLegacyProviders() throws IOException {
    if (Files.notExists(paths.legacyProviders())) {
      return Account.NONE;
    }
    var providers = JsonSupport.list(JsonSupport.object(Files.readString(paths.legacyProviders())).get("providers"));
    var key = providers.stream()
        .map(JsonSupport::map)
        .map(provider -> JsonSupport.string(provider.get("apiKey")))
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse("");
    var account = new Account("", key, "");
    if (account.hasApiKey()) save(account);
    Files.deleteIfExists(paths.legacyProviders());
    return account;
  }
}

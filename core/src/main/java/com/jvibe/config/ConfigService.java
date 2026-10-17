package com.jvibe.config;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.UnaryOperator;

/** Loads and saves {@link JvibeConfig}; every read sees the latest file contents. */
@Singleton
public final class ConfigService {
  private final JvibePaths paths;

  public ConfigService(JvibePaths paths) { this.paths = paths; }

  public JvibePaths paths() { return paths; }

  public synchronized JvibeConfig config() throws IOException {
    if (Files.notExists(paths.config())) return JvibeConfig.defaults();
    return JvibeConfig.fromJson(Files.readString(paths.config()));
  }

  public synchronized JvibeConfig updateConfig(UnaryOperator<JvibeConfig> update) throws IOException {
    var updated = update.apply(config());
    AtomicFiles.writeString(paths.config(), updated.toJson());
    return updated;
  }

  /** Creates the JVibe directories; called once at startup. */
  public void bootstrap() throws IOException {
    Files.createDirectories(paths.tmp());
  }
}

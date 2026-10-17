package com.jvibe.config;

import java.nio.file.Path;

/** Files under {@code ~/.jvibe}. */
public record JvibePaths(Path home, Path config, Path account, Path legacyProviders, Path log, Path tmp) {
  public static JvibePaths under(Path userHome) {
    var root = userHome.resolve(".jvibe");
    return new JvibePaths(root, root.resolve("config.json"), root.resolve("account.json"),
        root.resolve("providers.json"), root.resolve("jvibe.log"), root.resolve("tmp"));
  }

  public static JvibePaths userHome() {
    return under(Path.of(System.getProperty("user.home")));
  }
}

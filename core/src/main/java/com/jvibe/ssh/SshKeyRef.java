package com.jvibe.ssh;

import io.avaje.jsonb.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Json
public record SshKeyRef(String id, String path, String format, boolean unlocked) {
  public SshKeyRef {
    path = path == null ? "" : path;
    id = blank(id) ? stableId(path) : id;
    format = blank(format) ? detectFormat(path) : format;
  }

  public static SshKeyRef of(Path path) {
    return new SshKeyRef(stableId(path.toString()), path.toString(), detectFormat(path.toString()), false);
  }

  public static boolean looksLikeKey(Path path) {
    var name = path.getFileName() == null ? "" : path.getFileName().toString();
    return Files.isRegularFile(path)
        && (name.endsWith(".ppk")
            || name.equals("id_rsa")
            || name.equals("id_dsa")
            || name.equals("id_ecdsa")
            || name.equals("id_ed25519"));
  }

  static String stableId(String value) {
    return value == null || value.isBlank()
        ? "key"
        : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]+", "-");
  }

  private static String detectFormat(String path) {
    return path != null && path.toLowerCase(Locale.ROOT).endsWith(".ppk") ? "putty" : "openssh";
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}

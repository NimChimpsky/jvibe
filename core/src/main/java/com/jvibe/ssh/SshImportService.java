package com.jvibe.ssh;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Singleton
public final class SshImportService {
  private final Path home;

  public SshImportService() {
    this(Path.of(System.getProperty("user.home")));
  }

  SshImportService(Path home) {
    this.home = home;
  }

  public SshConnectionConfig importAll() {
    var connections = new LinkedHashMap<String, SshConnection>();
    var keys = new LinkedHashMap<String, SshKeyRef>();
    importOpenSshConfig().forEach(connection -> connections.putIfAbsent(connection.id(), connection));
    importPuttySessions().forEach(connection -> connections.putIfAbsent(connection.id(), connection));
    discoverKeys(connections.values().stream().map(SshConnection::keyPath).toList())
        .forEach(key -> keys.putIfAbsent(key.id(), key));
    return new SshConnectionConfig(List.copyOf(connections.values()), List.copyOf(keys.values()));
  }

  public List<SshConnection> importOpenSshConfig() {
    var config = home.resolve(".ssh").resolve("config");
    if (Files.notExists(config)) {
      return List.of();
    }
    try {
      return parseOpenSshConfig(Files.readAllLines(config));
    } catch (IOException e) {
      return List.of();
    }
  }

  public List<SshConnection> importPuttySessions() {
    if (isWindows()) {
      return importWindowsPuttySessions();
    }
    var sessions = home.resolve(".putty").resolve("sessions");
    if (Files.notExists(sessions)) {
      return List.of();
    }
    try (var stream = Files.list(sessions)) {
      return stream.filter(Files::isRegularFile)
          .map(this::parsePuttySessionFile)
          .flatMap(java.util.Optional::stream)
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  static List<SshConnection> parseOpenSshConfig(List<String> lines) {
    var result = new ArrayList<SshConnection>();
    var aliases = new ArrayList<String>();
    var props = new LinkedHashMap<String, String>();
    for (var raw : lines) {
      var line = stripComment(raw).trim();
      if (line.isBlank()) {
        continue;
      }
      var parts = line.split("\\s+", 2);
      if (parts.length < 2) {
        continue;
      }
      var key = parts[0].toLowerCase(Locale.ROOT);
      var value = unquote(parts[1].trim());
      if ("host".equals(key)) {
        flushOpenSshHost(result, aliases, props);
        aliases.clear();
        props.clear();
        for (var alias : value.split("\\s+")) {
          if (!alias.contains("*") && !alias.contains("?")) {
            aliases.add(alias);
          }
        }
      } else if (!aliases.isEmpty()) {
        props.put(key, value);
      }
    }
    flushOpenSshHost(result, aliases, props);
    return result;
  }

  static java.util.Optional<SshConnection> parsePuttySession(String name, List<String> lines) {
    var props = new LinkedHashMap<String, String>();
    for (var raw : lines) {
      var line = raw.trim();
      var index = line.indexOf('=');
      if (index > 0) {
        props.put(line.substring(0, index), unquote(line.substring(index + 1)));
      }
    }
    var host = props.getOrDefault("HostName", "");
    if (host.isBlank()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(new SshConnection(
        "",
        name,
        host,
        props.getOrDefault("UserName", ""),
        intValue(props.get("PortNumber"), 22),
        props.getOrDefault("PublicKeyFile", ""),
        SshAccessMode.READ_ONLY,
        ""));
  }

  private java.util.Optional<SshConnection> parsePuttySessionFile(Path file) {
    try {
      return parsePuttySession(file.getFileName().toString(), Files.readAllLines(file));
    } catch (IOException e) {
      return java.util.Optional.empty();
    }
  }

  private List<SshConnection> importWindowsPuttySessions() {
    try {
      var process = new ProcessBuilder(
          "reg",
          "query",
          "HKCU\\Software\\SimonTatham\\PuTTY\\Sessions",
          "/s").start();
      if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
        return List.of();
      }
      return parseWindowsRegistry(new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception e) {
      return List.of();
    }
  }

  static List<SshConnection> parseWindowsRegistry(String text) {
    var result = new ArrayList<SshConnection>();
    var name = "";
    var values = new ArrayList<String>();
    for (var line : text.lines().toList()) {
      if (line.startsWith("HKEY_CURRENT_USER\\Software\\SimonTatham\\PuTTY\\Sessions\\")) {
        if (!values.isEmpty()) {
          parsePuttySession(name, values).ifPresent(result::add);
        }
        name = line.substring(line.lastIndexOf('\\') + 1);
        values.clear();
      } else {
        var trimmed = line.trim().replaceAll("\\s+REG_\\w+\\s+", "=");
        if (trimmed.contains("=")) {
          values.add(trimmed);
        }
      }
    }
    if (!values.isEmpty()) {
      parsePuttySession(name, values).ifPresent(result::add);
    }
    return result;
  }

  private List<SshKeyRef> discoverKeys(List<String> importedKeyPaths) {
    var paths = new LinkedHashMap<String, Path>();
    for (var key : importedKeyPaths) {
      if (key != null && !key.isBlank()) {
        paths.put(key, Path.of(key));
      }
    }
    var ssh = home.resolve(".ssh");
    if (Files.isDirectory(ssh)) {
      try (var stream = Files.list(ssh)) {
        stream.filter(SshKeyRef::looksLikeKey).forEach(path -> paths.put(path.toString(), path));
      } catch (IOException ignored) {
      }
    }
    return paths.values().stream().map(SshKeyRef::of).toList();
  }

  private static void flushOpenSshHost(List<SshConnection> result, List<String> aliases, LinkedHashMap<String, String> props) {
    for (var alias : aliases) {
      var host = props.getOrDefault("hostname", alias);
      result.add(new SshConnection(
          "",
          alias,
          host,
          props.getOrDefault("user", ""),
          intValue(props.get("port"), 22),
          props.getOrDefault("identityfile", ""),
          SshAccessMode.READ_ONLY,
          ""));
    }
  }

  private static String stripComment(String value) {
    var index = value.indexOf('#');
    return index >= 0 ? value.substring(0, index) : value;
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static int intValue(String value, int fallback) {
    try {
      return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}

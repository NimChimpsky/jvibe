package com.jvibe.gui.terminal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

final class ShellLauncher {
  private ShellLauncher() {}

  static List<String> currentShellCommand() {
    return resolve(System.getenv(), platform(System.getProperty("os.name")), ShellLauncher::available);
  }

  static List<String> resolve(Map<String, String> environment, Platform platform, Predicate<String> available) {
    return switch (platform) {
      case WINDOWS -> available.test("pwsh.exe")
          ? List.of("pwsh.exe", "-NoLogo")
          : List.of("powershell.exe", "-NoLogo");
      case UNIX -> unixCommand(environment, available);
    };
  }

  static Platform platform(String osName) {
    var value = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
    return value.contains("win") ? Platform.WINDOWS : Platform.UNIX;
  }

  private static List<String> unixCommand(Map<String, String> environment, Predicate<String> available) {
    var configured = environment.get("SHELL");
    if (configured != null && !configured.isBlank() && available.test(configured)) {
      return List.of(configured, "-l");
    }
    if (available.test("/bin/bash")) {
      return List.of("/bin/bash", "-l");
    }
    return List.of("/bin/sh");
  }

  private static boolean available(String executable) {
    var path = Path.of(executable);
    if (path.isAbsolute()) {
      return Files.isExecutable(path);
    }
    var pathVariable = System.getenv("PATH");
    if (pathVariable == null || pathVariable.isBlank()) {
      return false;
    }
    for (var directory : pathVariable.split(java.io.File.pathSeparator)) {
      if (Files.isExecutable(Path.of(directory, executable))) {
        return true;
      }
    }
    return false;
  }

  enum Platform {
    UNIX,
    WINDOWS
  }
}

package com.jvibe.ssh;

import jakarta.inject.Singleton;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Singleton
public final class SshReadOnlyPolicy {
  private static final Set<String> ALLOWED = Set.of(
      "pwd", "ls", "find", "du", "df", "cat", "grep", "egrep", "fgrep", "awk", "sed",
      "head", "tail", "stat", "ps", "uname", "whoami", "sort", "wc", "file", "date");
  private static final Pattern UNSAFE_SHELL = Pattern.compile(".*(;|&&|\\|\\||>|<|`|\\$\\(|\\$\\{|\\n|\\r|\\s&\\s*|&\\s*$).*");
  private static final Set<String> MUTATING = Set.of(
      "rm", "mv", "cp", "chmod", "chown", "mkdir", "rmdir", "touch", "tee", "dd", "truncate",
      "apt", "apt-get", "yum", "dnf", "pacman", "apk", "brew", "service", "systemctl", "restart", "reboot",
      "shutdown", "kill", "pkill", "curl", "wget", "scp", "sftp", "rsync");
  private static final Pattern MUTATING_ARGUMENT = Pattern.compile(
      "(?i).*(^|\\s)(-delete|-exec|-execdir|-ok|-okdir|--in-place)(\\s|$).*|.*\\bsystem\\s*\\(.*");

  public boolean isAllowed(String command) {
    if (command == null
        || command.isBlank()
        || UNSAFE_SHELL.matcher(command).matches()
        || MUTATING_ARGUMENT.matcher(command).matches()) {
      return false;
    }
    for (var stage : command.split("\\|")) {
      var token = firstToken(stage);
      if (token.isBlank() || MUTATING.contains(token) || !ALLOWED.contains(token)) {
        return false;
      }
      if ("sed".equals(token) && stage.matches("(?is).*\\s-i(?:\\s|$).*$")) {
        return false;
      }
    }
    return true;
  }

  private static String firstToken(String stage) {
    var trimmed = stage.trim();
    if (trimmed.isBlank()) {
      return "";
    }
    return trimmed.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
  }
}

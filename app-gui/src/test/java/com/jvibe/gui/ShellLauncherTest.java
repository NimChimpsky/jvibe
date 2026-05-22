package com.jvibe.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShellLauncherTest {
  @Test
  void unixUsesConfiguredShellWhenAvailable() {
    var command = ShellLauncher.resolve(
        Map.of("SHELL", "/opt/homebrew/bin/fish"),
        ShellLauncher.Platform.UNIX,
        executable -> executable.equals("/opt/homebrew/bin/fish"));

    assertEquals(List.of("/opt/homebrew/bin/fish", "-l"), command);
  }

  @Test
  void unixFallsBackToBashThenSh() {
    assertEquals(
        List.of("/bin/bash", "-l"),
        ShellLauncher.resolve(Map.of(), ShellLauncher.Platform.UNIX, executable -> executable.equals("/bin/bash")));
    assertEquals(
        List.of("/bin/sh"),
        ShellLauncher.resolve(Map.of(), ShellLauncher.Platform.UNIX, executable -> false));
  }

  @Test
  void windowsPrefersPwshThenPowershell() {
    assertEquals(
        List.of("pwsh.exe", "-NoLogo"),
        ShellLauncher.resolve(Map.of(), ShellLauncher.Platform.WINDOWS, executable -> executable.equals("pwsh.exe")));
    assertEquals(
        List.of("powershell.exe", "-NoLogo"),
        ShellLauncher.resolve(Map.of(), ShellLauncher.Platform.WINDOWS, executable -> false));
  }

  @Test
  void platformDetectionTreatsWindowsSeparately() {
    assertEquals(ShellLauncher.Platform.WINDOWS, ShellLauncher.platform("Windows 11"));
    assertEquals(ShellLauncher.Platform.UNIX, ShellLauncher.platform("Mac OS X"));
    assertEquals(ShellLauncher.Platform.UNIX, ShellLauncher.platform("Linux"));
  }
}

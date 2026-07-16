package com.jvibe.gui.deploy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

final class CliArtifactLocator {
  private CliArtifactLocator() {}

  static Path locate() {
    var candidates = new ArrayList<Path>();
    var configured = System.getenv("JVIBE_CLI_JAR");
    if (configured != null && !configured.isBlank()) {
      candidates.add(Path.of(configured));
    }
    candidates.add(Path.of("app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar"));
    candidates.add(Path.of("app-gui/build/install/jvibe/agent/jvibe-cli.jar"));
    try {
      var running = Path.of(CliArtifactLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      var lib = Files.isDirectory(running) ? running : running.getParent();
      if (lib != null && lib.getParent() != null) {
        candidates.add(lib.getParent().resolve("agent/jvibe-cli.jar"));
      }
    } catch (Exception ignored) {
      // Development candidates above remain available.
    }
    return candidates.stream()
        .map(path -> path.toAbsolutePath().normalize())
        .filter(Files::isRegularFile)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "CLI deployment jar not found. Run ./gradlew :app-gui:installDist or set JVIBE_CLI_JAR."));
  }
}

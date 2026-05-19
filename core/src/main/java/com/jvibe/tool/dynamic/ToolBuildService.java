package com.jvibe.tool.dynamic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import jakarta.inject.Singleton;
import javax.tools.ToolProvider;

@Singleton
public final class ToolBuildService {
  public ToolBuildResult build(Path toolDir, ToolManifest manifest) throws IOException {
    if (!manifest.dependencies().isEmpty() || Files.exists(toolDir.resolve("build.gradle"))) {
      return buildWithGradle(toolDir);
    }
    return buildWithJavac(toolDir, manifest);
  }

  private ToolBuildResult buildWithJavac(Path toolDir, ToolManifest manifest) throws IOException {
    var sourceDir = toolDir.resolve("src");
    if (Files.notExists(sourceDir)) {
      return new ToolBuildResult(false, null, "Missing src directory: " + sourceDir);
    }
    var classesDir = toolDir.resolve("build").resolve("classes");
    var distDir = toolDir.resolve("dist");
    deleteDirectory(classesDir);
    Files.createDirectories(classesDir);
    Files.createDirectories(distDir);

    var sources = new ArrayList<Path>();
    try (var stream = Files.walk(sourceDir)) {
      stream.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
    }
    if (sources.isEmpty()) {
      return new ToolBuildResult(false, null, "No Java source files found in " + sourceDir);
    }

    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      return new ToolBuildResult(false, null, "No system Java compiler available. Use a JDK, not a JRE.");
    }
    var args = new ArrayList<String>();
    args.add("-d");
    args.add(classesDir.toString());
    args.add("-classpath");
    args.add(System.getProperty("java.class.path"));
    for (var source : sources) {
      args.add(source.toString());
    }
    var output = new java.io.ByteArrayOutputStream();
    var exit = compiler.run(null, output, output, args.toArray(String[]::new));
    if (exit != 0) {
      return new ToolBuildResult(false, null, output.toString());
    }

    var jar = distDir.resolve(manifest.id() + ".jar");
    createJar(classesDir, jar);
    return new ToolBuildResult(true, jar, output.toString());
  }

  private ToolBuildResult buildWithGradle(Path toolDir) throws IOException {
    try {
      var process = new ProcessBuilder("gradle", "clean", "jar")
          .directory(toolDir.toFile())
          .redirectErrorStream(true)
          .start();
      var finished = process.waitFor(180, java.util.concurrent.TimeUnit.SECONDS);
      var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      if (!finished) {
        process.destroyForcibly();
        return new ToolBuildResult(false, null, "Gradle build timed out\n" + output);
      }
      if (process.exitValue() != 0) {
        return new ToolBuildResult(false, null, output);
      }
      try (var stream = Files.walk(toolDir.resolve("build").resolve("libs"))) {
        var jar = stream.filter(path -> path.toString().endsWith(".jar")).findFirst().orElse(null);
        return jar == null
            ? new ToolBuildResult(false, null, "Gradle build produced no jar")
            : new ToolBuildResult(true, jar, output);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new ToolBuildResult(false, null, "Gradle build interrupted");
    }
  }

  private static void createJar(Path classesDir, Path jar) throws IOException {
    try (var output = new JarOutputStream(Files.newOutputStream(jar));
         var stream = Files.walk(classesDir)) {
      for (var path : stream.filter(Files::isRegularFile).toList()) {
        var name = classesDir.relativize(path).toString().replace('\\', '/');
        output.putNextEntry(new JarEntry(name));
        Files.copy(path, output);
        output.closeEntry();
      }
    }
  }

  private static void deleteDirectory(Path directory) throws IOException {
    if (Files.notExists(directory)) {
      return;
    }
    try (var stream = Files.walk(directory)) {
      for (var path : stream.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }
}

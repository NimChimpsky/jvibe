package com.jvibe.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeploymentServiceTest {
  @TempDir Path temp;

  @Test
  void createsSshDeploymentPlan() {
    var service = new DeploymentService(new ConfigService(JvibePaths.under(temp)));
    var profile = new DeploymentProfile(
        "prod",
        new DeploymentTarget("ssh", "server", "deploy", "/opt/jvibe", "/srv/app"),
        "openai",
        "gpt",
        "autonomous",
        true,
        "127.0.0.1",
        8737);

    var result = service.plan(profile, Path.of("jvibe-cli.jar"));

    assertTrue(result.success());
    assertTrue(result.commands().get(0).contains("ssh deploy@server"));
    assertTrue(result.commands().get(1).contains("scp jvibe-cli.jar deploy@server:/opt/jvibe/jvibe-cli.jar"));
    assertTrue(result.serviceConfig().contains("daemon --workspace \"/srv/app\" --profile \"prod\""));
  }

  @Test
  void deployLocalCopiesJarAndWritesScript() throws Exception {
    var jar = temp.resolve("jvibe-cli.jar");
    Files.writeString(jar, "jar");
    var install = temp.resolve("install");
    var service = new DeploymentService(new ConfigService(JvibePaths.under(temp)));
    var profile = new DeploymentProfile(
        "local",
        new DeploymentTarget("local", "", "", install.toString(), temp.toString()),
        "local",
        "echo",
        "autonomous",
        true,
        "127.0.0.1",
        8737);

    var result = service.deployLocal(profile, jar);

    assertTrue(result.success());
    assertEquals("jar", Files.readString(install.resolve("jvibe-cli.jar")));
    assertTrue(Files.readString(install.resolve("jvibe-agent.sh")).contains("java -jar"));
  }
}

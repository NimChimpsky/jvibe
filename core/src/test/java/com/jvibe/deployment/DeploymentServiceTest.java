package com.jvibe.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import com.jvibe.ssh.SshDeploymentService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeploymentServiceTest {
  @TempDir Path temp;

  @Test
  void createsSshDeploymentPlan() {
    var service = service();
    var profile = new DeploymentProfile(
        "prod",
        new DeploymentTarget("ssh", "server", "deploy", "/opt/jvibe", "/srv/app"),
        "openai",
        "gpt",
        true,
        "127.0.0.1",
        8737);

    var result = service.plan(profile, Path.of("jvibe-cli.jar"));

    assertTrue(result.success());
    assertTrue(result.commands().get(0).contains("deploy@server:22"));
    assertTrue(result.commands().get(1).contains("upload jvibe-cli.jar via SFTP"));
    assertTrue(result.serviceConfig().contains("daemon --workspace '/srv/app' --profile 'prod'"));
  }

  @Test
  void deployLocalCopiesJarAndWritesScript() throws Exception {
    var jar = temp.resolve("jvibe-cli.jar");
    Files.writeString(jar, "jar");
    var install = temp.resolve("install");
    var service = service();
    var profile = new DeploymentProfile(
        "local",
        new DeploymentTarget("local", "", "", install.toString(), temp.toString()),
        "jvibe",
        "jvibe-coder",
        true,
        "127.0.0.1",
        8737);

    var result = service.deployLocal(profile, jar);

    assertTrue(result.success());
    assertEquals("jar", Files.readString(install.resolve("jvibe-cli.jar")));
    assertTrue(Files.readString(install.resolve("jvibe-agent.sh")).contains("java -jar"));
    assertTrue(Files.isRegularFile(install.resolve("jvibe-agent.pid")));
  }

  @Test
  void sshDeployDelegatesArtifactAndCompleteDaemonScript() throws Exception {
    var jar = temp.resolve("jvibe-cli.jar");
    Files.writeString(jar, "jar");
    var transport = org.mockito.Mockito.mock(SshDeploymentService.class);
    when(transport.deploy(any(), any(), any())).thenReturn(
        DeploymentResult.success("deployed", java.util.List.of(), "script"));
    var service = new DeploymentService(new ConfigService(JvibePaths.under(temp)), transport);
    var profile = new DeploymentProfile(
        "prod",
        new DeploymentTarget("ssh", "server", "deploy", "/opt/jvibe", "/srv/app", 2222, "/keys/prod"),
        "openai",
        "gpt",
        true,
        "127.0.0.1",
        8737);

    var result = service.deploySsh(profile, jar);

    assertTrue(result.success());
    verify(transport).deploy(
        org.mockito.ArgumentMatchers.eq(profile),
        org.mockito.ArgumentMatchers.eq(jar),
        contains("--gateway-enabled true"));
  }

  private DeploymentService service() {
    return new DeploymentService(
        new ConfigService(JvibePaths.under(temp)),
        org.mockito.Mockito.mock(SshDeploymentService.class));
  }
}

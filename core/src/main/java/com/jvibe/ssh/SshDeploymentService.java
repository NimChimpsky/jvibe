package com.jvibe.ssh;

import com.jvibe.config.ConfigService;
import com.jvibe.deployment.DeploymentProfile;
import com.jvibe.deployment.DeploymentResult;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.apache.sshd.sftp.client.SftpClientFactory;

@Singleton
public final class SshDeploymentService {
  private static final Duration DEPLOY_TIMEOUT = Duration.ofMinutes(2);
  private final ConfigService configService;
  private final SshClientService sshClient;

  public SshDeploymentService(ConfigService configService, SshClientService sshClient) {
    this.configService = configService;
    this.sshClient = sshClient;
  }

  public DeploymentResult deploy(DeploymentProfile profile, Path cliJar, String script) {
    var target = profile.target();
    if (target.host() == null || target.host().isBlank()) {
      return DeploymentResult.failure("SSH deploy requires a host", List.of());
    }
    if (!Files.isRegularFile(cliJar)) {
      return DeploymentResult.failure("CLI jar not found: " + cliJar, List.of());
    }
    var connection = new SshConnection(
        "deployment-" + target.host(),
        target.host(),
        target.host(),
        target.user(),
        target.port(),
        target.keyPath(),
        SshAccessMode.FULL_ACCESS,
        "");
    try {
      configService.bootstrap();
      return sshClient.withSession(connection, DEPLOY_TIMEOUT, session -> {
        try (var sftp = SftpClientFactory.instance().createSftpClient(session)) {
          var home = sftp.canonicalPath(".");
          var install = remotePath(home, target.installPath());
          var configDir = home + "/.jvibe";
          mkdirs(sftp, install);
          mkdirs(sftp, configDir);

          var remoteJar = install + "/" + cliJar.getFileName();
          var remoteScript = install + "/jvibe-agent.sh";
          sftp.put(cliJar, remoteJar);
          sftp.put(new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)), remoteScript);
          sftp.put(configService.paths().config(), configDir + "/config.json");
          sftp.put(configService.paths().providers(), configDir + "/providers.json");

          var start = SshClientService.execute(session, String.join(" ",
              "chmod 700", quote(remoteScript), "&&",
              "chmod 600", quote(configDir + "/config.json"), quote(configDir + "/providers.json"), "&&",
              "nohup", quote(remoteScript), ">", quote(install + "/jvibe-agent.log"), "2>&1 < /dev/null & echo $!"),
              DEPLOY_TIMEOUT);
          if (start.exitCode() != 0) {
            return DeploymentResult.failure(
                "SSH deploy failed while starting agent: " + start.stderr().strip(),
                List.of("Uploaded CLI jar and configuration to " + target.host()));
          }
          var pid = start.stdout().strip();
          return DeploymentResult.success(
              "Deployed SSH agent to " + target.host() + (pid.isBlank() ? "" : " (PID " + pid + ")"),
              List.of(
                  "Uploaded " + cliJar.getFileName() + " to " + remoteJar,
                  "Uploaded JVibe configuration with owner-only permissions",
                  "Started " + remoteScript),
              script);
        }
      });
    } catch (Exception e) {
      return DeploymentResult.failure("SSH deploy failed: " + message(e), List.of());
    }
  }

  private static void mkdirs(org.apache.sshd.sftp.client.SftpClient sftp, String path) throws java.io.IOException {
    var current = path.startsWith("/") ? "" : sftp.canonicalPath(".");
    for (var part : path.split("/")) {
      if (part.isBlank()) {
        continue;
      }
      current = current.isEmpty() ? "/" + part : current + "/" + part;
      try {
        sftp.stat(current);
      } catch (java.io.IOException missing) {
        sftp.mkdir(current);
      }
    }
  }

  private static String remotePath(String home, String value) {
    if (value.startsWith("~/")) {
      return home + "/" + value.substring(2);
    }
    if (value.startsWith("/")) {
      return value;
    }
    return home + "/" + value;
  }

  private static String quote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private static String message(Exception exception) {
    var cause = exception;
    while (cause.getCause() instanceof Exception nested) {
      cause = nested;
    }
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }
}

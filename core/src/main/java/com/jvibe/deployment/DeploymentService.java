package com.jvibe.deployment;

import com.jvibe.config.ConfigService;
import com.jvibe.config.AtomicFiles;
import com.jvibe.ssh.SshDeploymentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
public final class DeploymentService {
  private final ConfigService configService;
  private final SshDeploymentService sshDeploymentService;

  @Inject
  public DeploymentService(ConfigService configService, SshDeploymentService sshDeploymentService) {
    this.configService = configService;
    this.sshDeploymentService = sshDeploymentService;
  }

  public DeploymentResult plan(DeploymentProfile profile, Path cliJar) {
    var target = profile.target();
    var jarName = cliJar.getFileName().toString();
    var installPath = target.installPath();
    var remoteJar = installPath + "/" + jarName;
    var service = new AgentServiceConfig(
        remoteJar,
        target.workspace(),
        profile.name(),
        profile.gatewayEnabled(),
        profile.gatewayHost(),
        profile.gatewayPort());
    var commands = new ArrayList<String>();
    if ("ssh".equalsIgnoreCase(target.type())) {
      var remote = target.user() == null || target.user().isBlank() ? target.host() : target.user() + "@" + target.host();
      commands.add("Connect to " + remote + ":" + target.port() + " with verified known_hosts");
      commands.add("Create " + installPath + " and upload " + cliJar + " via SFTP");
      commands.add("Upload provider/config files with owner-only permissions");
      commands.add("Start " + installPath + "/jvibe-agent.sh in the background");
    } else {
      commands.add("mkdir -p " + installPath);
      commands.add("cp " + cliJar + " " + remoteJar);
      commands.add("write " + installPath + "/jvibe-agent.sh");
      commands.add("start " + installPath + "/jvibe-agent.sh in the background");
    }
    return DeploymentResult.success("Prepared " + target.type() + " deployment", commands, serviceScript(service));
  }

  public DeploymentResult deployLocal(DeploymentProfile profile, Path cliJar) {
    var plan = plan(profile, cliJar);
    try {
      if (!Files.isRegularFile(cliJar)) {
        return DeploymentResult.failure("CLI jar not found: " + cliJar, plan.commands());
      }
      var target = profile.target();
      var installDir = expandHome(target.installPath());
      Files.createDirectories(installDir);
      var jarPath = installDir.resolve(cliJar.getFileName());
      Files.copy(cliJar, jarPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      var service = new AgentServiceConfig(
          jarPath.toString(),
          target.workspace(),
          profile.name(),
          profile.gatewayEnabled(),
          profile.gatewayHost(),
          profile.gatewayPort());
      var windows = isWindows();
      var script = installDir.resolve(windows ? "jvibe-agent.cmd" : "jvibe-agent.sh");
      AtomicFiles.writeString(script, windows ? windowsScript(service) : serviceScript(service));
      if (!windows) {
        script.toFile().setExecutable(true, true);
      }
      var process = new ProcessBuilder(daemonCommand(service))
          .directory(installDir.toFile())
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.appendTo(installDir.resolve("jvibe-agent.log").toFile()))
          .start();
      AtomicFiles.writeString(installDir.resolve("jvibe-agent.pid"), Long.toString(process.pid()) + "\n");
      return DeploymentResult.success(
          "Deployed local agent at " + installDir + " (PID " + process.pid() + ")",
          plan.commands(),
          Files.readString(script));
    } catch (IOException e) {
      return DeploymentResult.failure("Local deploy failed: " + e.getMessage(), plan.commands());
    }
  }

  public DeploymentResult deploySsh(DeploymentProfile profile, Path cliJar) {
    var target = profile.target();
    var remoteJar = remotePath(target.installPath(), cliJar.getFileName().toString());
    var script = serviceScript(new AgentServiceConfig(
        remoteJar,
        target.workspace(),
        profile.name(),
        profile.gatewayEnabled(),
        profile.gatewayHost(),
        profile.gatewayPort()));
    return sshDeploymentService.deploy(profile, cliJar, script);
  }

  public String serviceScript(AgentServiceConfig config) {
    return """
        #!/usr/bin/env sh
        set -eu
        exec java -jar %s daemon --workspace %s --profile %s --gateway-enabled %s --gateway-host %s --gateway-port %d
        """.formatted(
            quote(config.jarPath()),
            quote(config.workspace()),
            quote(config.profile()),
            config.gatewayEnabled(),
            quote(config.gatewayHost()),
            config.gatewayPort());
  }

  public List<DeploymentProfile> profiles() throws IOException {
    return configService.config().deploymentProfiles();
  }

  private Path expandHome(String path) {
    if (path.startsWith("~/")) {
      return Path.of(System.getProperty("user.home")).resolve(path.substring(2)).normalize();
    }
    return Path.of(path).toAbsolutePath().normalize();
  }

  private static String remotePath(String directory, String file) {
    return (directory.endsWith("/") ? directory : directory + "/") + file;
  }

  private static String quote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private static List<String> daemonCommand(AgentServiceConfig config) {
    var javaBinary = Path.of(
        System.getProperty("java.home"),
        "bin",
        isWindows() ? "java.exe" : "java").toString();
    return List.of(
        javaBinary,
        "-jar", config.jarPath(),
        "daemon",
        "--workspace", config.workspace(),
        "--profile", config.profile(),
        "--gateway-enabled", Boolean.toString(config.gatewayEnabled()),
        "--gateway-host", config.gatewayHost(),
        "--gateway-port", Integer.toString(config.gatewayPort()));
  }

  private static String windowsScript(AgentServiceConfig config) {
    return "@echo off\r\n"
        + "java -jar \"" + escapeWindows(config.jarPath()) + "\" daemon"
        + " --workspace \"" + escapeWindows(config.workspace()) + "\""
        + " --profile \"" + escapeWindows(config.profile()) + "\""
        + " --gateway-enabled " + config.gatewayEnabled()
        + " --gateway-host \"" + escapeWindows(config.gatewayHost()) + "\""
        + " --gateway-port " + config.gatewayPort() + "\r\n";
  }

  private static String escapeWindows(String value) {
    return value.replace("\"", "\"\"");
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
  }
}

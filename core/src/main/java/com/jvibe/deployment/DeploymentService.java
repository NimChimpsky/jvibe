package com.jvibe.deployment;

import com.jvibe.config.ConfigService;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
public final class DeploymentService {
  private final ConfigService configService;

  public DeploymentService(ConfigService configService) {
    this.configService = configService;
  }

  public DeploymentResult plan(DeploymentProfile profile, Path cliJar) {
    var target = profile.target();
    var jarName = cliJar.getFileName().toString();
    var installPath = target.installPath();
    var remoteJar = installPath + "/" + jarName;
    var service = new AgentServiceConfig(remoteJar, target.workspace(), profile.name(), profile.gatewayHost(), profile.gatewayPort());
    var commands = new ArrayList<String>();
    if ("ssh".equalsIgnoreCase(target.type())) {
      var remote = target.user() == null || target.user().isBlank() ? target.host() : target.user() + "@" + target.host();
      commands.add("ssh " + remote + " 'mkdir -p " + installPath + "'");
      commands.add("scp " + cliJar + " " + remote + ":" + remoteJar);
      commands.add("ssh " + remote + " 'cat > " + installPath + "/jvibe-agent.sh && chmod +x " + installPath + "/jvibe-agent.sh'");
    } else {
      commands.add("mkdir -p " + installPath);
      commands.add("cp " + cliJar + " " + remoteJar);
      commands.add("write " + installPath + "/jvibe-agent.sh");
    }
    return DeploymentResult.success("Prepared " + target.type() + " deployment", commands, serviceScript(service));
  }

  public DeploymentResult deployLocal(DeploymentProfile profile, Path cliJar) {
    var plan = plan(profile, cliJar);
    try {
      var target = profile.target();
      var installDir = expandHome(target.installPath());
      Files.createDirectories(installDir);
      var jarPath = installDir.resolve(cliJar.getFileName());
      Files.copy(cliJar, jarPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      var script = installDir.resolve("jvibe-agent.sh");
      Files.writeString(script, serviceScript(new AgentServiceConfig(
          jarPath.toString(), target.workspace(), profile.name(), profile.gatewayHost(), profile.gatewayPort())));
      script.toFile().setExecutable(true, true);
      return DeploymentResult.success("Installed local agent at " + installDir, plan.commands(), Files.readString(script));
    } catch (IOException e) {
      return DeploymentResult.failure("Local deploy failed: " + e.getMessage(), plan.commands());
    }
  }

  public String serviceScript(AgentServiceConfig config) {
    return """
        #!/usr/bin/env sh
        set -eu
        exec java -jar "%s" daemon --workspace "%s" --profile "%s"
        """.formatted(config.jarPath(), config.workspace(), config.profile());
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
}

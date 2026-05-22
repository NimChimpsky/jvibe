package com.jvibe.cli;

import com.jvibe.agent.AgentRunner;
import com.jvibe.config.ConfigService;
import com.jvibe.deployment.DeploymentProfile;
import com.jvibe.deployment.DeploymentService;
import com.jvibe.deployment.DeploymentTarget;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import com.jvibe.tool.ToolRegistryFactory;
import com.jvibe.workspace.Workspace;
import io.avaje.inject.BeanScope;
import java.nio.file.Path;
import java.util.Scanner;

public final class JvibeCli {
  private JvibeCli() {}

  public static void main(String[] args) throws Exception {
    try (var scope = BeanScope.builder().build()) {
      var config = scope.get(ConfigService.class);
      var runner = scope.get(AgentRunner.class);
      var sessionFactory = scope.get(SessionFactory.class);
      var toolRegistryFactory = scope.get(ToolRegistryFactory.class);
      config.bootstrap();

      if (args.length > 0 && "tools".equals(args[0])) {
        toolRegistryFactory.create(new Workspace(Path.of(System.getProperty("user.dir"))))
            .definitions()
            .forEach(tool -> System.out.println(tool.id() + " - " + tool.description()));
        return;
      }
      if (args.length > 0 && "deploy".equals(args[0])) {
        deploy(args, scope.get(DeploymentService.class));
        return;
      }
      if (args.length > 0 && "gateway".equals(args[0])) {
        gateway(args, runner, sessionFactory);
        return;
      }
      if (args.length > 0 && "daemon".equals(args[0])) {
        daemon(args, runner, sessionFactory, config);
        return;
      }

      var session = sessionFactory.create(Path.of(System.getProperty("user.dir")));

      if (args.length > 0 && "run".equals(args[0])) {
        var prompt = args.length > 1 ? args[1] : "";
        runner.run(session, prompt).forEach(event -> System.out.println(event.message()));
        return;
      }

      repl(runner, session);
    }
  }

  private static void repl(AgentRunner runner, AgentSession session) {
    System.out.println("jvibe cli - type /exit to quit");
    try (var scanner = new Scanner(System.in)) {
      while (true) {
        System.out.print("> ");
        if (!scanner.hasNextLine()) {
          break;
        }
        var line = scanner.nextLine();
        if (line.equals("/exit")) {
          break;
        }
        if (line.equals("/tools")) {
          session.toolRegistry().definitions().forEach(tool -> System.out.println(tool.id() + " - " + tool.description()));
          continue;
        }
        runner.run(session, line).forEach(event -> System.out.println(event.message()));
      }
    }
  }

  private static void gateway(String[] args, AgentRunner runner, SessionFactory sessionFactory) throws Exception {
    var host = option(args, "--host", "127.0.0.1");
    var port = Integer.parseInt(option(args, "--port", "8737"));
    var workspace = Path.of(option(args, "--workspace", System.getProperty("user.dir")));
    new GatewayServer(runner, sessionFactory, host, port, workspace).start();
    System.out.println("Gateway listening on http://" + host + ":" + port);
    Thread.currentThread().join();
  }

  private static void daemon(
      String[] args,
      AgentRunner runner,
      SessionFactory sessionFactory,
      ConfigService config) throws Exception {
    var cfg = config.config();
    var workspace = Path.of(option(args, "--workspace", System.getProperty("user.dir")));
    if (cfg.gatewayEnabled()) {
      new GatewayServer(runner, sessionFactory, cfg.gatewayHost(), cfg.gatewayPort(), workspace).start();
      System.out.println("Daemon gateway listening on http://" + cfg.gatewayHost() + ":" + cfg.gatewayPort());
    } else {
      System.out.println("Daemon started without gateway");
    }
    Thread.currentThread().join();
  }

  private static void deploy(String[] args, DeploymentService service) {
    var type = args.length > 1 ? args[1] : "local";
    var dryRun = has(args, "--dry-run");
    var jar = Path.of(option(args, "--jar", currentJar()));
    var target = new DeploymentTarget(
        type,
        option(args, "--host", ""),
        option(args, "--user", ""),
        option(args, "--path", "~/.jvibe/agent"),
        option(args, "--workspace", System.getProperty("user.dir")));
    var profile = new DeploymentProfile(
        option(args, "--profile", "default"),
        target,
        option(args, "--provider", "local"),
        option(args, "--model", "echo"),
        "autonomous",
        true,
        option(args, "--gateway-host", "127.0.0.1"),
        Integer.parseInt(option(args, "--gateway-port", "8737")));
    var result = dryRun || "ssh".equalsIgnoreCase(type) ? service.plan(profile, jar) : service.deployLocal(profile, jar);
    System.out.println(result.summary());
    result.commands().forEach(System.out::println);
    if (!result.serviceConfig().isBlank()) {
      System.out.println(result.serviceConfig());
    }
  }

  private static String option(String[] args, String name, String fallback) {
    for (var i = 0; i < args.length - 1; i++) {
      if (name.equals(args[i])) {
        return args[i + 1];
      }
    }
    return fallback;
  }

  private static boolean has(String[] args, String name) {
    for (var arg : args) {
      if (name.equals(arg)) {
        return true;
      }
    }
    return false;
  }

  private static String currentJar() {
    try {
      return Path.of(JvibeCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    } catch (Exception e) {
      return "app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar";
    }
  }
}

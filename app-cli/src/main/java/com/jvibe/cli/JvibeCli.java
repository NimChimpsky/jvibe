package com.jvibe.cli;

import com.jvibe.agent.AgentRunner;
import com.jvibe.config.ConfigService;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import com.jvibe.tool.ToolRegistryFactory;
import com.jvibe.tool.dynamic.DynamicToolManagementTools;
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
        tools(args, toolRegistryFactory, scope.get(DynamicToolManagementTools.class));
        return;
      }
      var session = sessionFactory.create(Path.of(System.getProperty("user.dir")));

      if (args.length > 0 && "run".equals(args[0])) {
        var prompt = args.length > 1 ? args[1] : "";
        printRun(runner, session, prompt);
        return;
      }

      repl(runner, session);
    }
  }

  private static void repl(AgentRunner runner, AgentSession session) {
    System.out.println("JVibe CLI - type /exit to quit");
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
        printRun(runner, session, line);
      }
    }
  }

  private static void printRun(AgentRunner runner, AgentSession session, String prompt) {
    try (var events = runner.run(session, prompt)) {
      events.forEach(event -> System.out.println(event.message()));
    }
  }

  private static void tools(
      String[] args,
      ToolRegistryFactory registryFactory,
      DynamicToolManagementTools management) {
    var workspace = new Workspace(Path.of(System.getProperty("user.dir")));
    var registry = registryFactory.create(workspace);
    var action = args.length > 1 ? args[1] : "list";
    if ("list".equals(action)) {
      management.discovered(workspace).forEach(tool -> System.out.printf(
          "%s (%s) %s%n", tool.id(), tool.scope(), tool.approved() ? tool.loaded() ? "loaded" : "approved" : "approval required"));
      return;
    }
    if (args.length < 3) {
      System.err.println("Usage: JVibe tools approve|reload|disable|delete <id> [--scope project|user]");
      return;
    }
    var id = args[2];
    var scope = option(args, "--scope", "project");
    var callId = "cli-" + action;
    var result = switch (action) {
      case "approve" -> management.approve(callId, workspace, registry, id, scope);
      case "reload" -> management.reload(callId, workspace, registry, id, scope);
      case "disable" -> management.disable(callId, workspace, registry, id, scope);
      case "delete" -> management.delete(callId, workspace, registry, id, scope);
      default -> com.jvibe.tool.ToolResult.failure(callId, "Unknown tools action: " + action);
    };
    (result.success() ? System.out : System.err).println(result.summary());
  }

  private static String option(String[] args, String name, String fallback) {
    for (var i = 0; i < args.length - 1; i++) {
      if (name.equals(args[i])) {
        return args[i + 1];
      }
    }
    return fallback;
  }

}

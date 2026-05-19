package com.jvibe.cli;

import com.jvibe.agent.AgentRunner;
import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import com.jvibe.provider.EchoProviderClient;
import com.jvibe.session.AgentSession;
import com.jvibe.tool.builtin.BuiltinTools;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.util.Scanner;

public final class JvibeCli {
  private JvibeCli() {}

  public static void main(String[] args) throws Exception {
    var config = new ConfigService(JvibePaths.userHome());
    config.bootstrap();

    if (args.length > 0 && "tools".equals(args[0])) {
      BuiltinTools.registry().definitions().forEach(tool -> System.out.println(tool.id() + " - " + tool.description()));
      return;
    }

    var workspace = new Workspace(Path.of(System.getProperty("user.dir")));
    var session = new AgentSession(workspace, BuiltinTools.registry());
    var runner = new AgentRunner(new EchoProviderClient(), config.systemPrompt(), 10);

    if (args.length > 0 && "run".equals(args[0])) {
      var prompt = args.length > 1 ? args[1] : "";
      runner.run(session, prompt).forEach(event -> System.out.println(event.message()));
      return;
    }

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
          BuiltinTools.registry().definitions().forEach(tool -> System.out.println(tool.id() + " - " + tool.description()));
          continue;
        }
        runner.run(session, line).forEach(event -> System.out.println(event.message()));
      }
    }
  }
}

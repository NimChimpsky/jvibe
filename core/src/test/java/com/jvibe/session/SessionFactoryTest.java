package com.jvibe.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.ssh.SshConnection;
import io.avaje.inject.BeanScope;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionFactoryTest {
  @TempDir Path repository;

  @Test
  void eachTabGetsItsModeToolsAndContext() {
    try (var scope = BeanScope.builder().build()) {
      var sessions = scope.get(SessionFactory.class);

      var chat = sessions.chat(() -> List.of(Path.of("/tmp/notes.md")));
      assertEquals(Mode.CHAT, chat.mode());
      assertTrue(chat.toolRegistry().find("file_read").isPresent());
      assertTrue(chat.toolRegistry().find("file_write").isEmpty());
      assertTrue(chat.context().contains("/tmp/notes.md"));

      var build = sessions.build(repository);
      assertEquals(Mode.BUILD, build.mode());
      assertTrue(build.toolRegistry().find("git_commit").orElseThrow().changesState());
      assertTrue(build.context().contains(repository.toString()));

      var server = new SshConnection("web01", "web01", "web01.example.com", "ops", 22, "", "");
      var servers = sessions.servers(() -> List.of(server));
      assertEquals(Mode.SERVERS, servers.mode());
      assertTrue(servers.toolRegistry().find("ssh_exec").isPresent());
      assertTrue(servers.context().contains("serverId=web01"));

      var database = JdbcConnectionConfig.create("orders", "jdbc:h2:mem:orders", "sa", "", "");
      var databases = sessions.databases(() -> List.of(database));
      assertEquals(Mode.DATABASES, databases.mode());
      assertTrue(databases.toolRegistry().find("sql_schema").isPresent());
      assertTrue(databases.context().contains("jdbc:h2:mem:orders"));
    }
  }
}

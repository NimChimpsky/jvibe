package com.jvibe.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcConnectionRepositoryTest {
  @TempDir java.nio.file.Path home;

  @Test
  void persistsDefinitionsButKeepsPasswordsOutOfConfig() throws Exception {
    var paths = JvibePaths.under(home);
    var credentials = new JdbcCredentialStore();
    var repository = new JdbcConnectionRepository(new ConfigService(paths), credentials);
    var connection = JdbcConnectionConfig.create("Database", "jdbc:test", "user", "driver.Type", "/tmp/driver.jar");

    credentials.put(connection.id(), "do-not-persist".toCharArray());
    repository.save(connection);

    assertEquals(connection, repository.connections().get(0));
    assertFalse(Files.readString(paths.config()).contains("do-not-persist"));
    repository.delete(connection.id());
    assertTrue(repository.connections().isEmpty());
    assertFalse(credentials.contains(connection.id()));
  }
}

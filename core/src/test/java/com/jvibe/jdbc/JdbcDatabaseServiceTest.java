package com.jvibe.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JdbcDatabaseServiceTest {
  private final JdbcDatabaseService database = new JdbcDatabaseService();
  private final JdbcConnectionConfig connection = new JdbcConnectionConfig(
      "test", "H2 test", "jdbc:h2:mem:jvibe-jdbc;DB_CLOSE_DELAY=-1", "sa", "org.h2.Driver", "");

  @Test
  void executesCrudAndQueries() throws Exception {
    database.execute(connection, new char[0], "CREATE TABLE projects (id INTEGER PRIMARY KEY, name VARCHAR(80))");
    var update = database.execute(connection, new char[0], "INSERT INTO projects VALUES (1, 'JVibe')");
    var query = database.execute(connection, new char[0], "SELECT id, name FROM projects");

    assertEquals(1, update.updateCount());
    assertEquals(java.util.List.of("ID", "NAME"), query.columns());
    assertEquals(1, query.rows().size());
    assertEquals("JVibe", query.rows().getFirst().get(1));
  }

  @Test
  void exposesSchemaAndAdministrationMetadata() throws Exception {
    database.execute(connection, new char[0], "CREATE TABLE IF NOT EXISTS audit_log (id BIGINT, message VARCHAR(120))");

    var schema = database.schemaSummary(connection, new char[0]);
    var stats = database.adminStats(connection, new char[0]);

    assertTrue(schema.contains("AUDIT_LOG"), schema);
    assertTrue(schema.contains("MESSAGE"), schema);
    assertTrue(stats.stream().anyMatch(stat -> stat.name().equals("Database") && stat.value().contains("H2")));
    assertTrue(stats.stream().anyMatch(stat -> stat.name().equals("Connection valid") && stat.value().equals("true")));
  }
}

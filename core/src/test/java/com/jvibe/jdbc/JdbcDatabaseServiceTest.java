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
    assertEquals("JVibe", query.rows().get(0).get(1));
  }

  @Test
  void schemaSummaryFiltersByTablePattern() throws Exception {
    database.execute(connection, new char[0], "CREATE TABLE IF NOT EXISTS audit_log (id BIGINT, message VARCHAR(120))");
    database.execute(connection, new char[0], "CREATE TABLE IF NOT EXISTS orders (id BIGINT)");

    var all = database.schemaSummary(connection, new char[0], "");
    var audit = database.schemaSummary(connection, new char[0], "AUDIT%");

    assertTrue(all.contains("AUDIT_LOG") && all.contains("ORDERS"), all);
    assertTrue(audit.contains("MESSAGE"), audit);
    assertTrue(!audit.contains("ORDERS"), audit);
    assertTrue(database.schemaSummary(connection, new char[0], "NOPE%").contains("No tables or views match"));
  }

  @Test
  void toolSetRunsOnlyOnAttachedConnectionsAndBoundsModelRows() throws Exception {
    database.execute(connection, new char[0], "CREATE TABLE IF NOT EXISTS numbers (n INTEGER)");
    database.execute(connection, new char[0], "DELETE FROM numbers");
    database.execute(connection, new char[0], "INSERT INTO numbers SELECT X FROM SYSTEM_RANGE(1, 80)");
    var tools = new JdbcToolSet(database, new JdbcCredentialStore());
    var context = com.jvibe.tool.ToolExecutionContext.defaults(
        new com.jvibe.workspace.Workspace(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"))));

    var detached = tools.registry(java.util.List::of).execute(new com.jvibe.tool.ToolCall("1", "sql_execute",
        java.util.Map.of("connectionId", "test", "sql", "SELECT 1")), context);
    assertTrue(!detached.success());

    var result = tools.registry(() -> java.util.List.of(connection)).execute(new com.jvibe.tool.ToolCall("2",
        "sql_execute", java.util.Map.of("connectionId", "test", "sql", "SELECT n FROM numbers ORDER BY n")), context);
    assertTrue(result.success());
    assertEquals(JdbcToolSet.MODEL_ROWS, ((java.util.List<?>) result.payload().get("rows")).size());
    assertEquals(true, result.payload().get("truncated"));
    assertEquals(80, ((JdbcQueryResult) result.display()).rows().size());
  }
}

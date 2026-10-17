package com.jvibe.jdbc;

import jakarta.inject.Singleton;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Singleton
public final class JdbcDatabaseService {
  private static final int MAX_RESULT_ROWS = 1_000;
  private static final int MAX_SCHEMA_TABLES = 200;
  private static final int MAX_SCHEMA_COLUMNS = 2_000;
  private static final int QUERY_TIMEOUT_SECONDS = 60;
  private static final int MAX_CELL_CHARS = 10_000;

  public String test(JdbcConnectionConfig config, char[] password) throws Exception {
    var started = System.nanoTime();
    try (var handle = open(config, password)) {
      var metadata = handle.connection().getMetaData();
      var valid = handle.connection().isValid(5);
      return "%s %s, %s ms%s".formatted(
          metadata.getDatabaseProductName(),
          metadata.getDatabaseProductVersion(),
          elapsedMillis(started),
          valid ? "" : " (connection validation failed)");
    }
  }

  public JdbcQueryResult execute(JdbcConnectionConfig config, char[] password, String sql) throws Exception {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("SQL is required");
    }
    var started = System.nanoTime();
    try (var handle = open(config, password);
         var statement = handle.connection().createStatement()) {
      try {
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      } catch (SQLFeatureNotSupportedException ignored) {
        // The driver can still execute; it simply cannot enforce a client-side timeout.
      }
      statement.setMaxRows(MAX_RESULT_ROWS + 1);
      var hasResults = statement.execute(sql);
      if (!hasResults) {
        var count = statement.getUpdateCount();
        return new JdbcQueryResult(
            List.of(),
            List.of(),
            count,
            elapsedMillis(started),
            false,
            count < 0 ? "Statement executed" : count + " row(s) affected");
      }
      try (var resultSet = statement.getResultSet()) {
        return readResultSet(resultSet, started);
      }
    }
  }

  public List<JdbcAdminStat> adminStats(JdbcConnectionConfig config, char[] password) throws Exception {
    try (var handle = open(config, password)) {
      var connection = handle.connection();
      var metadata = connection.getMetaData();
      var stats = new ArrayList<JdbcAdminStat>();
      stats.add(new JdbcAdminStat("Database", metadata.getDatabaseProductName()));
      stats.add(new JdbcAdminStat("Database version", metadata.getDatabaseProductVersion()));
      stats.add(new JdbcAdminStat("Driver", metadata.getDriverName() + " " + metadata.getDriverVersion()));
      stats.add(new JdbcAdminStat("URL", metadata.getURL()));
      stats.add(new JdbcAdminStat("User", metadata.getUserName()));
      stats.add(new JdbcAdminStat("Catalog", text(connection.getCatalog())));
      stats.add(new JdbcAdminStat("Schema", text(currentSchema(connection))));
      stats.add(new JdbcAdminStat("Read only", Boolean.toString(connection.isReadOnly())));
      stats.add(new JdbcAdminStat("Auto commit", Boolean.toString(connection.getAutoCommit())));
      stats.add(new JdbcAdminStat("Transactions", Boolean.toString(metadata.supportsTransactions())));
      stats.add(new JdbcAdminStat("Isolation", isolation(connection.getTransactionIsolation())));
      var counts = objectCounts(metadata);
      stats.add(new JdbcAdminStat("Tables", Integer.toString(counts.tables())));
      stats.add(new JdbcAdminStat("Views", Integer.toString(counts.views())));
      stats.add(new JdbcAdminStat("Connection valid", Boolean.toString(connection.isValid(5))));
      return List.copyOf(stats);
    }
  }

  public String schemaSummary(JdbcConnectionConfig config, char[] password) throws Exception {
    try (var handle = open(config, password)) {
      var metadata = handle.connection().getMetaData();
      var summary = new StringBuilder();
      summary.append("Database: ")
          .append(metadata.getDatabaseProductName())
          .append(' ')
          .append(metadata.getDatabaseProductVersion())
          .append('\n');
      var tables = 0;
      var columns = 0;
      try (var result = metadata.getTables(handle.connection().getCatalog(), null, "%", new String[] {"TABLE", "VIEW"})) {
        while (result.next() && tables < MAX_SCHEMA_TABLES && columns < MAX_SCHEMA_COLUMNS) {
          tables++;
          var catalog = result.getString("TABLE_CAT");
          var schema = result.getString("TABLE_SCHEM");
          var table = result.getString("TABLE_NAME");
          summary.append(qualified(catalog, schema, table)).append(" (");
          var first = true;
          try (var columnSet = metadata.getColumns(catalog, schema, table, "%")) {
            while (columnSet.next() && columns < MAX_SCHEMA_COLUMNS) {
              if (!first) {
                summary.append(", ");
              }
              first = false;
              columns++;
              summary.append(columnSet.getString("COLUMN_NAME"))
                  .append(' ')
                  .append(columnSet.getString("TYPE_NAME"));
            }
          }
          summary.append(")\n");
        }
      }
      if (tables >= MAX_SCHEMA_TABLES || columns >= MAX_SCHEMA_COLUMNS) {
        summary.append("[schema summary truncated]\n");
      }
      return summary.toString();
    }
  }

  private static JdbcQueryResult readResultSet(ResultSet resultSet, long started) throws SQLException {
    var metadata = resultSet.getMetaData();
    var columns = new ArrayList<String>();
    for (var index = 1; index <= metadata.getColumnCount(); index++) {
      columns.add(metadata.getColumnLabel(index));
    }
    var rows = new ArrayList<List<Object>>();
    var truncated = false;
    while (resultSet.next()) {
      if (rows.size() >= MAX_RESULT_ROWS) {
        truncated = true;
        break;
      }
      var row = new ArrayList<Object>(columns.size());
      for (var index = 1; index <= columns.size(); index++) {
        row.add(displayValue(resultSet.getObject(index)));
      }
      rows.add(row);
    }
    var message = rows.size() + " row(s) returned" + (truncated ? "; showing first " + MAX_RESULT_ROWS : "");
    return new JdbcQueryResult(columns, rows, -1, elapsedMillis(started), truncated, message);
  }

  private ConnectionHandle open(JdbcConnectionConfig config, char[] password) throws Exception {
    if (config.jdbcUrl().isBlank()) {
      throw new IllegalArgumentException("JDBC URL is required");
    }
    var properties = new Properties();
    if (!config.username().isBlank()) {
      properties.setProperty("user", config.username());
    }
    properties.setProperty("password", password == null ? "" : new String(password));
    if (config.driverJar().isBlank()) {
      if (!config.driverClass().isBlank()) {
        Class.forName(config.driverClass());
      }
      return new ConnectionHandle(DriverManager.getConnection(config.jdbcUrl(), properties), null);
    }

    var driverPath = expandHome(config.driverJar());
    if (!Files.isRegularFile(driverPath)) {
      throw new IllegalArgumentException("JDBC driver jar not found: " + driverPath);
    }
    if (config.driverClass().isBlank()) {
      throw new IllegalArgumentException("Driver class is required when using an external JDBC jar");
    }
    var loader = new URLClassLoader(
        new java.net.URL[] {driverPath.toUri().toURL()},
        JdbcDatabaseService.class.getClassLoader());
    try {
      var driver = (Driver) Class.forName(config.driverClass(), true, loader).getDeclaredConstructor().newInstance();
      var connection = driver.connect(config.jdbcUrl(), properties);
      if (connection == null) {
        throw new SQLException("Driver does not accept JDBC URL: " + config.jdbcUrl());
      }
      return new ConnectionHandle(connection, loader);
    } catch (Exception e) {
      loader.close();
      throw e;
    }
  }

  private static Object displayValue(Object value) throws SQLException {
    if (value instanceof Blob blob) {
      return "[BLOB " + blob.length() + " bytes]";
    }
    if (value instanceof Clob clob) {
      return "[CLOB " + clob.length() + " chars]";
    }
    if (value instanceof byte[] bytes) {
      return "[binary " + bytes.length + " bytes]";
    }
    if (value instanceof String text && text.length() > MAX_CELL_CHARS) {
      return text.substring(0, MAX_CELL_CHARS) + " [truncated]";
    }
    return value;
  }

  private static ObjectCounts objectCounts(java.sql.DatabaseMetaData metadata) throws SQLException {
    var tables = 0;
    var views = 0;
    try (var result = metadata.getTables(null, null, "%", new String[] {"TABLE", "VIEW"})) {
      while (result.next()) {
        if ("VIEW".equalsIgnoreCase(result.getString("TABLE_TYPE"))) {
          views++;
        } else {
          tables++;
        }
      }
    }
    return new ObjectCounts(tables, views);
  }

  private static String currentSchema(Connection connection) {
    try {
      return connection.getSchema();
    } catch (SQLFeatureNotSupportedException e) {
      return "";
    } catch (SQLException e) {
      return "unavailable";
    }
  }

  private static String isolation(int value) {
    return switch (value) {
      case Connection.TRANSACTION_NONE -> "None";
      case Connection.TRANSACTION_READ_UNCOMMITTED -> "Read uncommitted";
      case Connection.TRANSACTION_READ_COMMITTED -> "Read committed";
      case Connection.TRANSACTION_REPEATABLE_READ -> "Repeatable read";
      case Connection.TRANSACTION_SERIALIZABLE -> "Serializable";
      default -> Integer.toString(value);
    };
  }

  private static String qualified(String catalog, String schema, String table) {
    var name = new StringBuilder();
    if (catalog != null && !catalog.isBlank()) {
      name.append(catalog).append('.');
    }
    if (schema != null && !schema.isBlank()) {
      name.append(schema).append('.');
    }
    return name.append(table).toString();
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private static long elapsedMillis(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
  }

  private static Path expandHome(String value) {
    return value.startsWith("~/")
        ? Path.of(System.getProperty("user.home")).resolve(value.substring(2)).normalize()
        : Path.of(value).toAbsolutePath().normalize();
  }

  private record ObjectCounts(int tables, int views) {}

  private record ConnectionHandle(Connection connection, URLClassLoader loader) implements AutoCloseable {
    @Override
    public void close() throws Exception {
      try {
        connection.close();
      } finally {
        if (loader != null) {
          loader.close();
        }
      }
    }
  }
}

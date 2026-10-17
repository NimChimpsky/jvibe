package com.jvibe.jdbc;

import java.util.List;

public record JdbcQueryResult(
    List<String> columns,
    List<List<Object>> rows,
    int updateCount,
    long elapsedMillis,
    boolean truncated,
    String message) {
  public JdbcQueryResult {
    columns = columns == null ? List.of() : List.copyOf(columns);
    rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
    message = message == null ? "" : message;
  }
}

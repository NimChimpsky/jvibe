package com.jvibe.jdbc;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Singleton
public final class JdbcCredentialStore {
  private final Map<String, char[]> passwords = new HashMap<>();

  public synchronized void put(String connectionId, char[] password) {
    clear(passwords.put(connectionId, password == null ? new char[0] : password.clone()));
  }

  public synchronized boolean contains(String connectionId) {
    return passwords.containsKey(connectionId);
  }

  public synchronized char[] password(String connectionId) {
    var password = passwords.get(connectionId);
    return password == null ? new char[0] : password.clone();
  }

  public synchronized void remove(String connectionId) {
    clear(passwords.remove(connectionId));
  }

  @PreDestroy
  public synchronized void close() {
    passwords.values().forEach(JdbcCredentialStore::clear);
    passwords.clear();
  }

  private static void clear(char[] value) {
    if (value != null) {
      Arrays.fill(value, '\0');
    }
  }
}

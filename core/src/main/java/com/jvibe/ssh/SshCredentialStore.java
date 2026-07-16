package com.jvibe.ssh;

import jakarta.inject.Singleton;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** In-memory SSH secrets. Passphrases are never written to JVibe configuration. */
@Singleton
public final class SshCredentialStore implements AutoCloseable {
  private final Map<String, char[]> passphrases = new HashMap<>();

  public synchronized void putPassphrase(String connectionId, char[] passphrase) {
    clear(connectionId);
    passphrases.put(connectionId, Arrays.copyOf(passphrase, passphrase.length));
  }

  public synchronized String passphrase(String connectionId) {
    var value = passphrases.get(connectionId);
    return value == null ? null : new String(value);
  }

  public synchronized boolean isUnlocked(String connectionId) {
    return passphrases.containsKey(connectionId);
  }

  public synchronized void clear(String connectionId) {
    var previous = passphrases.remove(connectionId);
    if (previous != null) {
      Arrays.fill(previous, '\0');
    }
  }

  @Override
  @PreDestroy
  public synchronized void close() {
    passphrases.values().forEach(value -> Arrays.fill(value, '\0'));
    passphrases.clear();
  }
}

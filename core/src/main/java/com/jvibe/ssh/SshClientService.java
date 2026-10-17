package com.jvibe.ssh;

import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;

@Singleton
public final class SshClientService {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_CAPTURE_BYTES = 4 * 1024 * 1024;
  private final SshCredentialStore credentials;

  public SshClientService(SshCredentialStore credentials) {
    this.credentials = credentials;
  }

  public SshCommandResult execute(SshConnection connection, String command) throws Exception {
    return execute(connection, command, DEFAULT_TIMEOUT);
  }

  public SshCommandResult execute(SshConnection connection, String command, Duration timeout) throws Exception {
    return execute(connection, command, timeout, new java.util.concurrent.atomic.AtomicBoolean());
  }

  public SshCommandResult execute(SshConnection connection, String command, Duration timeout,
      java.util.concurrent.atomic.AtomicBoolean cancelled) throws Exception {
    checkCancelled(cancelled);
    return withSession(connection, timeout, session -> execute(session, command, timeout, cancelled));
  }

  <T> T withSession(SshConnection connection, Duration timeout, SessionOperation<T> operation) throws Exception {
    try (var client = SshClient.setUpDefaultClient()) {
      client.setServerKeyVerifier(new DefaultKnownHostsServerKeyVerifier(
          RejectAllServerKeyVerifier.INSTANCE,
          true));
      client.start();
      try (var session = client.connect(user(connection), connection.host(), connection.port())
          .verify(timeout.toMillis())
          .getSession()) {
        addIdentities(session, connection, credentials.passphrase(connection.id()));
        session.auth().verify(timeout.toMillis());
        return operation.apply(session);
      } finally {
        client.stop();
      }
    }
  }

  static SshCommandResult execute(
      org.apache.sshd.client.session.ClientSession session,
      String command,
      Duration timeout) throws Exception {
    return execute(session, command, timeout, new java.util.concurrent.atomic.AtomicBoolean());
  }

  static SshCommandResult execute(org.apache.sshd.client.session.ClientSession session,
      String command, Duration timeout, java.util.concurrent.atomic.AtomicBoolean cancelled) throws Exception {
    checkCancelled(cancelled);
    var stdout = new BoundedOutputStream(MAX_CAPTURE_BYTES);
    var stderr = new BoundedOutputStream(MAX_CAPTURE_BYTES);
    try (var channel = session.createExecChannel(command)) {
      channel.setOut(stdout);
      channel.setErr(stderr);
      channel.open().verify(timeout.toMillis());
      var deadline = System.nanoTime() + timeout.toNanos();
      while (true) {
        checkCancelled(cancelled);
        var remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          throw new java.util.concurrent.TimeoutException(
              "SSH command timed out after " + timeout.toSeconds() + " seconds");
        }
        var events = channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED),
            Math.max(1, Math.min(100, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining))));
        if (events.contains(ClientChannelEvent.CLOSED)) break;
      }
      var exit = channel.getExitStatus();
      return new SshCommandResult(
          exit == null ? -1 : exit,
          stdout.text(),
          stderr.text());
    }
  }

  private static void checkCancelled(java.util.concurrent.atomic.AtomicBoolean cancelled) {
    if (cancelled.get() || Thread.currentThread().isInterrupted()) {
      throw new java.util.concurrent.CancellationException("SSH command cancelled");
    }
  }

  public String test(SshConnection connection) throws Exception {
    var result = execute(connection, "printf JVibe-ssh-ok");
    return result.exitCode() == 0 && result.stdout().contains("JVibe-ssh-ok")
        ? "OK"
        : "Failed: " + result.stderr();
  }

  private static void addIdentities(
      org.apache.sshd.client.session.ClientSession session,
      SshConnection connection,
      String passphrase) throws Exception {
    if (connection.keyPath() != null && !connection.keyPath().isBlank()) {
      var path = Path.of(connection.keyPath().replaceFirst("^~", System.getProperty("user.home")));
      if (Files.isRegularFile(path)) {
        var provider = new FileKeyPairProvider(path);
        if (passphrase != null) {
          provider.setPasswordFinder(org.apache.sshd.common.config.keys.FilePasswordProvider.of(passphrase));
        }
        for (var key : provider.loadKeys(null)) {
          session.addPublicKeyIdentity(key);
        }
      }
    }
  }

  private static String user(SshConnection connection) {
    return connection.user() == null || connection.user().isBlank()
        ? System.getProperty("user.name")
        : connection.user();
  }

  @FunctionalInterface
  interface SessionOperation<T> {
    T apply(org.apache.sshd.client.session.ClientSession session) throws Exception;
  }
}

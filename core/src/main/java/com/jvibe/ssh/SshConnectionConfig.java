package com.jvibe.ssh;

import io.avaje.jsonb.Json;
import java.util.List;

@Json
public record SshConnectionConfig(List<SshConnection> connections, List<SshKeyRef> keys) {
  public SshConnectionConfig {
    connections = connections == null ? List.of() : List.copyOf(connections);
    keys = keys == null ? List.of() : List.copyOf(keys);
  }
}

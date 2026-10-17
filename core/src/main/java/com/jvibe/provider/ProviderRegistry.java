package com.jvibe.provider;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/** Fixed JVibe.net backend lookup. Alternate adapters are available only to isolated unit tests. */
@Singleton
public final class ProviderRegistry {
  private final ProviderClient client;

  public ProviderRegistry(JvibeProviderClient client) { this.client = client; }
  private ProviderRegistry(ProviderClient client) { this.client = client; }

  public static ProviderRegistry of(ProviderClient client) { return new ProviderRegistry(client); }

  public Optional<ProviderClient> find(String id) {
    return client.providerId().equals(id) ? Optional.of(client) : Optional.empty();
  }

  public ProviderClient require(String id) {
    return find(id).orElseThrow(() -> new IllegalArgumentException("Unsupported backend: " + id));
  }

  public List<ProviderClient> clients() { return List.of(client); }
}

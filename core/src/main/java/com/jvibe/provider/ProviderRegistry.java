package com.jvibe.provider;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;

/**
 * Runtime lookup for all configured model providers.
 *
 * <p>The registry is intentionally wired from {@code List<ProviderClient>} rather than from concrete
 * provider classes. Avaje injects every {@link ProviderClient} bean into this constructor, which
 * keeps provider extension simple: implement {@link ProviderClient}, annotate it as an Avaje bean,
 * and add its provider definition/config. No registry constructor change is required.
 */
@Singleton
public final class ProviderRegistry {
  private final Map<String, ProviderClient> clients;

  public ProviderRegistry(List<ProviderClient> clients) {
    this.clients = byProviderId(clients);
  }

  private ProviderRegistry(Map<String, ProviderClient> clients) {
    this.clients = Map.copyOf(clients);
  }

  public static ProviderRegistry of(ProviderClient client) {
    return new ProviderRegistry(Map.of(client.providerId(), client));
  }

  public Optional<ProviderClient> find(String providerId) {
    return Optional.ofNullable(clients.get(providerId));
  }

  public ProviderClient require(String providerId) {
    var client = clients.get(providerId);
    if (client == null) {
      throw new IllegalArgumentException("Unknown provider: " + providerId);
    }
    return client;
  }

  public List<ProviderClient> clients() {
    return clients.values().stream().toList();
  }

  private static Map<String, ProviderClient> byProviderId(List<ProviderClient> clients) {
    var indexed = new LinkedHashMap<String, ProviderClient>();
    for (var client : clients) {
      var previous = indexed.putIfAbsent(client.providerId(), client);
      if (previous != null) {
        throw new IllegalStateException("Duplicate provider id: " + client.providerId());
      }
    }
    return java.util.Collections.unmodifiableMap(indexed);
  }
}

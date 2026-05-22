package com.jvibe.provider;

import io.avaje.jsonb.Json;
import java.util.List;

@Json
public record ProvidersConfig(List<ProviderSettings> providers) {
  public ProvidersConfig {
    providers = providers == null ? List.of() : List.copyOf(providers);
  }
}

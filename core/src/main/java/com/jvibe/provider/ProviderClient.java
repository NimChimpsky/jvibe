package com.jvibe.provider;

import java.util.List;
import java.util.stream.Stream;

/** Streaming JVibe service adapter. The interface permits deterministic transport doubles in tests. */
public interface ProviderClient {
  String providerId();
  Stream<ProviderEvent> stream(ProviderRequest request);
  List<ModelDescriptor> models();
  List<ModelDescriptor> refreshModels(ProviderSettings settings);
}

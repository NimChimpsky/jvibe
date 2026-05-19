package com.jvibe.provider;

import java.util.List;
import java.util.stream.Stream;

public interface ProviderClient {
  Stream<ProviderEvent> stream(ProviderRequest request);

  List<ModelDescriptor> models();
}

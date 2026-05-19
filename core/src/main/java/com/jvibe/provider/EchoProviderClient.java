package com.jvibe.provider;

import java.util.List;
import java.util.stream.Stream;

public final class EchoProviderClient implements ProviderClient {
  @Override
  public Stream<ProviderEvent> stream(ProviderRequest request) {
    var last = request.messages().isEmpty() ? "" : request.messages().get(request.messages().size() - 1).content();
    return Stream.of(new ProviderEvent.AssistantDelta("Echo: " + last), new ProviderEvent.Done());
  }

  @Override
  public List<ModelDescriptor> models() {
    return List.of(new ModelDescriptor("local", "echo", "Local Echo", 8192, false));
  }
}

package com.jvibe.provider;

import java.util.List;
import java.util.stream.Stream;

/**
 * Provider adapter implemented by each model backend.
 *
 * <p>To add a provider, create a class that implements this interface and register it as an
 * Avaje bean, typically with {@code @Singleton}. {@link ProviderRegistry} receives all
 * {@code ProviderClient} beans through collection injection, so the registry does not need to be
 * edited when a new provider is added. The provider id returned here must match the id used by
 * {@link ProviderDefinition} and {@link ProviderSettings}.
 */
public interface ProviderClient {
  /**
   * Stable provider id, for example {@code openai} or {@code anthropic}.
   */
  String providerId();

  /**
   * Starts a provider request and emits streamed assistant text, tool calls, errors, and completion
   * events in provider-neutral form.
   */
  Stream<ProviderEvent> stream(ProviderRequest request);

  /**
   * Static or cached model descriptors available before a live refresh.
   */
  List<ModelDescriptor> models();

  /**
   * Refreshes the provider's model list using the supplied effective settings.
   */
  List<ModelDescriptor> refreshModels(ProviderSettings settings);
}

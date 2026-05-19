package com.jvibe.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.provider.ModelDescriptor;
import com.jvibe.provider.ProviderClient;
import com.jvibe.provider.ProviderEvent;
import com.jvibe.provider.ProviderRequest;
import com.jvibe.session.AgentSession;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.builtin.BuiltinTools;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRunnerTest {
  @TempDir Path temp;

  @Test
  void runsProviderToolCallAndReturnsDoneEvent() {
    var provider = new ProviderClient() {
      private final AtomicInteger calls = new AtomicInteger();

      @Override
      public Stream<ProviderEvent> stream(ProviderRequest request) {
        if (calls.getAndIncrement() == 0) {
          return Stream.of(new ProviderEvent.ToolCallRequested(new ToolCall("file.list", Map.of("path", "."))));
        }
        return Stream.of(new ProviderEvent.AssistantDelta("done"), new ProviderEvent.Done());
      }

      @Override
      public List<ModelDescriptor> models() {
        return List.of();
      }
    };

    var session = new AgentSession(new Workspace(temp), BuiltinTools.registry());
    var events = new AgentRunner(provider, "system", 10).run(session, "list files").toList();

    assertTrue(events.stream().anyMatch(event -> event.type() == com.jvibe.events.AgentEvent.Type.TOOL_FINISHED));
    assertTrue(events.stream().anyMatch(event -> event.type() == com.jvibe.events.AgentEvent.Type.DONE));
  }
}

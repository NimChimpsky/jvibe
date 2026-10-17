package com.jvibe.api;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jvibe.agent.AgentRunner;
import com.jvibe.session.SessionFactory;
import io.avaje.inject.BeanScope;
import org.junit.jupiter.api.Test;

class CoreDiTest {
  @Test
  void coreGraphIsInjectableWithTheJvibeClient() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(AgentRunner.class));
      assertNotNull(scope.get(SessionFactory.class));
      assertNotNull(scope.get(AccountService.class));
      assertInstanceOf(JvibeClient.class, scope.get(ModeClient.class));
    }
  }
}

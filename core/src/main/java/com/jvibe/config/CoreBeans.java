package com.jvibe.config;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

@Factory
public final class CoreBeans {
  @Bean
  JvibePaths jvibePaths() {
    return JvibePaths.userHome();
  }
}

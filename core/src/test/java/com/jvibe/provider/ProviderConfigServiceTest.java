package com.jvibe.provider;

import static org.junit.jupiter.api.Assertions.*;
import com.jvibe.config.JvibePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderConfigServiceTest {
  @TempDir Path home;

  @Test void jvibeIsTheOnlyBackendAndCannotBeDisabledOrRedirected() {
    var service = new ProviderConfigService(JvibePaths.under(home));
    assertEquals(List.of("jvibe"), service.definitions().stream().map(ProviderDefinition::id).toList());
    service.save(new ProviderSettings("jvibe", "https://untrusted.invalid", "secret", "model", "high", false));
    var saved = service.settings("jvibe");
    assertEquals(JvibeProviderClient.BASE_URL, saved.baseUrl());
    assertTrue(saved.enabled());
    assertEquals("jvibe", service.defaultProvider());
    assertThrows(IllegalArgumentException.class, () -> service.save(
        new ProviderSettings("other", "", "secret", "model", "none", true)));
  }

  @Test void keyModelAndThinkingRoundTripWithRestrictedPermissions() throws Exception {
    var paths = JvibePaths.under(home);
    var service = new ProviderConfigService(paths);
    service.save(new ProviderSettings("jvibe", "", "secret-key", "custom-model", "medium", true));
    assertEquals("secret-key", service.settings("jvibe").apiKey());
    assertEquals("custom-model", service.settings("jvibe").selectedModel());
    assertEquals("medium", service.settings("jvibe").thinking());
    assertNotEquals("secret-key", service.masked(service.settings("jvibe")).apiKey());
    assertFalse(service.settings("jvibe").toString().contains("secret-key"));
    if (Files.getFileStore(paths.providers()).supportsFileAttributeView("posix")) {
      assertEquals(java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
          java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(paths.providers()));
    }
  }

  @Test void unrelatedSavedProvidersAreNotExposedAndMalformedFilesAreNotOverwritten() throws Exception {
    var paths = JvibePaths.under(home);
    Files.createDirectories(paths.home());
    Files.writeString(paths.providers(), "{\"providers\":[{\"provider\":\"other\",\"apiKey\":\"old\"}]}");
    var service = new ProviderConfigService(paths);
    assertEquals(List.of("jvibe"), service.load().providers().stream().map(ProviderSettings::provider).toList());
    Files.writeString(paths.providers(), "{broken");
    assertThrows(IllegalStateException.class, service::load);
    assertEquals("{broken", Files.readString(paths.providers()));
  }
}

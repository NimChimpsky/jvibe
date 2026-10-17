package com.jvibe.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.JvibePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountServiceTest {
  @TempDir Path home;

  @Test
  void savesTheAccountReadableOnlyByTheOwner() throws Exception {
    var paths = JvibePaths.under(home);
    var accounts = new AccountService(paths, null);

    accounts.save(new Account("me@example.com", "jvibe_key", ""));

    assertEquals("jvibe_key", accounts.apiKey());
    assertFalse(accounts.account().toString().contains("jvibe_key"));
    if (Files.getFileStore(paths.account()).supportsFileAttributeView("posix")) {
      assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(paths.account()));
    }
  }

  @Test
  void environmentKeyOverridesTheStoredKey() {
    var accounts = new AccountService(JvibePaths.under(home), " jvibe_env ");
    accounts.save(new Account("me@example.com", "jvibe_file", ""));

    assertEquals("jvibe_env", accounts.apiKey());
    assertTrue(accounts.usesEnvironmentKey());
  }

  @Test
  void migratesTheKeyFromLegacyProvidersJsonOnce() throws Exception {
    var paths = JvibePaths.under(home);
    Files.createDirectories(paths.home());
    Files.writeString(paths.legacyProviders(),
        "{\"providers\":[{\"provider\":\"jvibe\",\"apiKey\":\"jvibe_legacy\",\"selectedModel\":\"jvibe-coder\"}]}");

    var accounts = new AccountService(paths, "");

    assertEquals("jvibe_legacy", accounts.apiKey());
    assertTrue(Files.exists(paths.account()));
    assertFalse(Files.exists(paths.legacyProviders()));
  }
}

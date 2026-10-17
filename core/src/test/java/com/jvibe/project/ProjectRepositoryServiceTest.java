package com.jvibe.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectRepositoryServiceTest {
  @TempDir
  Path temp;

  @Test
  void addsEnclosingGitRootAndPersistsIt() throws Exception {
    var repository = Files.createDirectory(temp.resolve("service"));
    Files.createDirectory(repository.resolve(".git"));
    var nested = Files.createDirectories(repository.resolve("src/main/java"));
    var config = new ConfigService(JvibePaths.under(temp.resolve("home")));
    var projects = new ProjectRepositoryService(config);

    var added = projects.add(nested);

    assertEquals(repository.toAbsolutePath(), added);
    assertEquals(java.util.List.of(repository.toAbsolutePath()), projects.projects());
    assertEquals(java.util.List.of(repository.toAbsolutePath().toString()), config.config().projectPaths());
  }

  @Test
  void removesProjectWithoutDeletingRepository() throws Exception {
    var repository = Files.createDirectory(temp.resolve("service"));
    Files.createDirectory(repository.resolve(".git"));
    var projects = new ProjectRepositoryService(
        new ConfigService(JvibePaths.under(temp.resolve("home"))));
    projects.add(repository);

    projects.remove(repository);

    assertTrue(projects.projects().isEmpty());
    assertTrue(Files.isDirectory(repository));
  }

  @Test
  void rejectsDirectoryOutsideGitRepository() throws Exception {
    var directory = Files.createDirectory(temp.resolve("plain"));
    var projects = new ProjectRepositoryService(
        new ConfigService(JvibePaths.under(temp.resolve("home"))));

    assertThrows(IllegalArgumentException.class, () -> projects.add(directory));
  }
}

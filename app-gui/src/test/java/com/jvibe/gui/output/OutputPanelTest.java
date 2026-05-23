package com.jvibe.gui.output;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputPanelTest {
  @TempDir
  Path temp;

  @Test
  void listsRegularFilesNewestFirst() throws Exception {
    var older = temp.resolve("older.txt");
    var newer = temp.resolve("newer.txt");
    var directory = temp.resolve("folder");
    Files.writeString(older, "old");
    Files.writeString(newer, "new");
    Files.createDirectory(directory);
    Files.setLastModifiedTime(older, FileTime.fromMillis(1_000));
    Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000));
    var logs = new ArrayList<String>();
    var panel = new OutputPanel(temp, (level, message) -> logs.add(level + ":" + message));

    assertEquals(java.util.List.of(newer, older), panel.files());
  }
}

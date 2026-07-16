package com.jvibe.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BoundedOutputStreamTest {
  @Test
  void capturesWithinLimit() throws Exception {
    var output = new BoundedOutputStream(8);

    output.write("hello".getBytes(StandardCharsets.UTF_8));

    assertEquals("hello", output.text());
  }

  @Test
  void truncatesWithoutGrowingPastLimit() throws Exception {
    var output = new BoundedOutputStream(4);

    output.write("abcdefgh".getBytes(StandardCharsets.UTF_8));

    assertTrue(output.text().startsWith("abcd\n"));
    assertTrue(output.text().contains("truncated at 4 bytes"));
  }
}

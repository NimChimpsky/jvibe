package com.jvibe.ssh;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class BoundedOutputStream extends OutputStream {
  private final int limit;
  private final ByteArrayOutputStream captured;
  private boolean truncated;

  BoundedOutputStream(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Output limit must be positive");
    }
    this.limit = limit;
    this.captured = new ByteArrayOutputStream(Math.min(limit, 8192));
  }

  @Override
  public synchronized void write(int value) {
    if (captured.size() < limit) {
      captured.write(value);
    } else {
      truncated = true;
    }
  }

  @Override
  public synchronized void write(byte[] bytes, int offset, int length) {
    var remaining = limit - captured.size();
    if (remaining > 0) {
      captured.write(bytes, offset, Math.min(remaining, length));
    }
    truncated |= length > remaining;
  }

  synchronized String text() {
    var value = captured.toString(StandardCharsets.UTF_8);
    return truncated ? value + "\n[output truncated at " + limit + " bytes]" : value;
  }
}

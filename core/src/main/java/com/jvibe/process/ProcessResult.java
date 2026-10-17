package com.jvibe.process;

public record ProcessResult(int exitCode, String stdout, String stderr, Status status) {
  public enum Status { EXITED, CANCELLED, TIMED_OUT }
}

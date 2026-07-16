package com.jvibe.ssh;

public record SshCommandResult(int exitCode, String stdout, String stderr) {}

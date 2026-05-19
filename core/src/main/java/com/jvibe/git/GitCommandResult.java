package com.jvibe.git;

public record GitCommandResult(int exitCode, String stdout, String stderr) {}

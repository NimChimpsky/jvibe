package com.jvibe.tool.builtin;

public record PatchResult(boolean applied, boolean dryRun, EditSummary summary, String failureReason) {}

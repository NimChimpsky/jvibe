package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;

public final class PatchApplyTool {
  private PatchApplyTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition(
            "file_apply_patch",
            "Apply patch",
            "Apply a unified diff inside the workspace after dry-run validation.",
            ToolSchemas.object(Map.of(
                "patch", ToolSchemas.string("Unified diff to apply."),
                "dryRun", ToolSchemas.bool("Validate the patch without writing files.")), "patch"),
            ApprovalRequest.Risk.HIGH),
        PatchApplyTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var patch = ToolArguments.string(call.arguments(), "patch");
    var dryRun = ToolArguments.bool(call.arguments(), "dryRun", false);
    if (patch.isBlank()) {
      return ToolResult.failure(call.id(), "Missing argument: patch");
    }
    var summary = summarize(patch, context.workspace().root());
    if (summary.files().isEmpty() || summary.hunks() == 0) {
      return ToolResult.failure(call.id(), "Patch does not look like a unified diff");
    }
    var check = gitApply(context.workspace().root(), patch, true);
    if (check.exitCode() != 0) {
      return ToolResult.failure(call.id(), "Patch dry-run failed: " + check.stderr());
    }
    if (!dryRun) {
      var apply = gitApply(context.workspace().root(), patch, false);
      if (apply.exitCode() != 0) {
        return ToolResult.failure(call.id(), "Patch apply failed: " + apply.stderr());
      }
    }
    var result = new PatchResult(!dryRun, dryRun, summary, "");
    return ToolResult.success(
        call.id(),
        Map.of(
            "applied", result.applied(),
            "dryRun", result.dryRun(),
            "files", summary.files(),
            "hunks", summary.hunks(),
            "additions", summary.additions(),
            "deletions", summary.deletions()),
        (dryRun ? "Validated patch for " : "Applied patch to ") + summary.files().size() + " file(s)");
  }

  static EditSummary summarize(String patch, Path workspace) {
    var files = new LinkedHashSet<String>();
    var hunks = 0;
    var additions = 0;
    var deletions = 0;
    for (var line : patch.split("\\R")) {
      if (line.startsWith("+++ ") || line.startsWith("--- ")) {
        var file = normalisePatchPath(line.substring(4).trim());
        if (file != null) {
          ensureInside(workspace, file);
          files.add(file);
        }
      } else if (line.startsWith("@@")) {
        hunks++;
      } else if (line.startsWith("+") && !line.startsWith("+++")) {
        additions++;
      } else if (line.startsWith("-") && !line.startsWith("---")) {
        deletions++;
      }
    }
    return new EditSummary(new ArrayList<>(files), hunks, additions, deletions);
  }

  private static String normalisePatchPath(String value) {
    var path = value.split("\\s+", 2)[0];
    if ("/dev/null".equals(path)) {
      return null;
    }
    if (path.startsWith("a/") || path.startsWith("b/")) {
      path = path.substring(2);
    }
    if (path.startsWith("/") || path.contains("..")) {
      throw new IllegalArgumentException("Patch path escapes workspace: " + path);
    }
    return path;
  }

  private static void ensureInside(Path workspace, String file) {
    var resolved = workspace.resolve(file).normalize();
    if (!resolved.startsWith(workspace)) {
      throw new IllegalArgumentException("Patch path escapes workspace: " + file);
    }
  }

  private static ProcessResult gitApply(Path workspace, String patch, boolean check) throws Exception {
    var command = new ArrayList<String>();
    command.add("git");
    command.add("apply");
    if (check) {
      command.add("--check");
    }
    var process = new ProcessBuilder(command)
        .directory(workspace.toFile())
        .redirectErrorStream(false)
        .start();
    try (var input = process.getOutputStream();
        var patchInput = new ByteArrayInputStream(patch.getBytes(StandardCharsets.UTF_8))) {
      patchInput.transferTo(input);
    }
    var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new ProcessResult(process.waitFor(), stdout, stderr);
  }

  private record ProcessResult(int exitCode, String stdout, String stderr) {}
}

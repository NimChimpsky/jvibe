package com.jvibe.tool.builtin;

import com.jvibe.git.GitService;
import com.jvibe.process.ProcessRunner;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolRegistry;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Local tool sets: read-only attachments for Chat, and the full repository tool set for Build. */
@Singleton
public final class FileToolSet {
  private final GitService git;
  private final ProcessRunner processes;

  public FileToolSet(GitService git, ProcessRunner processes) {
    this.git = git;
    this.processes = processes;
  }

  public static ToolRegistry chat(Supplier<List<Path>> attachments) {
    var access = FileToolAccess.attachments(attachments);
    return new ToolRegistry(List.of(FileListTool.registered(access), FileReadTool.registered(access)));
  }

  public ToolRegistry build() {
    var access = FileToolAccess.workspace();
    var tools = new ArrayList<>(List.of(
        FileListTool.registered(access),
        FileReadTool.registered(access),
        FileWriteTool.registered(access),
        FilePatchTool.registered(),
        PatchApplyTool.registered(git),
        ShellRunTool.registered(processes)));
    tools.addAll(GitTools.all(git));
    return new ToolRegistry(tools);
  }
}

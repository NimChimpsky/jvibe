# jvibe Implementation Plan

## 1. Product Summary

`jvibe` is a Java-based LLM coding agent with shared CLI and JavaFX GUI runtimes. It should provide a Codex/Claude Code style workflow while leaning into the JVM ecosystem: Java-native tooling, Gradle builds, JVM process control, Git integration, dynamic Java plugins, and future JMX/JFR/profiling support.

Primary deliverables:

- `jvibe-cli.jar`: command-line coding agent.
- `jvibe.jar`: JavaFX desktop app.
- Shared core runtime used by both entry points.
- Built-in tools for shell, files, Git, config, and dynamic tool management.
- Agent-created Java tools that can be compiled, loaded, approved, and used without rebuilding jvibe.

MVP principle:

- Build the CLI and core runtime first.
- Keep the GUI thin over the same core runtime.
- Make dynamic Java tools foundational, before advanced JVM diagnostics.
- Defer autonomous long-running tasks, JMX/JFR/profiling, PR creation, and native installers until after MVP.

## 2. Decisions And Constraints

### Language, Runtime, And Build

- Target Java 21 bytecode with `--release 21`; use a Java 21 Gradle toolchain when available, but allow newer local JDKs to compile Java 21-compatible output.
- Use Gradle multi-module build.
- Generate and commit the Gradle wrapper.
- Use Avaje Inject, Config, Jsonb, and logging.
- Use JavaFX for GUI.
- Use JUnit 5 and Mockito for tests.
- Use JDK `HttpClient` initially for provider HTTP calls.
- Use shell `git` behind a `GitService` abstraction for MVP.
- Consider JGit, picocli, RichTextFX, and OkHttp only if implementation friction justifies them.

### Safety Model

- Shell, file, Git, and dynamic tool actions go through central approval policy.
- GUI requires approval for shell, file writes, Git commits/pushes, first dynamic tool execution, and new dynamic tool dependencies.
- CLI requires approval by default, with an explicit `--yes` option for trusted workflows.
- Dynamic Java tools are trusted code after approval, not sandboxed code.
- Approved plugin tools get broad core access through `ToolPluginContext`.
- Secrets must never be printed in logs, session files, tool output summaries, or error messages.

### GitHub State

- Local Git repository is already initialized on `main`.
- GitHub remote is still pending.
- To push, add a supplied remote URL as `origin` and run `git push -u origin main`.

## 3. Repository And Module Layout

Target Gradle modules:

```text
jvibe-root/
  settings.gradle.kts
  build.gradle.kts
  gradlew
  gradlew.bat
  gradle/
  core/
  app-cli/
  app-gui/
  testing/
```

Module responsibilities:

- `core`: agent loop, provider abstraction, tools, dynamic tool registry, config, sessions, Git service, approval policy, event model.
- `app-cli`: CLI entry point, interactive REPL, slash commands, terminal prompts, CLI rendering.
- `app-gui`: JavaFX entry point, view models/controllers, chat UI, tool approval UI, settings, history.
- `testing`: shared fixtures for temp workspaces, fake providers, mock tool calls, temp Git repos, and dynamic tool test bundles.

Core package groups:

- `com.jvibe.agent`
- `com.jvibe.config`
- `com.jvibe.events`
- `com.jvibe.git`
- `com.jvibe.provider`
- `com.jvibe.session`
- `com.jvibe.tool`
- `com.jvibe.tool.dynamic`
- `com.jvibe.workspace`

## 4. Core Domain Model

Initial core types:

- `AgentSession`: active conversation, workspace, selected model, available tools, approval state.
- `AgentRunner`: owns the request/tool-call loop for one session.
- `AgentEvent`: streaming event emitted to CLI/GUI.
- `Conversation`: ordered messages, tool calls, tool results, usage, timestamps.
- `Message`: role, content parts, provider metadata.
- `ProviderClient`: provider-neutral LLM interface.
- `ProviderRequest`: messages, model, available tools, system prompt, generation options.
- `ProviderEvent`: assistant delta, tool call, usage, error, completion.
- `ModelDescriptor`: provider, model id, display name, context limits, tool support.
- `ToolDefinition`: name, description, JSON schema, approval policy, executor binding.
- `ToolCall`: id, name, JSON arguments, source provider metadata.
- `ToolResult`: id, success flag, structured payload, stderr/log summary, error metadata.
- `ToolExecutor`: built-in or dynamic executor interface.
- `ApprovalRequest`: action type, risk level, summary, details, requested permissions.
- `Workspace`: root path, Git status, jvibe workspace metadata.
- `JvibeConfig`: user-level config.

Dynamic tool types:

- `DynamicToolRegistry`: discovers, validates, builds, loads, reloads, disables, and publishes dynamic tools.
- `ToolManifest`: parsed `tool.json`.
- `ToolPlugin`: Java plugin contract implemented by dynamic tools.
- `ToolPluginContext`: broad service registry available to approved plugins.
- `ToolBuildService`: compiles simple tools with `javac` and dependency-backed tools with Gradle.
- `ToolClassLoaderFactory`: creates isolated per-tool classloaders.
- `ToolApprovalStore`: tracks first-run approval and dependency approval.

## 5. Configuration And Persistence

User config directory:

```text
~/.jvibe/
  config.json
  providers.json
  prompts/
    system.md
  sessions/
  tools/
  logs/
```

Workspace metadata:

```text
<workspace>/.jvibe/
  tools/
  state.json
```

First-run behavior:

- Create `~/.jvibe` if missing.
- Write default `config.json`.
- Write default `prompts/system.md`.
- Write empty `providers.json`.
- Create empty `sessions`, `tools`, and `logs` directories.
- Do not create fake or placeholder API keys.

Provider key precedence:

1. Environment variables, for example `OPENAI_API_KEY` and `ANTHROPIC_API_KEY`.
2. User provider config references.
3. GUI/CLI setup prompts.

Secrets policy:

- Never store plaintext secrets in sessions.
- Redact likely secret values in logs and tool output.
- Consider macOS Keychain support after MVP.

Session persistence:

- Store sessions as JSON under `~/.jvibe/sessions/{session-id}.json`.
- Persist workspace path, selected provider/model, messages, tool calls, tool results, usage, timestamps, and approval decisions.
- Do not persist dynamic tool source inside sessions; persist references to tool ids and versions.

## 6. Provider Integration

Implement OpenAI first, Anthropic second.

Provider contract:

```java
interface ProviderClient {
  Stream<ProviderEvent> stream(ProviderRequest request);
  List<ModelDescriptor> models();
}
```

OpenAI MVP:

- Streaming assistant output.
- Provider-native tool call mapping into `ToolCall`.
- Usage tracking where available.
- Error mapping for authentication, model access, quota, rate limits, malformed requests, and network failures.
- Retry transient network and 5xx errors with bounded backoff.

Anthropic follow-up:

- Implement after OpenAI loop and tool-call model are stable.
- Map provider-specific message/tool semantics into the same `ProviderEvent` stream.

Provider-neutral behavior:

- Core agent loop must not depend on provider-specific message classes.
- Tool schemas are generated once from `ToolDefinition` and adapted by provider mappers.
- Provider errors become structured events for both CLI and GUI.

## 7. Agent Loop

MVP flow:

1. User submits prompt.
2. `AgentRunner` builds `ProviderRequest` from session state, system prompt, model, and tool registry.
3. Provider streams assistant deltas and tool calls.
4. CLI/GUI renders streaming events.
5. Tool calls are validated and routed to `ToolExecutor`.
6. Approval policy blocks risky actions until accepted.
7. Tool results are appended to the conversation.
8. The loop continues until final assistant response or max iterations.
9. Session is persisted.

Loop constraints:

- One active run per session.
- Bounded tool-call iterations.
- Tool execution timeout.
- Cancellation token shared with provider stream and tool executors.
- Full structured event log for debugging.
- Failed tool calls return structured errors to the model unless the failure is fatal.

Default limits:

- Max tool iterations per prompt: 10.
- Default shell timeout: 120 seconds.
- Default dynamic tool build timeout: 180 seconds.
- Default dynamic tool runtime timeout: 120 seconds.
- Truncate model-visible tool output with a clear marker; keep full output in logs where safe.

## 8. Tool System

All built-in and dynamic tools expose the same shape:

- id/name.
- description.
- JSON input schema.
- approval metadata.
- executor.
- structured result.

Built-in MVP tools:

- `shell.run`
- `file.read`
- `file.write`
- `file.patch`
- `file.list`
- `git.status`
- `git.diff`
- `git.commit`
- `git.push`
- `tool.list`
- `tool.inspect`
- `tool.create`
- `tool.build`
- `tool.reload`
- `tool.disable`
- `tool.delete`
- `tool.approve`

Shell tool:

- Use `ProcessBuilder`.
- Run in workspace root by default.
- Capture stdout and stderr separately.
- Stream output as events.
- Enforce timeout and cancellation.
- Require approval unless policy says the command is safe or user passed `--yes`.

File tools:

- Use Java NIO.
- Normalize and validate paths against workspace root.
- Reject writes outside workspace unless explicitly approved.
- Prefer patch-style edits for reviewability.
- Show before/after diff before write in GUI and CLI approval flows.

Git tools:

- Use shell `git` behind `GitService`.
- MVP supports status, diff, add selected files, commit, and push current branch.
- Commit and push require approval.
- Branch creation, PR creation, and conflict resolution UI are post-MVP.

## 9. Dynamic Java Tools

Dynamic Java tools are a first-class feature. The agent can create a tool, compile it, register it, and call it in the same session without restarting or rebuilding core.

Discovery order:

1. Project tools: `<workspace>/.jvibe/tools/`
2. User tools: `~/.jvibe/tools/`

Collision rule:

- If project and user scopes define the same tool id, the project tool wins for that workspace.

Tool bundle layout:

```text
<tool-id>/
  tool.json
  src/
  build.gradle        optional, required for dependency-backed tools
  tests/              optional
  build/              generated
  dist/<tool-id>.jar  generated
```

Manifest fields:

- `id`: stable tool id.
- `name`: display name.
- `description`: provider-facing description.
- `version`: tool version.
- `pluginApiVersion`: required jvibe plugin API version.
- `entryClass`: Java class implementing `ToolPlugin`.
- `inputSchema`: JSON schema exposed to providers.
- `permissions`: declared capabilities such as shell, file-read, file-write, git, network, provider, config.
- `dependencies`: Maven coordinates requested by the tool.
- `enabled`: whether the tool is available for discovery.
- `approved`: whether first-run approval has been granted.

Build paths:

- Simple no-dependency tools compile through embedded `javac`.
- Tools with Maven dependencies or tests build through Gradle.
- New Maven dependencies require explicit user approval before build.

Runtime:

- Each compiled tool loads through an isolated per-tool classloader.
- Reload replaces the classloader and republishes the tool definition.
- Compile/load/runtime failure disables only that tool and emits a structured error.
- Approved plugins get broad access to `ToolPluginContext`; this is not a sandbox.

Agent workflow:

1. Agent identifies a missing capability.
2. Agent proposes a dynamic tool and target scope.
3. User approves creation if the action writes files.
4. Agent writes the bundle.
5. jvibe validates `tool.json`.
6. jvibe builds via `javac` or Gradle.
7. User approves any new dependencies.
8. jvibe loads and registers the tool.
9. User approves first execution.
10. Agent calls the tool like any built-in tool.

Dynamic tool management:

- CLI and GUI can list tools.
- Inspect manifest, source path, compiled jar, status, dependencies, and last error.
- Approve first run.
- Rebuild/reload.
- Disable/delete.
- Show scope and collision source.

## 10. CLI Design

Entrypoint:

```bash
java -jar app-cli/build/libs/jvibe-cli.jar
```

Top-level commands:

- `jvibe chat`
- `jvibe run "prompt"`
- `jvibe config`
- `jvibe models`
- `jvibe sessions`
- `jvibe tools`

Interactive slash commands:

- `/help`
- `/model`
- `/status`
- `/diff`
- `/commit`
- `/push`
- `/config`
- `/tools`
- `/tool approve`
- `/tool reload`
- `/tool disable`
- `/clear`
- `/exit`

CLI behavior:

- Stream assistant output.
- Render tool calls and approvals clearly.
- Prompt before risky actions.
- Support `--yes` for trusted non-interactive runs.
- Exit non-zero for fatal runtime/config errors.

## 11. GUI Design

Primary layout:

- Left sidebar: workspace, sessions, settings.
- Main pane: chat transcript.
- Bottom composer: prompt input, model selector, send/stop.
- Right pane: tool activity, diffs, approvals, Git status.

Views:

- Chat.
- Files/diffs.
- Git.
- Tools.
- Settings.
- Session history.

MVP GUI features:

- Open/select workspace.
- Select provider/model.
- Send prompt and stop active run.
- View streaming response.
- View tool calls and results.
- Approve shell/file/Git actions.
- Manage dynamic tools: list, inspect source/manifest, approve first run, rebuild/reload, disable, delete.
- Configure provider keys.
- View chat history.

Architecture:

- GUI must call core services, not duplicate agent behavior.
- Keep JavaFX controllers thin.
- Put state and actions in view models that can be unit tested.

## 12. Build, Packaging, And CI

Build command:

```bash
./gradlew clean build
```

Artifacts:

- `app-cli/build/libs/jvibe-cli.jar`
- `app-gui/build/libs/jvibe.jar`

Run commands:

```bash
java -jar app-cli/build/libs/jvibe-cli.jar
java -jar app-gui/build/libs/jvibe.jar
```

Packaging decisions:

- No DMG, EXE, native image, or installer in v1.
- Jars must be runnable from a fresh clone after `./gradlew build`.
- JavaFX runtime dependencies must be handled by Gradle packaging.

CI after GitHub remote exists:

- Build on macOS and Linux.
- Run unit and integration tests.
- Cache Gradle dependencies.
- Upload CLI/GUI jars as build artifacts.

## 13. Testing Strategy

Unit tests:

- Config loading and first-run defaults.
- Provider request/event mapping.
- Tool schema generation.
- Approval policy decisions.
- File path validation.
- File patch application.
- Git service command construction.
- Agent loop iteration limits, cancellation, and tool error recovery.
- Dynamic tool manifest parsing and validation.
- User/project tool discovery and precedence.
- Per-tool classloader load/reload behavior.
- First-run approval and dependency approval blocking.

Integration tests:

- Shell tool executes and captures output.
- File tools operate inside a temp workspace.
- Git service works in a temp repository.
- Provider clients work against a mock HTTP server.
- Simple dynamic Java tool compiles through embedded `javac`.
- Dependency-backed dynamic Java tool builds through Gradle after approval.
- Dynamic tool becomes callable in the same session without restart.
- Broken tool compile/load/runtime failures disable only that tool.

GUI tests:

- Unit test view models/controllers.
- Smoke test app startup.
- Smoke test prompt send with fake provider.
- Smoke test dynamic tool approval UI with fake registry.

Acceptance scenarios:

- Fresh clone builds successfully with `./gradlew clean build`.
- First run creates config directories and default files.
- CLI chat can stream a fake provider response in tests.
- Agent can read, edit, diff, commit, and push from a temp Git repo in tests.
- Agent can create and run a simple dynamic Java tool without restarting.
- GUI opens, sends a prompt, displays tool activity, and handles approval flow.

## 14. Milestones

### Milestone 0: Repository Foundation

- Add Gradle wrapper and multi-module skeleton.
- Add license decision.
- Add CI once remote exists.
- Push `main` after GitHub remote is provided.

### Milestone 1: Core Runtime

- Config service.
- Session model.
- Provider-neutral message/event model.
- Tool interfaces and approval policy.
- Public plugin API and `ToolPluginContext`.
- Dynamic tool manifest model.
- Logging and structured events.

### Milestone 2: Dynamic Tool Foundation

- Dynamic tool registry.
- Project and user tool discovery.
- Manifest validation.
- Embedded `javac` build path.
- Gradle build path for dependency-backed tools.
- Per-tool classloader loading and hot reload.
- First-run and dependency approval flow.
- CLI tool management commands.

### Milestone 3: Built-In Tools And CLI MVP

- Interactive CLI.
- OpenAI provider.
- Shell/file/Git tools.
- Dynamic tool creation and same-session use.
- Slash commands.
- Session persistence.
- Core unit/integration tests.

### Milestone 4: GUI MVP

- JavaFX app shell.
- Chat transcript.
- Model selector.
- Prompt composer.
- Tool activity and approval panel.
- Diff review flow.
- Dynamic tool management UI.
- Settings screen.

### Milestone 5: Provider And Tool Expansion

- Anthropic provider.
- Better model metadata.
- More slash commands.
- Safer permission profiles.
- Improved Git flows.

### Milestone 6: JVM Ecosystem Tools

- JMX queries.
- JFR recording.
- Thread dumps.
- GC stats.
- JVM profiling integrations.
- Prefer implementing these as bundled dynamic Java tools where practical.

### Milestone 7: Autonomous Long Tasks

- Background task runner.
- Checkpointing.
- Resume/cancel.
- Task history.
- Notification hooks.

## 15. Immediate Next Steps

1. Get GitHub repository URL and push current `main`.
2. Add Gradle wrapper and multi-module skeleton.
3. Configure Java 21 bytecode targeting and baseline dependencies.
4. Implement config service and first-run defaults.
5. Implement provider-neutral events, sessions, and tool interfaces.
6. Implement approval policy.
7. Implement dynamic tool manifest, registry, and simple `javac` build path.
8. Implement OpenAI provider behind `ProviderClient`.
9. Implement shell/file/Git tools.
10. Build CLI MVP.
11. Add JavaFX GUI MVP.

## 16. MVP Definition Of Done

MVP is complete when:

- A user can clone the repo and run `./gradlew clean build`.
- The CLI jar starts interactive chat.
- The GUI jar starts a JavaFX chat app.
- First run creates default config and prompt files.
- User can configure an OpenAI API key.
- Agent can request shell/file/Git tool calls.
- User can approve or reject risky actions.
- Chat history persists across restarts.
- Git status, diff, commit, and push work from CLI and GUI.
- Agent can create, compile, register, approve, and call a simple dynamic Java tool without restarting.
- New Maven dependencies for dynamic tools require approval before build.
- Tests cover core agent loop, config, built-in MVP tools, and dynamic tool lifecycle.

## 17. Post-MVP Backlog

- Anthropic provider.
- JMX/JFR/profiling tools.
- Autonomous long-running tasks.
- PR creation.
- Branch management and conflict resolution UI.
- macOS Keychain integration.
- Rich code editor/diff viewer.
- Native installers.
- Remote tool marketplace or sharing workflow.

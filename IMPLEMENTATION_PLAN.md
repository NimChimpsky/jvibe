# jvibe Implementation Plan

## 1. Project Summary

`jvibe` is a Java-based LLM coding agent with both desktop GUI and CLI entry points. It should provide Codex/Claude Code style workflows while leaning into the JVM ecosystem: Java-native tooling, Gradle builds, JVM process control, Git integration, JavaFX UI, and future support for JMX, JFR, profiling, and runtime diagnostics.

Primary deliverables:

- `jvibe.jar`: JavaFX desktop application.
- `jvibe-cli.jar`: command-line application.
- Shared core runtime used by both GUI and CLI.
- Extensible tool architecture for shell, file editing, Git, provider APIs, and JVM-specific tools.

## 2. Source Requirements

The initial requirements are captured in `jvibe-plan.md`.

Core requirements:

- Java implementation.
- Gradle build system.
- Avaje dependencies for injection, config, JSON, and logging.
- JavaFX GUI.
- GUI should resemble the OpenAI Codex app workflow.
- CLI and GUI share the same core dependencies and architecture.
- MVC-style separation for UI, state, and business logic.
- User-home configuration with first-run defaults.
- OpenAI and Anthropic API key configuration.
- Shell access implemented through JVM process APIs.
- File editing implemented through JVM file APIs.
- Git integration for common operations, especially commit and push.
- Slash commands inspired by aider, opencode, Claude Code, Codex, and Cline.
- Long-term extensibility for autonomous work, long-running tasks, JMX, JFR, profiling, and JVM diagnostics.

## 3. Key Concerns And Open Decisions

### 3.1 Scope Risk

The requested product spans a CLI agent, desktop app, provider integrations, tool runtime, autonomous task system, Git workflows, config management, and JVM diagnostics. Building all of that at once is too broad for a reliable first release.

Decision:

- Ship an MVP around chat, tool calls, shell/file/Git, config, and one provider first.
- Add the JavaFX app early, but keep it thin over the shared core.
- Treat JVM diagnostics, autonomous long tasks, and advanced slash commands as post-MVP unless needed to prove the architecture.

### 3.2 Provider Abstraction

OpenAI and Anthropic APIs differ in request/response schema, streaming format, tool calling, model naming, and usage reporting.

Decision:

- Define an internal provider-neutral model for messages, tool calls, streaming events, errors, and usage.
- Implement OpenAI first.
- Add Anthropic after the core agent loop is stable.

### 3.3 Tool Safety

Shell access and file editing are powerful and risky. The plan currently says "use JVM", but does not define confirmation, sandboxing, cwd boundaries, command timeouts, or audit history.

Decision:

- Every tool call must run through a central `ToolExecutor`.
- Shell commands get timeouts, working directory tracking, stdout/stderr capture, and cancellation.
- File edits must be reviewable before write in the GUI and logged in the CLI.
- Destructive Git/file operations require explicit confirmation unless the user opts into an unsafe mode.

### 3.4 JavaFX Packaging

JavaFX is no longer bundled with the JDK. Packaging runnable jars requires a deliberate Gradle setup.

Decision:

- Use Gradle application plugins and JavaFX plugin support.
- Produce two runnable artifacts: GUI and CLI.
- Do not target native installers, DMGs, or EXEs in v1.
- Document the required Java version clearly.

### 3.5 "Codex-like GUI" Ambiguity

"Replicate the OpenAI Codex app GUI" needs to be interpreted carefully. The goal should be workflow familiarity, not copying private implementation details or visual assets.

Decision:

- Build a similar functional layout: project/workspace panel, chat transcript, prompt composer, model selector, tool activity panel, file diff/review area, and settings.
- Use original styling.

### 3.6 GitHub Push Workflow

The local folder is not currently a Git repository. GitHub CLI is not installed in this environment, so pushing to GitHub needs either:

- A remote repository URL supplied by the user.
- GitHub CLI installed/configured.
- A GitHub connector/API flow that can create repos, if available.

Decision:

- Initialize the local repo now.
- Commit the planning baseline.
- Add/push remote once a GitHub repo URL is available.

## 4. Target Architecture

Use a layered architecture with shared core logic and thin UI adapters.

```text
jvibe-root
  app-gui          JavaFX desktop application
  app-cli          CLI application
  core             agent loop, sessions, providers, tools, config, Git
  testing          shared test fixtures and integration test helpers
```

Recommended Gradle modules:

- `:core`
- `:app-cli`
- `:app-gui`
- `:testing`

The GUI and CLI should never duplicate agent behavior. They should both call the same core services.

## 5. Core Domain Model

Initial domain types:

- `AgentSession`: active conversation plus workspace state.
- `Conversation`: ordered messages, tool calls, tool results, usage.
- `Message`: role, content parts, metadata.
- `ProviderClient`: abstraction over OpenAI, Anthropic, and future providers.
- `ModelDescriptor`: provider, model id, context limits, tool support.
- `ToolDefinition`: name, description, JSON schema, executor.
- `ToolCall`: provider-neutral tool invocation.
- `ToolResult`: success/failure payload, logs, metadata.
- `Workspace`: root directory, config, Git state.
- `JvibeConfig`: user-level config loaded from home directory.

## 6. Technology Choices

### Required

- Java 21 LTS.
- Gradle.
- JavaFX for GUI.
- Avaje Inject.
- Avaje Config.
- Avaje Jsonb.
- Avaje SLF4J/logging integration.
- JUnit 5.
- Mockito.

### Dependencies To Evaluate

Only add these if they materially reduce risk:

- JGit for Git operations.
- picocli for CLI parsing.
- RichTextFX or CodeArea-style component for GUI code/diff rendering.
- OkHttp or Java 21 `HttpClient` for provider APIs.

Default preference:

- Use JDK APIs first where reasonable.
- Add mature libraries where hand-rolling would create protocol, parsing, terminal, or UI risk.

## 7. Configuration Design

Config directory:

```text
~/.jvibe/
  config.json
  providers.json
  prompts/
    system.md
  sessions/
  logs/
```

First-run behavior:

- Create `~/.jvibe` if missing.
- Write default config.
- Write default system prompt.
- Create empty provider config.
- Do not create placeholder API keys that look valid.

API key handling:

- Prefer environment variables first.
- Support local config references.
- Avoid printing secrets in logs.
- Consider macOS Keychain support after MVP.

## 8. Provider Integration Plan

### MVP Provider

Implement OpenAI first:

- Chat/completions style request abstraction based on current API choice.
- Streaming responses.
- Tool call emission.
- Usage tracking.
- Retry handling for transient failures.
- Clear error mapping for auth, model access, rate limits, quota, and network errors.

### Second Provider

Add Anthropic once the provider-neutral event model is proven.

Provider interface:

```java
interface LlmProvider {
  Stream<AgentEvent> stream(ProviderRequest request);
  List<ModelDescriptor> models();
}
```

## 9. Agent Loop

The core loop should support:

- User prompt.
- Model request.
- Streaming assistant output.
- Tool call detection.
- Tool execution.
- Tool result return to model.
- Final assistant response.
- Cancellation.
- Session persistence.

MVP loop constraints:

- One active task at a time per session.
- Bounded tool-call recursion.
- Max iteration limit.
- Tool timeout support.
- Full event log for debugging.

## 10. Tool Architecture

Tool APIs:

- Tools declare name, description, input schema, and executor.
- Executors receive workspace, config, cancellation token, and typed input.
- Results are structured and serializable.

MVP tools:

- `shell.run`
- `file.read`
- `file.write`
- `file.patch`
- `file.list`
- `git.status`
- `git.diff`
- `git.commit`
- `git.push`

Post-MVP JVM tools:

- `jmx.query`
- `jfr.start`
- `jfr.stop`
- `jfr.summary`
- `jvm.gcStats`
- `jvm.threadDump`
- `jvm.heapSummary`

## 11. Shell Tool

Implementation:

- Use `ProcessBuilder`.
- Default cwd is current workspace root.
- Capture stdout/stderr separately.
- Stream output as agent events.
- Enforce timeout.
- Support cancellation.
- Limit output size returned to the model.
- Keep full output in logs where appropriate.

Open decision:

- Whether commands require confirmation by default in GUI, CLI, or both.

Recommendation:

- GUI: confirm by default.
- CLI: confirm by default, with `--yes` for trusted flows.

## 12. File Editing Tool

Implementation:

- Use Java NIO.
- Represent edits as patches where possible.
- Normalize path handling against workspace root.
- Reject paths outside the workspace unless explicitly allowed.
- Maintain before/after diff.

GUI behavior:

- Show proposed changes.
- Allow accept/reject.
- Track changed files in a workspace panel.

CLI behavior:

- Print concise diff summary.
- Prompt before applying unless `--yes`.

## 13. Git Integration

MVP:

- Status.
- Diff.
- Add selected files.
- Commit.
- Push.

Implementation options:

- Start with shelling out to `git` for correctness and familiarity.
- Wrap it behind `GitService` so JGit can be introduced later if needed.

Commit/push flow:

- Show changed files.
- Generate or accept commit message.
- Run commit.
- Push current branch.
- Report remote URL and commit SHA.

Out of scope for MVP:

- Branch creation automation.
- PR creation.
- Conflict resolution UI.

## 14. CLI Design

Entrypoint:

```bash
java -jar jvibe-cli.jar
```

Expected commands:

- `jvibe chat`
- `jvibe run "prompt"`
- `jvibe config`
- `jvibe models`
- `jvibe sessions`

Interactive slash commands:

- `/help`
- `/model`
- `/status`
- `/diff`
- `/commit`
- `/push`
- `/config`
- `/clear`
- `/exit`

Post-MVP slash commands:

- `/jfr`
- `/jmx`
- `/profile`
- `/threads`
- `/gc`

## 15. GUI Design

Primary layout:

- Left sidebar: workspace, sessions, settings.
- Main pane: chat transcript.
- Bottom composer: prompt input, model selector, send/stop.
- Right pane: tool activity, diffs, Git status.

Views:

- Chat.
- Files/diffs.
- Git.
- Settings.
- Session history.

MVP GUI features:

- Start/open workspace.
- Select model.
- Send prompt.
- View streaming response.
- View tool calls and results.
- Approve shell/file/Git actions.
- Configure providers/API keys.
- View chat history.

## 16. Persistence

Session files:

```text
~/.jvibe/sessions/{session-id}.json
```

Each session stores:

- Workspace path.
- Model/provider.
- Messages.
- Tool calls.
- Tool results.
- Timestamps.
- Token/usage metadata where available.

Do not persist secrets in session logs.

## 17. Testing Strategy

Unit tests:

- Config loading and first-run defaults.
- Provider request mapping.
- Tool schema generation.
- File path validation.
- File patch application.
- Git service command construction.
- Agent loop iteration limits and cancellation.

Integration tests:

- Shell tool executes and captures output.
- File tools operate inside temp workspace.
- Git service works in temp repository.
- Provider clients can be tested with mock HTTP server.

GUI tests:

- Keep JavaFX UI logic thin.
- Unit test view models/controllers.
- Add smoke tests for app startup where practical.

## 18. Build And Release

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

No native installers in v1.

## 19. Milestones

### Milestone 0: Repository Foundation

- Initialize Git repository.
- Add README, requirements, implementation plan, license decision, and `.gitignore`.
- Add Gradle multi-module skeleton.
- Add CI later once GitHub remote exists.

### Milestone 1: Core Runtime

- Config service.
- Session model.
- Provider-neutral message model.
- Tool interfaces.
- Event bus/streaming model.
- Logging.

### Milestone 2: CLI MVP

- Interactive CLI.
- OpenAI provider.
- Shell/file/Git tools.
- Slash commands.
- Session persistence.
- Unit/integration tests.

### Milestone 3: GUI MVP

- JavaFX shell.
- Chat transcript.
- Model selector.
- Prompt composer.
- Tool activity panel.
- Diff approval flow.
- Settings screen.

### Milestone 4: Provider And Tool Expansion

- Anthropic provider.
- More slash commands.
- Better Git flows.
- Improved model metadata.
- Safer permission profiles.

### Milestone 5: JVM Ecosystem Features

- JMX queries.
- JFR recording.
- Thread dumps.
- GC stats.
- JVM profiling integrations.

### Milestone 6: Autonomous Long Tasks

- Background task runner.
- Checkpointing.
- Resume/cancel.
- Notification hooks.
- Task history.

## 20. Immediate Next Steps

1. Initialize the Git repository.
2. Commit this planning baseline.
3. Decide GitHub owner/repo and visibility.
4. Create remote repository.
5. Push `main`.
6. Scaffold Gradle multi-module project.
7. Implement config loading and first-run defaults.
8. Implement provider-neutral core model.
9. Implement OpenAI provider behind abstraction.
10. Build CLI MVP before GUI complexity expands.

## 21. Definition Of Done For MVP

The MVP is complete when:

- A user can clone the repo and run `./gradlew build`.
- The CLI jar can start an interactive chat.
- The GUI jar can start a basic JavaFX chat app.
- The user can configure an OpenAI API key.
- The agent can request shell/file/Git tool calls.
- The user can approve or reject risky actions.
- Chat history persists across restarts.
- Git status, commit, and push work from the app.
- Tests cover the core agent loop, config, and MVP tools.


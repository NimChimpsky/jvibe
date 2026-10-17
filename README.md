# JVibe

A Java desktop assistant for enterprise Java developers: inline chat, a coding agent for Git
repositories, plain-English server administration over SSH, and SQL over JDBC.

## Requirements

**JDK 21+** on macOS, Linux or Windows.

## Build and run

```bash
./gradlew jvibe                 # build and launch
./gradlew build                 # tests + app-gui/build/libs/jvb.jar
java -jar app-gui/build/libs/jvb.jar
```

On Windows use `.\gradlew.bat`. `jvb.jar` is self-contained; copy it to any machine with JDK 21+.

## First start

JVibe asks for your email. It returns an API key straight away and emails you a link; open it
and approve the code the app shows, and the key becomes active. The key is stored in
`~/.jvibe/account.json` (owner-only permissions) and is visible in the JVibe tab and on
[jvibe.net/account](https://jvibe.net/account), so you can reuse it on other machines.
`JVIBE_API_KEY` in the environment overrides the stored key.

## Tabs

- **Chat:** inline answers. Attach files or folders for read-only context; nothing is written.
- **Build:** one tab per Git project with a coding agent (read, edit, patch, shell, Git), an
  embedded terminal and a Git status/diff panel. Add repositories with **+**.
- **Servers:** import PuTTY sessions or add connections, then attach servers with `@name`
  (or double-click) and describe what you need. JVibe runs the commands over SSH, across
  several servers at once, and shows each command inline. Host keys are checked against
  `~/.ssh/known_hosts`.
- **Databases:** saved JDBC connections (external driver jars supported). Attach with `@name`
  (or double-click) and ask in plain English; JVibe inspects the schema, runs SQL and shows the
  latest result in a table. Passwords stay in memory only.
- **JVibe:** account and credits, thinking level, tool-call limit, theme, and per-tab custom
  instructions and **Ask before running** (approve each command, edit or SQL statement).

Tabs can be dragged to reorder. Configuration and logs live in `~/.jvibe`.

## How it works

Each tab calls its own endpoint, `POST https://jvibe.net/api/v1/modes/{chat|build|servers|databases}`.
The service owns the system prompts and tool definitions; the app sends the conversation,
tool results, the tab's context (attached files, servers or connections) and your custom
instructions, and executes the tools the model requests locally. See `../jvibe-web/README.md`.

For local development against a local service: `-Djvibe.apiUrl=http://localhost:3000/api/v1`.

Tools run with your permissions: Build commands, SSH commands and SQL are not sandboxed.
Turn on **Ask before running** for tabs where you want to approve every change. Stop cancels the
model request and cooperative tools; it does not undo completed changes.

## Code layout

- `core` — `api` (JVibe client, account, streaming), `agent` (run loop), `session`, `tool`
  (local tool implementations), `ssh`, `jdbc`, `config`
- `app-gui` — Swing UI (FlatLaf, JediTerm); Avaje Inject wires both modules

Avaje Config, Inject and Jsonb conventions are in `.agents/skills/`.

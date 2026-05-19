# jvibe

`jvibe` is a Java-based LLM coding agent with shared CLI and JavaFX GUI runtimes.

The project goals are captured in:

- `jvibe-plan.md`: initial requirements.
- `IMPLEMENTATION_PLAN.md`: expanded implementation plan, concerns, architecture, and milestones.

## Build

```bash
./gradlew clean build
```

## Run

```bash
java -jar app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar run "hello"
java -jar app-gui/build/libs/jvibe-0.1.0-SNAPSHOT.jar
```

## Current State

The repository has a working multi-module Gradle skeleton, a core agent/tool runtime, config bootstrap, built-in shell/file/Git tools, dynamic Java tool compilation/loading, a CLI entry point, a JavaFX GUI shell, and core tests.

The Avaje coding skills used for this project are versioned under `.agents/skills/`.

# JVibe

Java-native coding, JVM analysis, remote administration, and agent orchestration in one Swing desktop application.

## Requirements

- Full JDK 21+ on `PATH`. A JRE is insufficient because dynamic tools use `javac`.
- macOS, Linux, or Windows.
- Provider API key configured in **Providers**.

## Build And Run

macOS/Linux:

```bash
./gradlew jvibe
```

Windows PowerShell:

```powershell
.\gradlew.bat jvibe
```

The task builds `:app-gui:installDist` and launches the installed application. Distribution archives are created with:

```bash
./gradlew clean build :app-gui:distZip :app-gui:distTar
```

## CLI And Autonomous Agent

Build the self-contained CLI jar:

```bash
./gradlew :app-cli:jar
java -jar app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar run "Inspect this project"
```

The fat jar can be copied to a server or sandbox. `deploy local` and `deploy ssh` install it as a background agent; add `--dry-run` to print a deployment plan. SSH deployment uses verified `~/.ssh/known_hosts`, a configured private key, and copies provider settings with owner-only permissions.

```bash
java -jar app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar deploy ssh \
  --host server --user deploy --key ~/.ssh/id_ed25519 \
  --workspace /srv/app --path ~/.jvibe/agent
```

The optional task gateway binds to localhost by default:

```bash
java -jar app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar gateway --workspace /srv/app
```

## Main Features

- **Desktop:** isolated chat output under `~/.jvibe/tmp`, or workspace tabs for in-place editing.
- **Remote SSH:** OpenSSH/PuTTY import, key-based connections, read-only or full-access remote tools.
- **Agent Orchestrator:** autonomous deployment and lightweight JVM service monitoring.
- **JFR / Stacktrace:** local Flight Recorder and JVM diagnostic analysis.
- **Dynamic Java tools:** source or Gradle-backed plugins hot-build and load without rebuilding JVibe. New tools require explicit approval in the Tools panel or CLI.

Applications can publish JVM health to the orchestrator with `monitor-agent/build/libs/jvibe-monitor-*.jar`, either through `JvibeMonitor.start()` or `-javaagent`. Configure `jvibe.monitor.orchestratorUrl` and related `jvibe.monitor.*` properties.

Local configuration is stored under `~/.jvibe`. Provider keys are written with owner-only permissions where the platform supports them. Monitoring intake is unauthenticated in this version; expose it only on a trusted private development network.

Avaje Config, Inject, and Jsonb conventions are documented in the versioned `.agents/skills/` directory.

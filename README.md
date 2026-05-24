# JVibe

Java-based LLM coding agent and agent orchestrator.  JVM focussed tools, and dynamic tool creation.

## Build All

Requires a full JDK 21+ on `PATH`. A JRE is not enough because JVibe builds dynamic Java tools with `javac` and Gradle.

```bash
./gradlew jvibe
```

## Notes

- Use `installDist`/`distZip` for GUI distribution.
- Use the CLI fat jar for servers, sandboxes, and autonomous agent runs.
- Install a full JDK, not just a runtime, when dynamic Java tools are needed.
- Avaje coding skills are versioned under `.agents/skills/`.

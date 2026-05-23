# JVibe

Java-based LLM coding agent with CLI and Swing GUI runtimes.

## Build All

Requires a full JDK 21+ on `PATH`. A JRE is not enough because JVibe builds dynamic Java tools with `javac` and Gradle.

```bash
./gradlew clean build
```

## CLI Fat Jar

Build:

```bash
./gradlew :app-cli:jar
```

Run:

```bash
java -jar app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar run "hello"
```

Copy that jar to a server/sandbox and run it with JDK 21+:

```bash
scp app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar user@host:/opt/jvibe/
ssh user@host 'java -jar /opt/jvibe/jvibe-cli-0.1.0-SNAPSHOT.jar run "inspect this repo"'
```

## GUI Development

The desktop app uses Swing with FlatLaf and bundled IntelliJ-style themes.

```bash
./gradlew :app-gui:run
```

Provider keys and model selection are managed in the GUI Settings panel. Keys are saved to `~/.jvibe/providers.json`; blank keys fall back to `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `MISTRAL_API_KEY`, or `DEEPSEEK_API_KEY`.

CLI runs use the saved default provider:

```bash
./gradlew :app-cli:run --args='run "hello"'
```

## GUI Distribution

The GUI distribution is a normal JVM app bundle with dependency jars and launcher scripts. It expects JDK 21+ to be installed on the target machine so dynamic Java tools can compile and run against the available JDK.

### macOS

```bash
./gradlew :app-gui:installDist
app-gui/build/install/jvibe/bin/jvibe
```

```bash
./gradlew :app-gui:distZip
unzip app-gui/build/distributions/jvibe.zip
./jvibe/bin/jvibe
```

### Windows

```powershell
.\gradlew.bat :app-gui:installDist
app-gui\build\install\jvibe\bin\jvibe.bat
```

```powershell
.\gradlew.bat :app-gui:distZip
Expand-Archive app-gui\build\distributions\jvibe.zip
.\jvibe\bin\jvibe.bat
```

### Linux

```bash
./gradlew :app-gui:installDist
app-gui/build/install/jvibe/bin/jvibe
```

```bash
./gradlew :app-gui:distZip
unzip app-gui/build/distributions/jvibe.zip
./jvibe/bin/jvibe
```

## Themes

Use the theme selector in the GUI. Bundled options include FlatLaf Dark/Light, IntelliJ Light, Darcula, Arc Dark, Dracula, Gruvbox Dark, Monokai Pro, Nord, One Dark, Solarized, GitHub, and High Contrast.

## Notes

- Use `installDist`/`distZip` for GUI distribution.
- Use the CLI fat jar for servers, sandboxes, and autonomous agent runs.
- Install a full JDK, not just a runtime, when dynamic Java tools are needed.
- Avaje coding skills are versioned under `.agents/skills/`.

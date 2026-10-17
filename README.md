# JVibe

Java-native chat, project development, SSH administration and database work.

## Requirements

Full **JDK 21+** on PATH, on macOS, Linux or Windows. Dynamic Java tools require javac.

## Build And Run

macOS/Linux:

```bash
./gradlew jvibe
```

Windows PowerShell:

```powershell
.\gradlew.bat jvibe
```

Create distributable GUI archives:

```bash
./gradlew clean build :app-gui:distZip :app-gui:distTar
```

## Tabs

- **Chat:** inline answers and read-only file/folder attachments. No file edits, exports, shell or Git access.
- **Build:** Git projects, coding chat, in-place editing, embedded terminal and Git status/diff. Add an existing Git repository with **+**.
- **Servers:** import PuTTY sessions, manage SSH keys and connections, and attach hosts with **@** to inspect or administer them. Access mode is per connection; host keys are verified against known_hosts.
- **Databases:** saved JDBC connections, natural-language SQL generation, editable SQL, explicit execution and query/update results. External JDBC driver jars are supported. Passwords remain in memory.
- **JVibe:** API key, account credits, model/thinking settings, custom instructions and theme. Theme changes apply immediately.

**JVibe.net is the only supported service.** Register or buy credits at [jvibe.net/account](https://jvibe.net/account). Set the API key in the JVibe tab or through JVIBE_API_KEY. The endpoint is fixed; there is no provider or endpoint selector.

Main tabs can be dragged to reorder; startup selects the first tab. Configuration and logs live under ~/.jvibe.

## CLI

```bash
./gradlew :app-cli:jar
java -jar app-cli/build/libs/jvibe-cli-0.1.0-SNAPSHOT.jar run "Inspect this project"
```

On Windows, use .\gradlew.bat for the build command. The self-contained CLI jar can be copied to another machine with JDK 21+. Run it from the project directory; omit run to start interactive chat. The same JVibe API key configuration applies.

Build shells and approved dynamic Java tools are trusted code, not an OS sandbox. Use restricted accounts or containers for untrusted projects. Stop cancels provider requests and cooperative tools; it does not undo completed changes. SSH read-only command checks are conservative, not a remote sandbox.

Avaje Config, Inject and Jsonb conventions are in .agents/skills/.

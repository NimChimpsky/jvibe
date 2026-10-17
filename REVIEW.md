# Current Product Scope

The GUI has five reorderable main tabs: Chat, Build, Servers, Databases and JVibe.
Removed functionality includes diagnostics, orchestration/monitoring, deployment, file output,
alternate provider integrations and OpenSSH configuration import.

## Access And Architecture

- Chat answers inline. Its registry exposes only attachment-guarded file_read/file_list. No tmp writes, shell, Git, patch or dynamic tools are exposed.
- Attached paths remain readable for the submitted conversation after composer chips are cleared.
- Build operates on selected Git projects, with workspace tools, a terminal and Git inspection.
- Servers uses PuTTY imports and Apache MINA SSHD. Connections retain their access mode and known-host verification.
- Databases separates generated SQL from explicit execution, with schema context and displayed results.
- Only the injected JVibe client is registered. Account settings cannot disable it or change its fixed endpoint.
- Shared application services use Avaje constructor injection; per-view Swing state is created by injected factories.
- Prompts and custom preferences do not grant tools. Runtime registries enforce mode access independently.

## Verification And Limits

Automated tests cover tab composition/order, DI resolution, attachment permissions, forbidden Chat writes,
Build Git requirements, account settings/key masking, fixed endpoints, streaming and tool-call history,
cancellation/process cleanup, SSH policy/imports and JDBC results/concurrency.

Live JVibe credentials, remote SSH hosts and external database instances are not available for integration checks.
Build shell commands and Java plugins are trusted, not OS-sandboxed; cancellation is cooperative and completed edits
are not rolled back. SSH read-only validation is not a formal remote sandbox.

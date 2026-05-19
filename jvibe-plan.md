# Task and Requirements
We are going to build an agent for llm usage, similar to openclaw/hermes and code/claude code.  It will be written in Java, with the goal of an easy to extend tooling architecture and deep integration with the jvm and the java ecosystem


Java based llm agent : jvibe

GUI accessed via jvibe.jar
Cli version via jvibe-cli.jar

Should use avaje - inject, config, json, and logging.

Java fx for the GUI.

GUI should replicate the openai codex app GUI.

The GUI and CLI should use the same dependencies, a MVC architecture. 

Key features gui : 
Chat prompt with model selection.
Chat history
Shell access (use jvm)
Edit files (using jvm)
Git integration - just commit and push
Edit and config of API keys using openai and anthropic endpoints.

Key features cli : 
Shell access (use jvm)
Edit files (using jvm)
Git integration
Commonly used  forward slash commands - pi, opencode, Claude code, codex, cline.  

Config stored in user home dir. On startup check for existence, populate if nothing there with system prompt, config

Long term goal is an easily extensible architecture for adding tooling tightly integrated with jvm and java ecosystem :

JMX beans, jfr recording, vm profiling, gc stats

Should support similar functionality as openclaw and Hermes, messaging, autonomous long tasks.

use gradle build system, only pull in depedencies if absolutely necessary - well supported.  Use mockito and junit for unit testing.

build/deployment process is to be able to perform a gradle build, then run either the gui or cli jar to open app. Expected pipeline is users clone the git repo and build for themselves. We won't build exe's or dmg
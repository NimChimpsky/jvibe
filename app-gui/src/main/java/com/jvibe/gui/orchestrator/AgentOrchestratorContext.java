package com.jvibe.gui.orchestrator;

import java.nio.file.Path;

public record AgentOrchestratorContext(Path workspace, String provider, String model) {}

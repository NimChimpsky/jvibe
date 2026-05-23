package com.jvibe.gui.swarm;

import java.nio.file.Path;

public record AgentSwarmContext(Path workspace, String provider, String model) {}

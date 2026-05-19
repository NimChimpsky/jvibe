package com.jvibe.tool.dynamic;

import java.nio.file.Path;

public record ToolBuildResult(boolean success, Path jar, String output) {}

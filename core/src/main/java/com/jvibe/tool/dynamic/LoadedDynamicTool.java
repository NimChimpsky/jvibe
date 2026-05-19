package com.jvibe.tool.dynamic;

import java.net.URLClassLoader;
import java.nio.file.Path;

public record LoadedDynamicTool(ToolManifest manifest, Path jar, URLClassLoader classLoader, ToolPlugin plugin) {}

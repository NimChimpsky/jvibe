package com.jvibe.gui.analysis;

import java.util.List;

public record ParsedThread(String name, String state, List<String> frames, List<String> lockLines) {}

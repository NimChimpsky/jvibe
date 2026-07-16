package com.jvibe.gui.analysis;

import java.util.List;

public record ParsedException(String type, String message, List<String> frames) {}

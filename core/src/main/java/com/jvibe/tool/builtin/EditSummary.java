package com.jvibe.tool.builtin;

import java.util.List;

public record EditSummary(List<String> files, int hunks, int additions, int deletions) {}

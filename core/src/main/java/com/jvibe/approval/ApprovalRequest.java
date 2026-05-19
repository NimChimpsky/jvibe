package com.jvibe.approval;

import java.util.Map;

public record ApprovalRequest(String action, Risk risk, String summary, Map<String, Object> details) {
  public enum Risk {
    LOW,
    MEDIUM,
    HIGH
  }
}

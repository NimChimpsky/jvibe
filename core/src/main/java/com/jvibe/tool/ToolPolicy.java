package com.jvibe.tool;

import com.jvibe.approval.ApprovalDecision;
import com.jvibe.approval.ApprovalRequest;
import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibeConfig;
import jakarta.inject.Singleton;

@Singleton
public final class ToolPolicy {
  private final ConfigService configService;

  public ToolPolicy(ConfigService configService) {
    this.configService = configService;
  }

  public ApprovalDecision decide(ToolDefinition definition) {
    try {
      return decide(definition, configService.config());
    } catch (Exception e) {
      return definition.risk() == ApprovalRequest.Risk.LOW ? ApprovalDecision.APPROVED : ApprovalDecision.REQUIRED;
    }
  }

  public static ApprovalDecision decide(ToolDefinition definition, JvibeConfig config) {
    if (definition.risk() == ApprovalRequest.Risk.LOW) {
      return ApprovalDecision.APPROVED;
    }
    if ("autonomous".equalsIgnoreCase(config.autonomyMode())
        || "fully_autonomous".equalsIgnoreCase(config.autonomyMode())) {
      return allowed(definition, config) ? ApprovalDecision.APPROVED : ApprovalDecision.REQUIRED;
    }
    return config.requireApproval() ? ApprovalDecision.REQUIRED : ApprovalDecision.APPROVED;
  }

  private static boolean allowed(ToolDefinition definition, JvibeConfig config) {
    return config.allowedHighRiskTools().isEmpty()
        || config.allowedHighRiskTools().contains("*")
        || config.allowedHighRiskTools().contains(definition.id());
  }
}

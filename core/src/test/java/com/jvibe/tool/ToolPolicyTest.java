package com.jvibe.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jvibe.approval.ApprovalDecision;
import com.jvibe.approval.ApprovalRequest;
import com.jvibe.config.JvibeConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolPolicyTest {
  private final ToolDefinition high = new ToolDefinition("shell_run", "Shell", "Run shell", Map.of(), ApprovalRequest.Risk.HIGH);
  private final ToolDefinition low = new ToolDefinition("file_list", "List", "List files", Map.of(), ApprovalRequest.Risk.LOW);

  @Test
  void lowRiskToolsAreApproved() {
    assertEquals(ApprovalDecision.APPROVED, ToolPolicy.decide(low, JvibeConfig.defaults()));
  }

  @Test
  void highRiskToolsRequireApprovalWhenSupervised() {
    assertEquals(ApprovalDecision.REQUIRED, ToolPolicy.decide(high, JvibeConfig.defaults()));
  }

  @Test
  void highRiskToolsExecuteWhenAutonomousAndAllowed() {
    var config = new JvibeConfig("local", "echo", false, 10, "FLATLAF_DARK", "autonomous", List.of("*"), false, "127.0.0.1", 8737, List.of());

    assertEquals(ApprovalDecision.APPROVED, ToolPolicy.decide(high, config));
  }
}

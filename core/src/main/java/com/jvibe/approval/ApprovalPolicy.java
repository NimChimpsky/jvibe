package com.jvibe.approval;

public interface ApprovalPolicy {
  ApprovalDecision decide(ApprovalRequest request);

  static ApprovalPolicy alwaysApprove() {
    return request -> ApprovalDecision.APPROVED;
  }

  static ApprovalPolicy requireRiskyApproval() {
    return request -> request.risk() == ApprovalRequest.Risk.LOW
        ? ApprovalDecision.APPROVED
        : ApprovalDecision.REQUIRED;
  }
}

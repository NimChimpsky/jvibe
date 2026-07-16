package com.jvibe.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SshImportServiceTest {
  @Test
  void parsesOpenSshConfig() {
    var connections = SshImportService.parseOpenSshConfig(List.of(
        "Host prod",
        "  HostName 10.0.0.10",
        "  User deploy",
        "  Port 2222",
        "  IdentityFile ~/.ssh/prod_ed25519"));

    assertEquals(1, connections.size());
    assertEquals("prod", connections.getFirst().name());
    assertEquals("10.0.0.10", connections.getFirst().host());
    assertEquals("deploy", connections.getFirst().user());
    assertEquals(2222, connections.getFirst().port());
    assertEquals("~/.ssh/prod_ed25519", connections.getFirst().keyPath());
  }

  @Test
  void parsesPuttySession() {
    var connection = SshImportService.parsePuttySession("prod", List.of(
        "HostName=server.example.com",
        "UserName=deploy",
        "PortNumber=22",
        "PublicKeyFile=/keys/prod.ppk")).orElseThrow();

    assertEquals("prod", connection.name());
    assertEquals("server.example.com", connection.host());
    assertEquals("/keys/prod.ppk", connection.keyPath());
  }

  @Test
  void readOnlyPolicyAllowsInspectionAndRejectsMutation() {
    var policy = new SshReadOnlyPolicy();

    assertTrue(policy.isAllowed("find /tmp -type f | sort | tail -1"));
    assertTrue(!policy.isAllowed("rm -rf /tmp/foo"));
    assertTrue(!policy.isAllowed("find /tmp -type f > /tmp/out"));
    assertTrue(!policy.isAllowed("find /tmp -delete"));
    assertTrue(!policy.isAllowed("awk 'BEGIN { system(\"rm -rf /tmp/foo\") }'"));
    assertTrue(!policy.isAllowed("sed -i s/a/b/ file"));
    assertTrue(policy.isAllowed("grep -i error app.log | head -20"));
  }
}

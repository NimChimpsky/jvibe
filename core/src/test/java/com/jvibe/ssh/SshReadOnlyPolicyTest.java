package com.jvibe.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SshReadOnlyPolicyTest {
  private final SshReadOnlyPolicy policy = new SshReadOnlyPolicy();

  @ParameterizedTest
  @ValueSource(strings = {
      "find /var/log -type f -name '*.log' -printf '%s %p\\n' | sort -nr | head -10",
      "cat -- '/tmp/two words.txt'", "grep 'a|b' log.txt | wc -l",
      "awk '{print $1,$2}' /tmp/data", "sed -n '1,20p' /tmp/data", "date -u +%Y-%m-%d",
      "find /tmp -mtime -1 -print", "du -sh -- /tmp"
  })
  void permitsInspection(String command) {
    assertTrue(policy.isAllowed(command), command);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "sort -o /tmp/output /tmp/input", "sort -nro/tmp/output /tmp/input",
      "sort --output=/tmp/output /tmp/input", "sort --compress-program=sh /tmp/input",
      "sort --out=/tmp/output /tmp/input", "file --comp -m /tmp/magic",
      "sed -i.bak s/a/b/ /tmp/input", "sed 'w /tmp/output' /tmp/input",
      "sed 'e whoami' /tmp/input", "awk -f /tmp/script /tmp/input",
      "awk 'BEGIN {system(\"id\")}'", "date --set=tomorrow", "file -C -m /tmp/magic",
      "find /tmp -fprintf /tmp/output '%p'", "find /tmp -fprint0 /tmp/output",
      "find /tmp -exec rm '{}' '+'", "ls; whoami", "ls&whoami", "ls\nwhoami",
      "cat $(whoami)", "cat \"$FILE\"", "cat `whoami`", "ls || whoami", "ls |", "| ls",
      "cat 'unterminated", "sort *", "sort $OPTIONS file", "cat < /tmp/input",
      "cat /tmp/input > /tmp/output", "LS /tmp"
  })
  void rejectsWritesAndShellExpansion(String command) {
    assertFalse(policy.isAllowed(command), command);
  }
}

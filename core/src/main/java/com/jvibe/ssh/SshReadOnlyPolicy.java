package com.jvibe.ssh;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Conservative command filter, not a replacement for a restricted remote account. */
@Singleton
public final class SshReadOnlyPolicy {
  private static final Set<String> ALLOWED = Set.of(
      "pwd", "ls", "find", "du", "df", "cat", "grep", "egrep", "fgrep", "awk", "sed",
      "head", "tail", "stat", "ps", "uname", "whoami", "sort", "wc", "file", "date");
  private static final Set<String> FIND_OPTIONS = Set.of(
      "-H", "-L", "-P", "-O0", "-O1", "-O2", "-O3", "-maxdepth", "-mindepth", "-depth",
      "-xdev", "-mount", "-follow", "-noleaf", "-name", "-iname", "-path", "-ipath",
      "-wholename", "-iwholename", "-regex", "-iregex", "-regextype", "-type", "-xtype",
      "-size", "-mtime", "-mmin", "-atime", "-amin", "-ctime", "-cmin", "-newer",
      "-user", "-group", "-uid", "-gid", "-perm", "-links", "-inum", "-empty",
      "-readable", "-writable", "-executable", "-print", "-print0", "-printf", "-ls",
      "-prune", "-quit", "-true", "-false", "-a", "-and", "-o", "-or", "-not");

  public boolean isAllowed(String command) {
    var stages = tokenize(command);
    return !stages.isEmpty() && stages.stream().allMatch(this::allowedStage);
  }

  private boolean allowedStage(List<String> words) {
    if (words.isEmpty() || !ALLOWED.contains(words.getFirst())) return false;
    var args = words.subList(1, words.size());
    return switch (words.getFirst()) {
      case "find" -> args.stream().noneMatch(arg -> arg.startsWith("-")
          && !FIND_OPTIONS.contains(arg) && !arg.matches("-\\d+[cwbkMGs]?"));
      case "sort" -> args.stream().noneMatch(arg -> shortOptionContains(arg, 'o')
          || unknownLongOption(arg, Set.of("--", "--numeric-sort", "--human-numeric-sort",
              "--general-numeric-sort", "--reverse", "--unique", "--key", "--field-separator",
              "--ignore-case", "--ignore-leading-blanks", "--stable", "--zero-terminated", "--check")));
      case "file" -> args.stream().noneMatch(arg -> shortOptionContains(arg, 'C')
          || unknownLongOption(arg, Set.of("--", "--brief", "--mime", "--mime-type", "--mime-encoding",
              "--dereference", "--no-dereference", "--separator", "--keep-going", "--raw")));
      // Scripts can write or execute commands. Only these small inspection forms are accepted.
      case "awk" -> !args.isEmpty() && args.getFirst().matches(
          "\\{\\s*print\\s+\\$\\d+(\\s*,\\s*\\$\\d+)*\\s*}")
          && args.subList(1, args.size()).stream().noneMatch(arg -> arg.startsWith("-") || arg.contains("="));
      case "sed" -> args.size() >= 2 && args.getFirst().equals("-n")
          && args.get(1).matches("\\d+(,\\d+)?p")
          && args.subList(2, args.size()).stream().noneMatch(arg -> arg.startsWith("-"));
      case "date" -> args.stream().allMatch(arg -> Set.of("-u", "--utc", "--universal", "-R", "--rfc-email", "-I", "--iso-8601").contains(arg)
          || arg.startsWith("+"));
      default -> true;
    };
  }

  private static boolean shortOptionContains(String arg, char option) {
    return arg.startsWith("-") && !arg.startsWith("--") && arg.indexOf(option) >= 0;
  }

  private static boolean unknownLongOption(String arg, Set<String> allowed) {
    return arg.startsWith("--") && !allowed.contains(arg.split("=", 2)[0]);
  }

  // Accept only words, quoting, and pipelines. Reject expansion, escaping, and shell control syntax.
  // Unquoted globs could expand into mutating command options on the remote host.
  private static List<List<String>> tokenize(String command) {
    if (command == null || command.isBlank()) return List.of();
    var stages = new ArrayList<List<String>>();
    var words = new ArrayList<String>();
    var word = new StringBuilder();
    char quote = 0;
    boolean started = false;
    for (char c : command.toCharArray()) {
      if (Character.isISOControl(c) && c != '\t') return List.of();
      if (quote != 0) {
        if (c == quote) quote = 0;
        else {
          if (quote == '"' && (c == '$' || c == '`' || c == '\\')) return List.of();
          word.append(c);
        }
      } else if (c == '\'' || c == '"') {
        quote = c;
        started = true;
      } else if (Character.isWhitespace(c) || c == '|') {
        if (started) {
          words.add(word.toString());
          word.setLength(0);
          started = false;
        }
        if (c == '|') {
          if (words.isEmpty()) return List.of();
          stages.add(List.copyOf(words));
          words.clear();
        }
      } else {
        if (";&<>`$\\(){}*?[]~#".indexOf(c) >= 0) return List.of();
        word.append(c);
        started = true;
      }
    }
    if (quote != 0) return List.of();
    if (started) words.add(word.toString());
    if (words.isEmpty()) return List.of();
    stages.add(List.copyOf(words));
    return stages;
  }
}

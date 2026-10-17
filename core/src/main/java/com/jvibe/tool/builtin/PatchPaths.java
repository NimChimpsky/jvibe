package com.jvibe.tool.builtin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Decodes the C-style UTF-8 path quoting emitted by git diff. */
final class PatchPaths {
  private PatchPaths() { }

  static String unquote(String path) {
    if (path.length() < 2 || !path.endsWith("\"")) {
      throw new IllegalArgumentException("Unterminated quoted patch path");
    }
    var output = new ByteArrayOutputStream();
    for (int i = 1; i < path.length() - 1; i++) {
      char c = path.charAt(i);
      if (c != '\\') {
        int point = path.codePointAt(i);
        output.writeBytes(new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8));
        i += Character.charCount(point) - 1;
        continue;
      }
      if (++i >= path.length() - 1) throw new IllegalArgumentException("Invalid quoted patch path");
      c = path.charAt(i);
      if (c >= '0' && c <= '7') {
        int octal = c - '0';
        for (int n = 1; n < 3 && i + 1 < path.length() - 1; n++) {
          char next = path.charAt(i + 1);
          if (next < '0' || next > '7') break;
          octal = octal * 8 + next - '0';
          i++;
        }
        output.write(octal);
      } else {
        output.write(switch (c) {
          case 'a' -> 7;
          case 'b' -> 8;
          case 't' -> 9;
          case 'n' -> 10;
          case 'v' -> 11;
          case 'f' -> 12;
          case 'r' -> 13;
          case '\\' -> '\\';
          case '"' -> '"';
          default -> throw new IllegalArgumentException("Unknown escape in patch path");
        });
      }
    }
    return output.toString(StandardCharsets.UTF_8);
  }
}

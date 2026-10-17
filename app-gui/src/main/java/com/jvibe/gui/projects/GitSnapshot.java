package com.jvibe.gui.projects;

record GitSnapshot(String branch, java.util.List<String> changes, String diff, String error) {
  static GitSnapshot error(String message) {
    return new GitSnapshot("", java.util.List.of(), "", message);
  }
}

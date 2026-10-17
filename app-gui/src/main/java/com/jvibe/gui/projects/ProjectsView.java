package com.jvibe.gui.projects;

import com.jvibe.gui.chat.DesktopChatController;
import javax.swing.JComponent;

public final class ProjectsView implements AutoCloseable {
  private final JComponent component;
  private final DesktopChatController chat;

  ProjectsView(JComponent component, DesktopChatController chat) {
    this.component = component;
    this.chat = chat;
  }

  public JComponent component() {
    return component;
  }

  public void refreshModelSelectors() {
    chat.refreshModelSelectors();
  }

  @Override
  public void close() {
    chat.close();
  }
}

package com.jvibe.gui.chat;

import com.jvibe.config.ConfigService;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.session.SessionFactory;
import io.avaje.inject.External;
import jakarta.inject.Singleton;

@Singleton
public final class DesktopChatFactory {
  private final ConfigService configService;
  private final ModelSelectionController modelSelection;
  private final ChatPromptRunner promptRunner;
  private final SessionFactory sessions;

  public DesktopChatFactory(
      @External ConfigService configService,
      ModelSelectionController modelSelection,
      ChatPromptRunner promptRunner,
      @External SessionFactory sessions) {
    this.configService = configService;
    this.modelSelection = modelSelection;
    this.promptRunner = promptRunner;
    this.sessions = sessions;
  }

  public DesktopChatController create(DesktopChatController.Callbacks callbacks) {
    return new DesktopChatController(
        configService, modelSelection, promptRunner, sessions, callbacks, null, false);
  }

  public DesktopChatController createWorkspace(
      java.nio.file.Path workspace,
      DesktopChatController.Callbacks callbacks) {
    return new DesktopChatController(
        configService, modelSelection, promptRunner, sessions, callbacks, workspace, true);
  }

  public DesktopChatController createBuild(DesktopChatController.Callbacks callbacks) {
    return new DesktopChatController(configService, modelSelection, promptRunner, sessions, callbacks, null, true);
  }
}

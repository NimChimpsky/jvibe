package com.jvibe.gui.chat;

import com.jvibe.config.ConfigService;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.gui.controller.PromptController;
import com.jvibe.logging.FileLogService;
import com.jvibe.session.SessionFactory;
import io.avaje.inject.External;
import jakarta.inject.Singleton;

@Singleton
public final class DesktopChatFactory {
  private final ConfigService configService;
  private final ModelSelectionController modelSelection;
  private final PromptController prompts;
  private final FileLogService fileLog;
  private final SessionFactory sessions;

  public DesktopChatFactory(
      @External ConfigService configService,
      ModelSelectionController modelSelection,
      PromptController prompts,
      @External FileLogService fileLog,
      @External SessionFactory sessions) {
    this.configService = configService;
    this.modelSelection = modelSelection;
    this.prompts = prompts;
    this.fileLog = fileLog;
    this.sessions = sessions;
  }

  public DesktopChatController create(DesktopChatController.Callbacks callbacks) {
    return new DesktopChatController(configService, modelSelection, prompts, fileLog, sessions, callbacks);
  }
}

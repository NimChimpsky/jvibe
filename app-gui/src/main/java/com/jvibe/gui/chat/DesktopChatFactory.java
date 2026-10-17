package com.jvibe.gui.chat;

import com.jvibe.config.ConfigService;
import com.jvibe.session.SessionFactory;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.nio.file.Path;

@Singleton
public final class DesktopChatFactory {
  private final ConfigService configService;
  private final ChatPromptRunner promptRunner;
  private final SessionFactory sessions;

  public DesktopChatFactory(
      @External ConfigService configService,
      ChatPromptRunner promptRunner,
      @External SessionFactory sessions) {
    this.configService = configService;
    this.promptRunner = promptRunner;
    this.sessions = sessions;
  }

  public DesktopChatController create(ChatCallbacks callbacks) {
    return new DesktopChatController(configService, promptRunner, sessions, callbacks, null, false);
  }

  public DesktopChatController createWorkspace(Path workspace, ChatCallbacks callbacks) {
    return new DesktopChatController(configService, promptRunner, sessions, callbacks, workspace, true);
  }

  public DesktopChatController createBuild(ChatCallbacks callbacks) {
    return new DesktopChatController(configService, promptRunner, sessions, callbacks, null, true);
  }
}

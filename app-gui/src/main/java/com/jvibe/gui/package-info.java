/** Swing desktop app. Core services are wired by the core module and injected here. */
@io.avaje.inject.InjectModule(name = "Gui", requires = {
    com.jvibe.agent.AgentRunner.class,
    com.jvibe.api.AccountService.class,
    com.jvibe.api.JvibeClient.class
})
package com.jvibe.gui;

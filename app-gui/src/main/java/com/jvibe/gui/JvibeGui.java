package com.jvibe.gui;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class JvibeGui extends Application {
  @Override
  public void start(Stage stage) throws Exception {
    new ConfigService(JvibePaths.userHome()).bootstrap();

    var sessions = new ListView<String>();
    sessions.getItems().add("Current workspace");

    var transcript = new TextArea();
    transcript.setEditable(false);
    transcript.setWrapText(true);

    var model = new ComboBox<String>();
    model.getItems().addAll("local/echo", "openai/gpt-5.2");
    model.getSelectionModel().selectFirst();

    var prompt = new TextField();
    prompt.setPromptText("Ask jvibe");
    var send = new Button("Send");
    send.setOnAction(event -> {
      if (!prompt.getText().isBlank()) {
        transcript.appendText("You: " + prompt.getText() + System.lineSeparator());
        transcript.appendText("jvibe: GUI shell is ready; core agent wiring continues in CLI MVP." + System.lineSeparator());
        prompt.clear();
      }
    });

    var composer = new HBox(8, model, prompt, send);
    composer.setPadding(new Insets(8));
    HBox.setHgrow(prompt, javafx.scene.layout.Priority.ALWAYS);

    var tools = new VBox(8, new Label("Tool activity"), new Label("Approvals and dynamic tools will appear here."));
    tools.setPadding(new Insets(8));
    tools.setPrefWidth(280);

    var root = new BorderPane();
    root.setLeft(sessions);
    root.setCenter(transcript);
    root.setRight(tools);
    root.setBottom(composer);

    stage.setTitle("jvibe");
    stage.setScene(new Scene(root, 1100, 720));
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}

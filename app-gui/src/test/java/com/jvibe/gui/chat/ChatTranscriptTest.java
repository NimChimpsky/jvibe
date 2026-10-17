package com.jvibe.gui.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Container;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChatTranscriptTest {
  @Test
  void referenceChipsFitAndWrapWithoutReducingTextHeight() throws Exception {
    var transcript = new ChatTranscript();
    SwingUtilities.invokeAndWait(() -> transcript.appendUser("Summarise", List.of(
        java.nio.file.Path.of("/tmp/a-long-file-name-to-summarise.md"),
        java.nio.file.Path.of("/tmp/another-file-name.txt"), java.nio.file.Path.of("/tmp/third.txt"))));
    layoutAtWidth(transcript, 240);
    SwingUtilities.invokeAndWait(() -> {
      var row = (JPanel) transcript.component().getComponent(0);
      var wrapper = (JPanel) row.getComponent(0);
      var references = (JPanel) wrapper.getComponent(0);
      for (var chip : references.getComponents()) {
        assertTrue(chip.getY() + chip.getHeight() <= references.getHeight());
        assertTrue(chip.getX() + chip.getWidth() <= references.getWidth());
      }
      assertTrue(((JTextArea) row.getClientProperty("body")).getHeight() > 0);
    });
  }

  @Test
  void shortTurnsStayCompactAndLongResponsesWrapWithoutClipping() throws Exception {
    var transcript = new ChatTranscript();
    SwingUtilities.invokeAndWait(() -> {
      transcript.appendUser("hi", List.of());
      transcript.appendAssistant("Hello.");
    });
    layoutAtWidth(transcript, 500);
    SwingUtilities.invokeAndWait(() -> {
      var panel = transcript.component();
      assertTrue(panel.getComponent(0).getHeight() < 50);
      assertTrue(panel.getComponent(1).getHeight() < 50);
      var user = (JPanel) panel.getComponent(0);
      var assistant = (JPanel) panel.getComponent(1);
      assertTrue(user.getComponent(0).getX() > assistant.getComponent(0).getX());
      transcript.appendAssistantDelta("This is a long response with wrapped words and paragraphs.\n\n".repeat(30));
      transcript.appendAssistantDelta("Final line is visible.");
      transcript.finishAssistant();
    });
    layoutAtWidth(transcript, 240);
    SwingUtilities.invokeAndWait(() -> {
      var panel = transcript.component();
      var row = (JComponent) panel.getComponent(2);
      var text = (JTextArea) row.getClientProperty("body");
      assertTrue(text.getWidth() < 240 - 40);
      assertTrue(row.getHeight() > 500);
      try {
        var last = text.modelToView2D(text.getDocument().getLength() - 1);
        assertNotNull(last);
        assertTrue(last.getMaxY() <= text.getHeight(), "Last line must fit in the actual text component");
      } catch (javax.swing.text.BadLocationException e) { throw new AssertionError(e); }
    });
  }

  private static void layoutAtWidth(ChatTranscript transcript, int width) throws Exception {
    SwingUtilities.invokeAndWait(() -> transcript.component().setSize(width, 100));
    SwingUtilities.invokeAndWait(() -> {
      var panel = transcript.component();
      panel.setSize(width, panel.getPreferredSize().height);
      layout(panel);
    });
    SwingUtilities.invokeAndWait(() -> layout(transcript.component()));
  }

  private static void layout(Container component) {
    component.doLayout();
    for (var child : component.getComponents()) if (child instanceof Container nested) layout(nested);
  }
}

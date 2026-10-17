package com.jvibe.gui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Function;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class MentionControllerTest {
  @Test
  void detectsTheMentionUnderTheCaret() {
    var input = new JTextArea("check @pro");
    var controller = new MentionController<String>(input, List::of, Function.identity(), Function.identity(), item -> {});

    input.setCaretPosition(input.getText().length());
    assertEquals("pro", controller.activePrefix());

    input.setText("check @prod now");
    input.setCaretPosition(input.getText().length());
    assertNull(controller.activePrefix());
  }
}

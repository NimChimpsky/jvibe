package com.jvibe.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.JvibePaths;
import com.jvibe.session.Message;
import com.jvibe.session.Mode;
import com.jvibe.tool.ToolCall;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JvibeClientTest {
  @TempDir Path home;
  private final FakeHttpTransport http = new FakeHttpTransport();
  private AccountService accounts;
  private JvibeClient client;

  @BeforeEach
  void setUp() {
    accounts = new AccountService(JvibePaths.under(home), "");
    client = new JvibeClient(http, accounts, "https://jvibe.test/api/v1/");
  }

  private ModeRequest request() {
    return new ModeRequest(Mode.BUILD, List.of(
        Message.user("inspect"),
        Message.assistant("Checking.", List.of(new ToolCall("call_1", "file_read", Map.of("path", "a")))),
        Message.tool("call_1", "{\"success\":true}")),
        Thinking.HIGH, "Be brief.", "Repository root: /work");
  }

  @Test
  void streamsToTheModeEndpointWithConversationOnly() {
    accounts.save(new Account("me@example.com", "jvibe_key", ""));
    http.streamLines = List.of(
        "data: {\"choices\":[{\"delta\":{\"content\":\"done\"}}]}",
        "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");

    var events = client.stream(request()).toList();

    assertEquals("https://jvibe.test/api/v1/modes/build", http.lastUri.toString());
    assertEquals("Bearer jvibe_key", http.lastHeaders.get("Authorization"));
    var body = JsonSupport.object(http.lastJson);
    assertEquals("high", body.get("thinking"));
    assertEquals("Be brief.", body.get("instructions"));
    assertEquals("Repository root: /work", body.get("context"));
    assertFalse(body.containsKey("tools"));
    assertFalse(body.containsKey("model"));
    var messages = JsonSupport.list(body.get("messages"));
    assertEquals(List.of("user", "assistant", "tool"),
        messages.stream().map(message -> JsonSupport.map(message).get("role")).toList());
    var call = JsonSupport.map(JsonSupport.list(JsonSupport.map(messages.get(1)).get("tool_calls")).get(0));
    assertEquals("{\"path\":\"a\"}", JsonSupport.map(call.get("function")).get("arguments"));
    assertEquals("call_1", JsonSupport.map(messages.get(2)).get("tool_call_id"));
    assertEquals(List.of(new StreamEvent.AssistantDelta("done"), new StreamEvent.Done()), events);
  }

  @Test
  void requiresSignInAndExplainsHttpErrors() {
    assertEquals(List.of(new StreamEvent.Error("Sign in from the JVibe tab to start.")), client.stream(request()).toList());

    accounts.save(new Account("", "jvibe_key", ""));
    http.streamStatusCode = 402;
    http.streamLines = List.of("{\"error\":{\"message\":\"Insufficient credits.\"}}");
    var error = assertInstanceOf(StreamEvent.Error.class, client.stream(request()).toList().get(0));
    assertTrue(error.message().contains("Out of JVibe credits"));

    http.streamStatusCode = 403;
    http.streamLines = List.of("{\"error\":{\"message\":\"Approve this sign-in (code K7Q-M2P).\"}}");
    assertEquals(List.of(new StreamEvent.Error("Approve this sign-in (code K7Q-M2P).")), client.stream(request()).toList());
  }

  @Test
  void signInStoresThePendingKeyAndStatusActivatesIt() {
    http.postResponse = new HttpTextResponse(200,
        "{\"email\":\"me@example.com\",\"api_key\":\"jvibe_new\",\"status\":\"pending\",\"approval_code\":\"K7Q-M2P\"}");

    var account = client.signIn(" me@example.com ", "me@laptop");

    assertEquals("https://jvibe.test/api/v1/device/signin", http.lastUri.toString());
    assertEquals(Map.of("email", "me@example.com", "device_name", "me@laptop"), JsonSupport.object(http.lastJson));
    assertEquals(new Account("me@example.com", "jvibe_new", "K7Q-M2P"), account);
    assertEquals(account, accounts.account());

    http.getResponse = new HttpTextResponse(403, "{\"error\":{\"message\":\"Approve code K7Q-M2P\"}}");
    var pending = client.status();
    assertEquals(AccountStatus.State.PENDING, pending.state());
    assertEquals("Approve code K7Q-M2P", pending.summary());

    http.getResponse = new HttpTextResponse(200, "{\"email\":\"me@example.com\",\"credits_balance\":2.5}");
    var active = client.status();
    assertEquals("Signed in as me@example.com · 2.50 credits", active.summary());
    assertEquals(new Account("me@example.com", "jvibe_new", ""), accounts.account());
  }

  @Test
  void signInFailureKeepsTheExistingAccount() {
    accounts.save(new Account("old@example.com", "jvibe_old", ""));
    http.postResponse = new HttpTextResponse(429, "{\"error\":\"Too many sign-in attempts.\"}");

    var error = assertThrows(IllegalStateException.class, () -> client.signIn("me@example.com", "laptop"));

    assertTrue(error.getMessage().contains("Too many sign-in attempts."));
    assertEquals("jvibe_old", accounts.apiKey());
  }
}

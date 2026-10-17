package com.jvibe.api;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;

/** Client for the JVibe service: per-tab mode streams, device sign-in and account status. */
@Singleton
public final class JvibeClient implements ModeClient {
  public static final String SITE_URL = "https://jvibe.net";
  public static final String ACCOUNT_URL = SITE_URL + "/account";
  /** Overrides the API base URL, e.g. {@code -Djvibe.apiUrl=http://localhost:3000/api/v1}. */
  static final String API_URL_PROPERTY = "jvibe.apiUrl";

  private final HttpTransport http;
  private final AccountService accounts;
  private final String apiUrl;

  @Inject
  public JvibeClient(HttpTransport http, AccountService accounts) {
    this(http, accounts, System.getProperty(API_URL_PROPERTY, SITE_URL + "/api/v1"));
  }

  JvibeClient(HttpTransport http, AccountService accounts, String apiUrl) {
    this.http = http;
    this.accounts = accounts;
    this.apiUrl = apiUrl.replaceAll("/+$", "");
  }

  @Override
  public Stream<StreamEvent> stream(ModeRequest request) {
    var key = accounts.apiKey();
    if (key.isBlank()) {
      return Stream.of(new StreamEvent.Error("Sign in from the JVibe tab to start."));
    }
    var response = http.postStream(
        uri("/modes/" + request.mode().id()),
        headers(key, "text/event-stream"),
        JsonSupport.toJson(MessageMapper.body(request)));
    return response.success()
        ? SseEventStream.parse(response.lines())
        : Stream.of(StreamErrors.httpError(response.statusCode(), response.lines()));
  }

  /**
   * Requests a key for this email and stores it. Outside local development the key stays
   * pending until the user approves the emailed code, which {@link #status()} detects.
   */
  public Account signIn(String email, String deviceName) {
    var response = http.post(
        uri("/device/signin"),
        Map.of("Content-Type", "application/json", "Accept", "application/json"),
        JsonSupport.toJson(Map.of("email", email.trim(), "device_name", deviceName)));
    if (!response.success()) {
      throw new IllegalStateException(StreamErrors.httpMessage(response.statusCode(), response.body()));
    }
    var body = JsonSupport.object(response.body());
    var pending = "pending".equals(JsonSupport.string(body.get("status")));
    var account = new Account(
        JsonSupport.string(body.get("email")),
        JsonSupport.string(body.get("api_key")),
        pending ? JsonSupport.string(body.get("approval_code")) : "");
    if (!account.hasApiKey()) {
      throw new IllegalStateException("JVibe did not return an API key.");
    }
    accounts.save(account);
    return account;
  }

  /** Checks the key with {@code GET /credits}; once it works, records the email and clears the approval code. */
  public AccountStatus status() {
    var key = accounts.apiKey();
    if (key.isBlank()) {
      return AccountStatus.of(AccountStatus.State.SIGNED_OUT, "Not signed in");
    }
    var response = http.get(uri("/credits"), headers(key, "application/json"));
    if (response.success()) {
      var body = JsonSupport.object(response.body());
      var email = JsonSupport.string(body.get("email"));
      var stored = accounts.account();
      var approved = new Account(email, key, "");
      if (stored.apiKey().equals(key) && !stored.equals(approved)) {
        accounts.save(approved);
      }
      var credits = body.get("credits_balance") instanceof Number number ? number.doubleValue() : 0;
      return new AccountStatus(AccountStatus.State.ACTIVE, email, credits, "");
    }
    var message = StreamErrors.httpMessage(response.statusCode(), response.body());
    return switch (response.statusCode()) {
      case 403 -> AccountStatus.of(AccountStatus.State.PENDING, message);
      case 401 -> AccountStatus.of(AccountStatus.State.INVALID, message);
      default -> AccountStatus.of(AccountStatus.State.UNAVAILABLE, message);
    };
  }

  private URI uri(String path) {
    return URI.create(apiUrl + path);
  }

  private static Map<String, String> headers(String key, String accept) {
    return Map.of(
        "Authorization", "Bearer " + key,
        "Content-Type", "application/json",
        "Accept", accept);
  }
}

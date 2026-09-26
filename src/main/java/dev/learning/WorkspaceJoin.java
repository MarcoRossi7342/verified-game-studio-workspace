package dev.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class WorkspaceJoin {
    private final InfraiClient client;

    public WorkspaceJoin(@Value("${infrai.base-url}") String baseUrl, ObjectMapper mapper) {
        this.client = new InfraiClient(baseUrl, System.getenv("INFRAI_API_KEY"), mapper);
    }

    public static void main(String[] args) {
        SpringApplication.run(WorkspaceJoin.class, args);
    }

    public record JoinRequest(String domain, String email, String workspaceId, String assetCollection,
                              String liveEventChannel, String moderationQueue) {}

    public record JoinResult(String workspaceId, String email, String userId,
                             String assetCollection, String liveEventChannel, String moderationQueue) {}

    static boolean eligible(String verifiedDomain, String email) {
        if (verifiedDomain == null || email == null) return false;
        String domain = verifiedDomain.toLowerCase(java.util.Locale.ROOT);
        return email.toLowerCase(java.util.Locale.ROOT).endsWith("@" + domain)
                && !domain.isBlank() && !domain.contains("@");
    }

    @PostMapping("/workspaces/join")
    public JoinResult join(@RequestBody JoinRequest request) throws Exception {
        if (request.domain() == null || request.domain().isBlank()
                || request.workspaceId() == null || request.workspaceId().isBlank()
                || request.assetCollection() == null || request.assetCollection().isBlank()
                || request.liveEventChannel() == null || request.liveEventChannel().isBlank()
                || request.moderationQueue() == null || request.moderationQueue().isBlank()
                || !eligible(request.domain(), request.email())) {
            throw new JoinRejected("Company email, domain and workspace are required");
        }

        String email = request.email().toLowerCase(java.util.Locale.ROOT);
        JsonNode user = client.getByEmail(email);
        return new JoinResult(request.workspaceId(), email, user.path("user_id").asText(),
                request.assetCollection(), request.liveEventChannel(), request.moderationQueue());
    }

    @ExceptionHandler(JoinRejected.class)
    ResponseEntity<Map<String, String>> rejected(JoinRejected error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(InfraiError.class)
    ResponseEntity<Map<String, String>> upstream(InfraiError error) {
        int status = error.status >= 400 && error.status < 500 ? error.status : 502;
        return ResponseEntity.status(status).body(Map.of("error", error.code));
    }

    static class JoinRejected extends RuntimeException {
        JoinRejected(String message) { super(message); }
    }

    static class InfraiError extends RuntimeException {
        final int status;
        final String code;
        InfraiError(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    static class InfraiClient {
        private final String baseUrl;
        private final String key;
        private final ObjectMapper mapper;
        private final HttpClient http = HttpClient.newHttpClient();

        InfraiClient(String baseUrl, String key, ObjectMapper mapper) {
            this.baseUrl = baseUrl.replaceAll("/+$", "");
            this.key = key;
            this.mapper = mapper;
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Set INFRAI_API_KEY");
        }

        JsonNode getByEmail(String email) throws Exception {
            return call("GET", "/v1/auth/user/get_by_email?email="
                    + URLEncoder.encode(email, StandardCharsets.UTF_8), null);
        }

        JsonNode call(String method, String path, Object body) throws Exception {
            String json = body == null ? "" : mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json)).build();
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = mapper.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long backoff = 1L << attempt;
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    try { backoff = Math.max(backoff, Long.parseLong(retryAfter)); }
                    catch (NumberFormatException ignored) { /* Exponential backoff remains in effect. */ }
                    Thread.sleep(Math.min(backoff, 30) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new InfraiError(response.statusCode(), error.path("code").asText("UPSTREAM_REJECTED"),
                            error.path("message").asText("Request rejected"));
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Upstream response: " + response.statusCode());
                return envelope.path("data");
            }
            throw new IllegalStateException("Retry limit reached");
        }
    }
}

package ch.tntzockt.shop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Spricht mit https://www.tnt-zockt.ch/api/mc. Alle Aufrufe sind asynchron. */
final class ApiClient {

    /** Fehler mit einer Meldung, die man dem Spieler zeigen kann. */
    static final class ApiException extends RuntimeException {
        ApiException(String message) { super(message); }
    }

    private static final Gson GSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String url;
    private final String secret;

    ApiClient(String url, String secret) {
        this.url = url;
        this.secret = secret;
    }

    boolean isConfigured() {
        return secret != null && secret.length() >= 24 && url != null && url.startsWith("https://");
    }

    CompletableFuture<JsonObject> call(Map<String, Object> body) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new ApiException("Der Shop ist noch nicht eingerichtet."));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-MC-Secret", secret)
                .header("User-Agent", "TNT-Shop-Plugin/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            JsonObject json;
            try {
                json = JsonParser.parseString(resp.body()).getAsJsonObject();
            } catch (Exception e) {
                throw new ApiException("Ungültige Antwort vom Server (" + resp.statusCode() + ").");
            }
            if (resp.statusCode() >= 400) {
                String msg = json.has("error") ? json.get("error").getAsString() : "Fehler " + resp.statusCode();
                throw new ApiException(msg);
            }
            return json;
        });
    }

    static String messageOf(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && !(c instanceof ApiException)) c = c.getCause();
        if (c instanceof ApiException) return c.getMessage();
        return "Shop gerade nicht erreichbar. Bitte später nochmals versuchen.";
    }
}

package ch.tntzockt.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Zeigt in einem Discord-Kanal (über einen Webhook) eine Spielerliste, die sich
 * automatisch aktualisiert, und meldet Beitritte.
 *
 * Kein Bot nötig: der Webhook erstellt EINE Nachricht und bearbeitet sie danach immer wieder.
 */
final class DiscordStatus implements Listener {

    private static final Gson GSON = new Gson();
    private static final Pattern WEBHOOK = Pattern.compile("^https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+$");

    private final TntShopPlugin plugin;
    private final RankManager ranks;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private File dataFile;
    private YamlConfiguration data;
    private String webhook = "";
    private boolean joinMessages, firstJoinOnly, leaveMessages;
    private String serverAddress = "";
    private int taskId = -1;
    private volatile boolean dirty = false;
    private volatile boolean busy = false;

    DiscordStatus(TntShopPlugin plugin, RankManager ranks) {
        this.plugin = plugin;
        this.ranks = ranks;
    }

    void load() {
        var c = plugin.getConfig();
        String url = String.valueOf(c.getString("discord.webhook-url")).trim();
        webhook = WEBHOOK.matcher(url).matches() ? url : "";
        if (!url.isEmpty() && !url.equals("null") && webhook.isEmpty()) {
            plugin.getLogger().warning("discord.webhook-url sieht nicht wie ein Discord-Webhook aus – Discord-Anzeige ist aus.");
        }
        joinMessages = c.getBoolean("discord.join-messages", true);
        firstJoinOnly = c.getBoolean("discord.first-join-only", false);
        leaveMessages = c.getBoolean("discord.leave-messages", false);
        serverAddress = String.valueOf(c.getString("discord.server-address", "stamina-elk.tun.ply.gg"));

        dataFile = new File(plugin.getDataFolder(), "discord.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        if (!enabled()) return;
        // Änderungen sammeln und höchstens alle 5 s an Discord schicken (Rate-Limit schonen)
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 40L, 100L).getTaskId();
        dirty = true;
        plugin.getLogger().info("Discord-Spielerliste aktiv");
    }

    boolean enabled() {
        return !webhook.isEmpty();
    }

    // ---------------------------------------------------------------- Events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled()) return;
        dirty = true;
        Player p = e.getPlayer();
        boolean first = !p.hasPlayedBefore();
        if (joinMessages && (first || !firstJoinOnly)) {
            String text = first
                    ? "🎉 **" + esc(p.getName()) + "** ist zum ersten Mal auf dem Server – willkommen!"
                    : "➡️ **" + esc(p.getName()) + "** ist dem Server beigetreten.";
            post(text);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (!enabled()) return;
        // Spieler ist während des Events noch in der Liste → einen Tick später zählen
        Bukkit.getScheduler().runTaskLater(plugin, () -> dirty = true, 1L);
        if (leaveMessages) post("⬅️ **" + esc(e.getPlayer().getName()) + "** hat den Server verlassen.");
    }

    // ---------------------------------------------------------------- Liste

    private static String esc(String s) {
        return s.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`");
    }

    private JsonObject buildEmbed(boolean online) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int max = Bukkit.getMaxPlayers();

        StringBuilder list = new StringBuilder();
        if (!online) {
            list.append("Der Server ist gerade offline.");
        } else if (players.isEmpty()) {
            list.append("Gerade ist niemand online.");
        } else {
            for (Player p : players) {
                RankManager.Rank r = ranks.rankOf(p.getUniqueId());
                list.append("• ");
                if (r != null) list.append("`[").append(r.display()).append("]` ");
                list.append("**").append(esc(p.getName())).append("**");
                if (p.isOp()) list.append(" 🛡️");
                list.append('\n');
                if (list.length() > 3800) { list.append("…"); break; }
            }
        }

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🎮 TNT-Zockt Minecraft – Spieler");
        embed.addProperty("description", list.toString());
        embed.addProperty("color", online ? (players.isEmpty() ? 0x7f8c8d : 0x2ecc71) : 0xe74c3c);
        JsonArray fields = new JsonArray();
        fields.add(field("Status", online ? "🟢 Online" : "🔴 Offline", true));
        fields.add(field("Spieler", online ? players.size() + " / " + max : "–", true));
        fields.add(field("Adresse", "`" + serverAddress + "`", false));
        embed.add("fields", fields);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Java Edition 26.3 · aktualisiert sich automatisch");
        embed.add("footer", footer);
        embed.addProperty("timestamp", Instant.now().toString());
        return embed;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    private String payload(JsonObject embed) {
        JsonObject body = new JsonObject();
        body.addProperty("username", "TNT-Zockt Minecraft");
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray()); // nie @everyone o.ä. pingen
        body.add("allowed_mentions", mentions);
        return GSON.toJson(body);
    }

    /** Läuft im Haupt-Thread: Daten einsammeln, senden asynchron. */
    private void flush() {
        if (!dirty || busy) return;
        dirty = false;
        busy = true;
        String body = payload(buildEmbed(true));
        String id = data.getString("status-message-id", "");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sendStatus(body, id);
            } finally {
                busy = false;
            }
        });
    }

    private void sendStatus(String body, String messageId) {
        try {
            if (messageId != null && !messageId.isEmpty()) {
                HttpResponse<String> r = http.send(req(webhook + "/messages/" + messageId, "PATCH", body), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() / 100 == 2) return;
                if (r.statusCode() == 429) { dirty = true; return; }
                if (r.statusCode() != 404) {
                    plugin.getLogger().warning("Discord-Liste: Fehler " + r.statusCode());
                    return;
                }
                // Nachricht wurde gelöscht → neu erstellen
            }
            HttpResponse<String> r = http.send(req(webhook + "?wait=true", "POST", body), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) {
                String newId = JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    data.set("status-message-id", newId);
                    try { data.save(dataFile); } catch (IOException ignored) { }
                });
            } else if (r.statusCode() == 429) {
                dirty = true;
            } else {
                plugin.getLogger().warning("Discord-Liste: Fehler " + r.statusCode() + " beim Erstellen");
            }
        } catch (Exception ex) {
            dirty = true;
            plugin.getLogger().warning("Discord nicht erreichbar: " + ex.getMessage());
        }
    }

    private void post(String text) {
        JsonObject body = new JsonObject();
        body.addProperty("username", "TNT-Zockt Minecraft");
        body.addProperty("content", text);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);
        String json = GSON.toJson(body);
        http.sendAsync(req(webhook, "POST", json), HttpResponse.BodyHandlers.discarding())
            .exceptionally(ex -> null);
    }

    private HttpRequest req(String url, String method, String json) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "TNT-Shop-Plugin/1.2")
                .method(method, HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    /** Beim Herunterfahren: Liste auf "offline" setzen (kurz, blockierend). */
    void shutdown() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        if (!enabled()) return;
        String id = data.getString("status-message-id", "");
        if (id == null || id.isEmpty()) return;
        try {
            http.send(req(webhook + "/messages/" + id, "PATCH", payload(buildEmbed(false))), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { }
    }

}

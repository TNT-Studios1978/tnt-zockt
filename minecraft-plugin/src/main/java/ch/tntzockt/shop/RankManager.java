package ch.tntzockt.shop;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Kosmetische Ränge (Supporter / VIP / Legende), gekauft über Tebex.
 * Nur Aussehen: Chat-Präfix, Namensfarbe, Partikel. Kein Spielvorteil.
 *
 * Tebex führt bei einem Kauf z.B. "tntrang give {id} vip" aus der Konsole aus.
 */
final class RankManager implements Listener, CommandExecutor, TabCompleter {

    /** Ein Rang aus der config.yml. Höherer index = höherer Rang. */
    record Rank(String id, int index, String display, TextColor color, List<Particle> particles, Material icon) {}

    private static final class ParticleMenu implements InventoryHolder {
        private Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private static final Pattern UUID_NO_DASH = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final TntShopPlugin plugin;
    private final NamespacedKey particleKey;
    private final NamespacedKey menuKey;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    /** UUID -> Rang-ID; wird auch im Chat-Thread gelesen. */
    private final Map<UUID, String> playerRanks = new ConcurrentHashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private int taskId = -1;

    RankManager(TntShopPlugin plugin) {
        this.plugin = plugin;
        this.particleKey = new NamespacedKey(plugin, "particle");
        this.menuKey = new NamespacedKey(plugin, "menu_particle");
    }

    // ---------------------------------------------------------------- Laden

    void load() {
        ranks.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("ranks");
        int i = 0;
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(id);
                if (r == null) continue;
                TextColor color = parseColor(r.getString("color", "white"));
                List<Particle> parts = new ArrayList<>();
                for (String p : r.getStringList("particles")) {
                    Particle particle = parseParticle(p);
                    if (particle != null) parts.add(particle);
                    else plugin.getLogger().warning("Partikel unbekannt oder nicht nutzbar: " + p);
                }
                Material icon = Material.matchMaterial(r.getString("icon", "NAME_TAG"));
                ranks.put(id.toLowerCase(Locale.ROOT), new Rank(id.toLowerCase(Locale.ROOT), i++,
                        r.getString("display", id), color, parts, icon == null ? Material.NAME_TAG : icon));
            }
        }
        dataFile = new File(plugin.getDataFolder(), "ranks.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        playerRanks.clear();
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    playerRanks.put(UUID.fromString(key), players.getString(key + ".rank", ""));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) applyNames(p);
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 20L, 5L).getTaskId();
    }

    private static TextColor parseColor(String s) {
        s = s.trim();
        if (s.startsWith("#")) {
            TextColor c = TextColor.fromHexString(s);
            if (c != null) return c;
        }
        NamedTextColor n = NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
        return n != null ? n : NamedTextColor.WHITE;
    }

    /** Nur Partikel ohne Zusatzdaten (z.B. keine Farbe nötig). */
    private static Particle parseParticle(String s) {
        try {
            Particle p = Particle.valueOf(s.trim().toUpperCase(Locale.ROOT));
            return p.getDataType() == Void.class ? p : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("ranks.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    Rank rankOf(UUID id) {
        String r = playerRanks.get(id);
        return r == null ? null : ranks.get(r);
    }

    // ---------------------------------------------------------------- Anzeige

    private Component rankTag(Rank r) {
        return Component.text("[" + r.display() + "] ", r.color(), TextDecoration.BOLD);
    }

    private void applyNames(Player p) {
        Rank r = rankOf(p.getUniqueId());
        if (r == null) {
            p.displayName(null);
            p.playerListName(null);
            return;
        }
        Component name = Component.text(p.getName(), r.color());
        p.displayName(name);
        p.playerListName(rankTag(r).append(name.decoration(TextDecoration.BOLD, false)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Rank r = rankOf(e.getPlayer().getUniqueId());
        if (r == null) return;
        Component tag = rankTag(r);
        TextColor color = r.color();
        e.renderer((source, displayName, message, viewer) -> Component.empty()
                .append(tag)
                .append(Component.text(source.getName(), color))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        applyNames(e.getPlayer());
    }

    // ---------------------------------------------------------------- Partikel

    private List<Particle> allowedParticles(Rank r) {
        List<Particle> list = new ArrayList<>();
        if (r == null) return list;
        for (Rank other : ranks.values()) {
            if (other.index() <= r.index()) {
                for (Particle p : other.particles()) if (!list.contains(p)) list.add(p);
            }
        }
        return list;
    }

    private int tick = 0;

    private void tickParticles() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String sel = p.getPersistentDataContainer().get(particleKey, PersistentDataType.STRING);
            if (sel == null) continue;
            if (p.getGameMode() == GameMode.SPECTATOR || p.isInvisible()) continue;
            Particle particle = parseParticle(sel);
            if (particle == null || !allowedParticles(rankOf(p.getUniqueId())).contains(particle)) continue;
            double angle = (tick % 16) * (Math.PI / 8);
            Location loc = p.getLocation().add(Math.cos(angle) * 0.6, 2.2, Math.sin(angle) * 0.6);
            p.getWorld().spawnParticle(particle, loc, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static String niceParticle(Particle p) {
        return switch (p.name()) {
            case "HEART" -> "Herzen";
            case "NOTE" -> "Musiknoten";
            case "HAPPY_VILLAGER" -> "Grüne Funken";
            case "END_ROD" -> "Lichtpunkte";
            case "ENCHANT" -> "Zauberschrift";
            case "FLAME" -> "Flammen";
            case "SOUL_FIRE_FLAME" -> "Seelenflammen";
            case "TOTEM_OF_UNDYING" -> "Totem-Glanz";
            case "WITCH" -> "Hexenzauber";
            case "ELECTRIC_SPARK" -> "Blitzfunken";
            default -> p.name();
        };
    }

    private void openParticleMenu(Player p) {
        Rank r = rankOf(p.getUniqueId());
        List<Particle> allowed = allowedParticles(r);
        if (allowed.isEmpty()) {
            p.sendMessage(Component.text("Partikel gibt es mit einem Supporter-Rang. ", NamedTextColor.YELLOW)
                    .append(storeLink()));
            return;
        }
        int size = Math.min(54, ((allowed.size() + 1 + 8) / 9) * 9);
        ParticleMenu holder = new ParticleMenu();
        Inventory inv = Bukkit.createInventory(holder, size, Component.text("Partikel wählen", NamedTextColor.DARK_PURPLE));
        holder.inventory = inv;
        String current = p.getPersistentDataContainer().get(particleKey, PersistentDataType.STRING);
        int slot = 0;
        for (Particle particle : allowed) {
            ItemStack item = new ItemStack(particle.name().equals(current) ? Material.LIME_DYE : Material.BLAZE_POWDER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(niceParticle(particle), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(particle.name().equals(current) ? "✔ Aktiv" : "▶ Klicken zum Aktivieren",
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, particle.name());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        ItemStack off = new ItemStack(Material.BARRIER);
        ItemMeta om = off.getItemMeta();
        om.displayName(Component.text("Partikel aus", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        om.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, "OFF");
        off.setItemMeta(om);
        inv.setItem(size - 1, off);
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ParticleMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String sel = it.getItemMeta().getPersistentDataContainer().get(menuKey, PersistentDataType.STRING);
        if (sel == null) return;
        if (sel.equals("OFF")) {
            p.getPersistentDataContainer().remove(particleKey);
            p.sendMessage(Component.text("Partikel ausgeschaltet.", NamedTextColor.GRAY));
        } else {
            Particle particle = parseParticle(sel);
            if (particle == null || !allowedParticles(rankOf(p.getUniqueId())).contains(particle)) return;
            p.getPersistentDataContainer().set(particleKey, PersistentDataType.STRING, particle.name());
            p.sendMessage(Component.text("Partikel aktiv: " + niceParticle(particle), NamedTextColor.LIGHT_PURPLE));
        }
        p.closeInventory();
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ParticleMenu) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- Befehle

    Component storeLink() {
        String url = String.valueOf(plugin.getConfig().getString("store-url")).trim();
        if (url.equals("null")) url = "";
        if (url.isBlank()) return Component.text("(Shop folgt bald)", NamedTextColor.GRAY);
        return Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(url));
    }

    private void cmdStore(CommandSender s) {
        s.sendMessage(Component.text("✦ TNT-Zockt Store ✦ ", NamedTextColor.GOLD, TextDecoration.BOLD));
        s.sendMessage(Component.text("Unterstütze den Server mit einem Rang (nur Aussehen, kein Spielvorteil):", NamedTextColor.WHITE));
        for (Rank r : ranks.values()) {
            s.sendMessage(Component.text(" • ", NamedTextColor.GRAY).append(rankTag(r).decoration(TextDecoration.BOLD, false)));
        }
        s.sendMessage(Component.text("➜ ", NamedTextColor.GRAY).append(storeLink()));
    }

    /** Spieler aus UUID (mit/ohne Bindestriche) oder Name ermitteln. */
    private OfflinePlayer resolve(String arg) {
        String a = arg.trim();
        if (UUID_NO_DASH.matcher(a).matches()) {
            a = a.substring(0, 8) + "-" + a.substring(8, 12) + "-" + a.substring(12, 16) + "-" + a.substring(16, 20) + "-" + a.substring(20);
        }
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(a));
        } catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(a);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(a);
    }

    private void setRank(OfflinePlayer op, Rank r) {
        String key = "players." + op.getUniqueId();
        if (r == null) {
            playerRanks.remove(op.getUniqueId());
            data.set(key, null);
        } else {
            playerRanks.put(op.getUniqueId(), r.id());
            data.set(key + ".rank", r.id());
            data.set(key + ".name", op.getName());
            data.set(key + ".since", java.time.Instant.now().toString());
        }
        save();
        Player online = op.getPlayer();
        if (online != null) {
            applyNames(online);
            if (r != null) {
                online.sendMessage(Component.text("Danke für deine Unterstützung! Du bist jetzt ", NamedTextColor.GREEN)
                        .append(rankTag(r))
                        .append(Component.text("– Partikel wählst du mit ", NamedTextColor.GREEN))
                        .append(Component.text("/partikel", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/partikel"))));
            } else {
                online.getPersistentDataContainer().remove(particleKey);
            }
        }
    }

    private boolean cmdAdmin(CommandSender s, String[] args) {
        if (args.length == 0) {
            s.sendMessage("/tntrang give <spieler|uuid> <rang>  – Rang geben (nur höher, nie tiefer)");
            s.sendMessage("/tntrang set <spieler|uuid> <rang>   – Rang genau setzen");
            s.sendMessage("/tntrang remove <spieler|uuid>       – Rang entfernen (z.B. Rückbuchung)");
            s.sendMessage("/tntrang info <spieler|uuid>");
            s.sendMessage("Ränge: " + String.join(", ", ranks.keySet()));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2) return false;
        OfflinePlayer op = resolve(args[1]);
        if (op == null) {
            s.sendMessage(Component.text("Spieler nicht gefunden: " + args[1], NamedTextColor.RED));
            return true;
        }
        String who = op.getName() != null ? op.getName() : op.getUniqueId().toString();
        switch (sub) {
            case "give", "set" -> {
                if (args.length < 3) return false;
                Rank r = ranks.get(args[2].toLowerCase(Locale.ROOT));
                if (r == null) {
                    s.sendMessage(Component.text("Unbekannter Rang. Möglich: " + String.join(", ", ranks.keySet()), NamedTextColor.RED));
                    return true;
                }
                Rank cur = rankOf(op.getUniqueId());
                if (sub.equals("give") && cur != null && cur.index() >= r.index()) {
                    s.sendMessage(who + " hat bereits " + cur.display() + " – nichts geändert.");
                    return true;
                }
                setRank(op, r);
                plugin.getLogger().info("Rang " + r.id() + " an " + who + " vergeben");
                s.sendMessage(Component.text("✔ " + who + " ist jetzt " + r.display(), NamedTextColor.GREEN));
            }
            case "remove" -> {
                setRank(op, null);
                plugin.getLogger().info("Rang von " + who + " entfernt");
                s.sendMessage(Component.text("✔ Rang von " + who + " entfernt", NamedTextColor.YELLOW));
            }
            case "info" -> {
                Rank cur = rankOf(op.getUniqueId());
                s.sendMessage(who + ": " + (cur == null ? "kein Rang" : cur.display()));
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "store" -> cmdStore(sender);
            case "partikel" -> {
                if (sender instanceof Player p) openParticleMenu(p);
                else sender.sendMessage("Nur im Spiel verfügbar.");
            }
            case "tntrang" -> { return cmdAdmin(sender, args); }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("tntrang")) return List.of();
        if (args.length == 1) return List.of("give", "set", "remove", "info");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3) return new ArrayList<>(ranks.keySet());
        return List.of();
    }
}

package ch.tntzockt.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class TntShopPlugin extends JavaPlugin implements Listener {

    private ApiClient api;
    private RankManager ranks;
    private DiscordStatus discord;
    private PlotManager plots;
    private WorldManager worlds;
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private NamespacedKey starterKey;
    private NamespacedKey kitKey;

    /** Kennzeichnet das Shop-Menü. */
    private static final class ShopHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        starterKey = new NamespacedKey(this, "starter_given");
        kitKey = new NamespacedKey(this, "kit_id");
        loadApi();
        getServer().getPluginManager().registerEvents(this, this);
        ranks = new RankManager(this);
        ranks.load();
        getServer().getPluginManager().registerEvents(ranks, this);
        discord = new DiscordStatus(this, ranks);
        discord.load();
        getServer().getPluginManager().registerEvents(discord, this);
        plots = new PlotManager(this);
        SkyblockManager sky = new SkyblockManager(this);
        worlds = new WorldManager(this, plots, sky);
        Minigames games = new Minigames(this);
        worlds.setMinigames(games);
        worlds.load();
        getServer().getPluginManager().registerEvents(games, this);
        var gameCmd = getCommand("spiele");
        if (gameCmd != null) { gameCmd.setExecutor(games); gameCmd.setTabCompleter(games); }
        getServer().getPluginManager().registerEvents(worlds, this);
        getServer().getPluginManager().registerEvents(plots, this);
        getServer().getPluginManager().registerEvents(sky, this);
        var isCmd = getCommand("is");
        if (isCmd != null) { isCmd.setExecutor(sky); isCmd.setTabCompleter(sky); }
        for (String c : new String[]{"welten", "lobby", "welt", "event"}) {
            var cmd = getCommand(c);
            if (cmd != null) { cmd.setExecutor(worlds); cmd.setTabCompleter(worlds); }
        }
        var plotCmd = getCommand("plot");
        if (plotCmd != null) { plotCmd.setExecutor(plots); plotCmd.setTabCompleter(plots); }
        for (String c : new String[]{"store", "partikel", "tntrang"}) {
            var cmd = getCommand(c);
            if (cmd != null) { cmd.setExecutor(ranks); cmd.setTabCompleter(ranks); }
        }
        getLogger().info("TNT-Shop aktiv" + (api.isConfigured() ? " (Verbindung eingerichtet)" : " - ACHTUNG: server-secret in config.yml fehlt!"));
    }

    @Override
    public void onDisable() {
        if (discord != null) discord.shutdown();
    }

    private void loadApi() {
        reloadConfig();
        // Ältere config.yml um neue Abschnitte (Ränge, Store) ergänzen – bestehende Werte bleiben
        if (!getConfig().isSet("ranks") || !getConfig().isSet("store-url") || !getConfig().isSet("discord") || !getConfig().isSet("worlds")) {
            getConfig().options().copyDefaults(true);
            saveConfig();
        }
        api = new ApiClient(getConfig().getString("api-url", ""), getConfig().getString("server-secret", "").trim());
    }

    // ---------------------------------------------------------------- Hilfen

    private static Component prefix() {
        return Component.text("[TNT-Shop] ", NamedTextColor.GOLD);
    }

    private void msg(Player p, Component c) {
        p.sendMessage(prefix().append(c));
    }

    private void error(Player p, String text) {
        msg(p, Component.text(text, NamedTextColor.RED));
    }

    /** Führt ein Ergebnis wieder im Haupt-Thread aus, falls der Spieler noch online ist. */
    private void onMain(UUID id, Consumer<Player> action) {
        Bukkit.getScheduler().runTask(this, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) action.accept(p);
        });
    }

    private static String nice(String material) {
        String[] parts = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (s.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }

    private static String fmt(long n) {
        return String.format("%,d", n).replace(',', '\'');
    }

    /** Gibt Items; was nicht ins Inventar passt, fällt vor die Füsse. */
    private void giveItems(Player p, List<ItemStack> items) {
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(items.toArray(new ItemStack[0]));
        for (ItemStack rest : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        if (!leftover.isEmpty()) {
            msg(p, Component.text("Inventar voll – ein Teil liegt vor deinen Füssen.", NamedTextColor.YELLOW));
        }
    }

    private List<ItemStack> itemsFromJson(JsonArray arr) {
        List<ItemStack> list = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            Material m = Material.matchMaterial(o.get("material").getAsString());
            int amount = o.get("amount").getAsInt();
            if (m == null || !m.isItem()) {
                getLogger().warning("Unbekanntes Material im Paket: " + o.get("material").getAsString());
                continue;
            }
            list.add(new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize() * 36))));
        }
        return list;
    }

    // ---------------------------------------------------------------- Befehle

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("tntshop")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                loadApi();
                ranks.load();
                discord.load();
                sender.sendMessage(prefix().append(Component.text("Konfiguration neu geladen. Verbindung: "
                        + (api.isConfigured() ? "eingerichtet" : "server-secret fehlt"), NamedTextColor.GREEN)));
                return true;
            }
            return false;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur im Spiel verfügbar.");
            return true;
        }
        switch (name) {
            case "link" -> cmdLink(p);
            case "punkte" -> cmdPoints(p);
            case "shop" -> cmdShop(p);
            default -> { return false; }
        }
        return true;
    }

    private void cmdLink(Player p) {
        UUID id = p.getUniqueId();
        msg(p, Component.text("Code wird erstellt…", NamedTextColor.GRAY));
        api.call(Map.of("action", "link_code", "uuid", id.toString(), "name", p.getName()))
           .whenComplete((json, err) -> onMain(id, pl -> {
               if (err != null) { error(pl, ApiClient.messageOf(err)); return; }
               String code = json.get("code").getAsString();
               String page = getConfig().getString("link-page", "https://www.tnt-zockt.ch/minecraft.html");
               msg(pl, Component.text("Dein Code: ", NamedTextColor.WHITE)
                       .append(Component.text(code, NamedTextColor.AQUA, TextDecoration.BOLD)
                               .clickEvent(ClickEvent.copyToClipboard(code)))
                       .append(Component.text(" (10 Minuten gültig)", NamedTextColor.GRAY)));
               msg(pl, Component.text("Gib ihn hier ein (mit Twitch anmelden): ", NamedTextColor.WHITE)
                       .append(Component.text(page, NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                               .clickEvent(ClickEvent.openUrl(page))));
           }));
    }

    private void cmdPoints(Player p) {
        UUID id = p.getUniqueId();
        api.call(Map.of("action", "status", "uuid", id.toString()))
           .whenComplete((json, err) -> onMain(id, pl -> {
               if (err != null) { error(pl, ApiClient.messageOf(err)); return; }
               if (!json.get("linked").getAsBoolean()) {
                   msg(pl, Component.text("Noch nicht verknüpft. Tippe ", NamedTextColor.WHITE)
                           .append(Component.text("/link", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/link"))));
                   return;
               }
               msg(pl, Component.text("Twitch ", NamedTextColor.WHITE)
                       .append(Component.text(json.get("twitch_login").getAsString(), NamedTextColor.LIGHT_PURPLE))
                       .append(Component.text(": ⭐ " + fmt(json.get("points").getAsLong()) + " Kanalpunkte", NamedTextColor.GOLD)));
           }));
    }

    private void cmdShop(Player p) {
        UUID id = p.getUniqueId();
        api.call(Map.of("action", "status", "uuid", id.toString()))
           .thenCompose(status -> api.call(Map.of("action", "kits")).thenApply(kits -> new JsonObject[]{status, kits}))
           .whenComplete((res, err) -> onMain(id, pl -> {
               if (err != null) { error(pl, ApiClient.messageOf(err)); return; }
               openShop(pl, res[0], res[1].getAsJsonArray("kits"));
           }));
    }

    // ---------------------------------------------------------------- Menü

    private void openShop(Player p, JsonObject status, JsonArray kits) {
        boolean linked = status.get("linked").getAsBoolean();
        long points = linked ? status.get("points").getAsLong() : 0;
        int size = Math.max(9, Math.min(54, ((kits.size() + 1 + 8) / 9) * 9));
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("TNT-Shop · ⭐ " + (linked ? fmt(points) : "nicht verknüpft"), NamedTextColor.DARK_RED));
        holder.inventory = inv;

        int slot = 0;
        for (JsonElement el : kits) {
            if (slot >= size - 1) break;
            JsonObject k = el.getAsJsonObject();
            Material icon = Material.matchMaterial(k.get("icon").getAsString());
            if (icon == null || !icon.isItem()) icon = Material.CHEST;
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            int price = k.get("price_points").getAsInt();
            meta.displayName(Component.text(k.get("name").getAsString(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(price > 0 ? "⭐ " + fmt(price) + " Kanalpunkte" : "Gratis",
                    linked && points >= price ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            if (k.has("description") && !k.get("description").isJsonNull()) {
                lore.add(Component.text(k.get("description").getAsString(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            for (JsonElement it : k.getAsJsonArray("items")) {
                JsonObject o = it.getAsJsonObject();
                lore.add(Component.text("• " + o.get("amount").getAsInt() + "× " + nice(o.get("material").getAsString()), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            if (k.get("once_only").getAsBoolean()) {
                lore.add(Component.text("Nur einmal pro Spieler", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            } else if (k.get("cooldown_hours").getAsInt() > 0) {
                lore.add(Component.text("Wieder verfügbar nach " + k.get("cooldown_hours").getAsInt() + " h", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text(linked ? "▶ Klicken zum Kaufen" : "▶ Zuerst /link", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, k.get("id").getAsString());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        // Info-Feld unten rechts
        ItemStack info = new ItemStack(linked ? Material.EMERALD : Material.NAME_TAG);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text(linked ? "Verknüpft mit " + status.get("twitch_login").getAsString() : "Konto verknüpfen",
                NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(Component.text(linked ? "Kanalpunkte sammelst du im Stream." : "Klicken: /link", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        info.setItemMeta(im);
        inv.setItem(size - 1, info);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ShopHolder)) return;
        e.setCancelled(true); // nichts aus dem Menü nehmen
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof ShopHolder)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (e.getRawSlot() == e.getInventory().getSize() - 1) {
            p.closeInventory();
            p.performCommand("link");
            return;
        }
        String kitId = clicked.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        if (kitId == null) return;

        long now = System.currentTimeMillis();
        long wait = getConfig().getLong("click-cooldown-seconds", 3) * 1000L;
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < wait) return;
        lastClick.put(p.getUniqueId(), now);

        p.closeInventory();
        if (worlds != null && !worlds.isSurvivalGroup(p.getWorld())) {
            error(p, "Pakete gibt es nur in Survival, Abenteuer und Farmwelt. Reise mit /welten dorthin.");
            return;
        }
        buy(p, kitId);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ShopHolder) e.setCancelled(true);
    }

    private void buy(Player p, String kitId) {
        UUID id = p.getUniqueId();
        msg(p, Component.text("Kauf wird ausgeführt…", NamedTextColor.GRAY));
        api.call(Map.of("action", "buy", "uuid", id.toString(), "kit_id", kitId))
           .whenComplete((json, err) -> onMain(id, pl -> {
               if (err != null) { error(pl, ApiClient.messageOf(err)); return; }
               giveItems(pl, itemsFromJson(json.getAsJsonArray("items")));
               Component c = Component.text("✔ " + json.get("name").getAsString() + " erhalten!", NamedTextColor.GREEN);
               if (json.has("points") && !json.get("points").isJsonNull()) {
                   c = c.append(Component.text("  Rest: ⭐ " + fmt(json.get("points").getAsLong()), NamedTextColor.GOLD));
               }
               msg(pl, c);
               getLogger().info(pl.getName() + " hat " + json.get("name").getAsString() + " gekauft (Kauf #" + json.get("purchase_id").getAsLong() + ")");
           }));
    }

    // ---------------------------------------------------------------- Starterpaket

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!getConfig().getBoolean("starter-kit.enabled", true)) return;
        var pdc = p.getPersistentDataContainer();
        if (pdc.has(starterKey, PersistentDataType.BYTE)) return;
        if (p.hasPlayedBefore()) {
            // Spieler von vor der Installation: kein Starterpaket, aber markieren
            pdc.set(starterKey, PersistentDataType.BYTE, (byte) 1);
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (String line : getConfig().getStringList("starter-kit.items")) {
            String[] parts = line.split(":");
            Material m = Material.matchMaterial(parts[0].trim());
            int amount = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            if (m != null && m.isItem()) items.add(new ItemStack(m, amount));
        }
        pdc.set(starterKey, PersistentDataType.BYTE, (byte) 1);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            giveItems(p, items);
            msg(p, Component.text("Willkommen auf TNT-Zockt! Hier ist dein Starterpaket. ", NamedTextColor.GREEN)
                    .append(Component.text("Mehr Pakete: /shop", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/shop"))));
        }, 40L);
    }
}

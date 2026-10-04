package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Mehrere Welten: Lobby, Survival (bestehend), Farmwelt, Grundstücke, Kreativ.
 * Survival-Welten und Kreativ-Welten haben getrennte Inventare, damit keine
 * Kreativ-Items in die Survival-Welt gelangen.
 */
final class WorldManager implements Listener, CommandExecutor, TabCompleter {

    enum Group { SURVIVAL, CREATIVE }

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private final TntShopPlugin plugin;
    private final PlotManager plots;
    private final NamespacedKey groupKey, menuKey;
    private String lobby = "lobby", survival = "world", farm = "farmwelt", plotWorld = "grundstuecke", creative = "kreativ";
    private boolean joinToLobby = true;
    private int lobbyProtectRadius = 30, creativeSpawnProtect = 16;

    WorldManager(TntShopPlugin plugin, PlotManager plots) {
        this.plugin = plugin;
        this.plots = plots;
        this.groupKey = new NamespacedKey(plugin, "inv_group");
        this.menuKey = new NamespacedKey(plugin, "menu_world");
    }

    // ---------------------------------------------------------------- Laden

    void load() {
        var c = plugin.getConfig();
        lobby = c.getString("worlds.lobby", lobby);
        survival = c.getString("worlds.survival", survival);
        farm = c.getString("worlds.farm", farm);
        plotWorld = c.getString("worlds.plots", plotWorld);
        creative = c.getString("worlds.creative", creative);
        joinToLobby = c.getBoolean("worlds.join-to-lobby", true);
        plots.configure(plotWorld, c.getInt("worlds.plot-size", 64), c.getInt("worlds.plot-road", 7), c.getInt("worlds.plots-per-player", 1));
        plots.load();

        World l = create(lobby, new Generators.Void());
        World f = createNormal(farm);
        World p = create(plotWorld, plots.generator());
        World k = create(creative, new Generators.Flat());

        if (l != null) {
            setupCalm(l);
            set(l, GameRule.FALL_DAMAGE, false);
            set(l, GameRule.PVP, false);
            l.setDifficulty(Difficulty.PEACEFUL);
            buildLobby(l);
        }
        for (World w : new World[]{p, k}) {
            if (w == null) continue;
            setupCalm(w);
            set(w, GameRule.TNT_EXPLODES, false);
            set(w, GameRule.DO_FIRE_TICK, false);
            set(w, GameRule.MOB_GRIEFING, false);
            set(w, GameRule.KEEP_INVENTORY, true);
            set(w, GameRule.PVP, false);
            set(w, GameRule.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
            w.setDifficulty(Difficulty.PEACEFUL);
        }
        if (f != null) f.setDifficulty(Difficulty.NORMAL);
    }

    private World create(String name, org.bukkit.generator.ChunkGenerator gen) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        try {
            w = new WorldCreator(name).generator(gen).generateStructures(false).createWorld();
            if (w != null) plugin.getLogger().info("Welt geladen: " + name);
            return w;
        } catch (Exception e) {
            plugin.getLogger().severe("Welt " + name + " konnte nicht erstellt werden: " + e.getMessage());
            return null;
        }
    }

    private World createNormal(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        try {
            w = new WorldCreator(name).environment(World.Environment.NORMAL).createWorld();
            if (w != null) plugin.getLogger().info("Welt geladen: " + name);
            return w;
        } catch (Exception e) {
            plugin.getLogger().severe("Welt " + name + " konnte nicht erstellt werden: " + e.getMessage());
            return null;
        }
    }

    private static <T> void set(World w, GameRule<T> rule, T value) {
        try { w.setGameRule(rule, value); } catch (Exception ignored) { }
    }

    @SuppressWarnings("deprecation")
    private static void setupCalm(World w) {
        set(w, GameRule.DO_DAYLIGHT_CYCLE, false);
        set(w, GameRule.DO_WEATHER_CYCLE, false);
        set(w, GameRule.DO_MOB_SPAWNING, false);
        set(w, GameRule.SPAWN_MONSTERS, false);
        set(w, GameRule.DO_PATROL_SPAWNING, false);
        set(w, GameRule.DO_TRADER_SPAWNING, false);
        set(w, GameRule.DO_INSOMNIA, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
    }

    /** Lobby-Plattform einmalig bauen. */
    private void buildLobby(World w) {
        NamespacedKey built = new NamespacedKey(plugin, "lobby_built");
        if (w.getPersistentDataContainer().has(built, PersistentDataType.BYTE)) return;
        int y = Generators.SURFACE, r = 15;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean edge = Math.abs(x) == r || Math.abs(z) == r;
                boolean cross = x == 0 || z == 0;
                Material m = edge ? Material.QUARTZ_BRICKS : cross ? Material.RED_CONCRETE : Material.SMOOTH_STONE;
                w.getBlockAt(x, y, z).setType(m, false);
                w.getBlockAt(x, y - 1, z).setType(Material.STONE_BRICKS, false);
                if (edge && (x + z) % 4 == 0) w.getBlockAt(x, y + 1, z).setType(Material.LANTERN, false);
            }
        }
        w.getBlockAt(0, y, 0).setType(Material.SEA_LANTERN, false);
        w.setSpawnLocation(0, y + 1, 0);
        w.getPersistentDataContainer().set(built, PersistentDataType.BYTE, (byte) 1);
        plugin.getLogger().info("Lobby-Plattform gebaut");
    }

    // ---------------------------------------------------------------- Gruppen & Inventare

    Group groupOf(World w) {
        if (w == null) return Group.SURVIVAL;
        String n = w.getName();
        return n.equals(plotWorld) || n.equals(creative) ? Group.CREATIVE : Group.SURVIVAL;
    }

    boolean isCreative(World w) {
        return groupOf(w) == Group.CREATIVE;
    }

    private GameMode modeFor(World w) {
        if (w.getName().equals(lobby)) return GameMode.ADVENTURE;
        return isCreative(w) ? GameMode.CREATIVE : GameMode.SURVIVAL;
    }

    private void applyMode(Player p) {
        if (p.isOp() && p.getGameMode() == GameMode.SPECTATOR) return;
        GameMode m = modeFor(p.getWorld());
        if (p.getGameMode() != m) p.setGameMode(m);
    }

    private File invFile(Player p) {
        File dir = new File(plugin.getDataFolder(), "inventories");
        dir.mkdirs();
        return new File(dir, p.getUniqueId() + ".yml");
    }

    private static ItemStack[] clean(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? ItemStack.empty() : in[i];
        return out;
    }

    private void saveGroup(Player p, Group g) {
        File f = invFile(p);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String k = g.name().toLowerCase(Locale.ROOT);
        Base64.Encoder b64 = Base64.getEncoder();
        y.set(k + ".inv", b64.encodeToString(ItemStack.serializeItemsAsBytes(clean(p.getInventory().getContents()))));
        y.set(k + ".ender", b64.encodeToString(ItemStack.serializeItemsAsBytes(clean(p.getEnderChest().getContents()))));
        y.set(k + ".level", p.getLevel());
        y.set(k + ".exp", (double) p.getExp());
        y.set(k + ".health", p.getHealth());
        y.set(k + ".food", p.getFoodLevel());
        y.set(k + ".saturation", (double) p.getSaturation());
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().severe("Inventar von " + p.getName() + " konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void loadGroup(Player p, Group g) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(invFile(p));
        String k = g.name().toLowerCase(Locale.ROOT);
        p.getInventory().clear();
        p.getEnderChest().clear();
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        if (!y.isSet(k)) {
            p.setLevel(0);
            p.setExp(0f);
            p.setFoodLevel(20);
            p.setSaturation(5f);
            return;
        }
        Base64.Decoder b64 = Base64.getDecoder();
        try {
            p.getInventory().setContents(ItemStack.deserializeItemsFromBytes(b64.decode(y.getString(k + ".inv", ""))));
            p.getEnderChest().setContents(ItemStack.deserializeItemsFromBytes(b64.decode(y.getString(k + ".ender", ""))));
        } catch (Exception e) {
            plugin.getLogger().severe("Inventar von " + p.getName() + " (" + k + ") konnte nicht geladen werden: " + e.getMessage());
        }
        p.setLevel(y.getInt(k + ".level"));
        p.setExp((float) y.getDouble(k + ".exp"));
        var maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? 20 : maxAttr.getValue();
        p.setHealth(Math.max(1, Math.min(max, y.getDouble(k + ".health", max))));
        p.setFoodLevel(y.getInt(k + ".food", 20));
        p.setSaturation((float) y.getDouble(k + ".saturation", 5));
    }

    /** Stellt sicher, dass das Inventar zur aktuellen Welt passt. */
    private void ensureGroup(Player p) {
        Group target = groupOf(p.getWorld());
        String cur = p.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING);
        Group current = cur == null ? Group.SURVIVAL : Group.valueOf(cur);
        if (current != target) {
            saveGroup(p, current);
            loadGroup(p, target);
            p.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, target.name());
        } else if (cur == null) {
            p.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, target.name());
        }
        applyMode(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        ensureGroup(e.getPlayer());
        if (e.getPlayer().getWorld().getName().equals(plotWorld) && plots.homeOf(e.getPlayer()) == null) {
            e.getPlayer().sendMessage(Component.text("Willkommen in der Grundstück-Welt! ", NamedTextColor.GREEN)
                    .append(Component.text("[Eigene Parzelle holen]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/plot claim"))));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        World l = Bukkit.getWorld(lobby);
        if (joinToLobby && l != null && !p.getWorld().equals(l)) {
            saveLast(p, p.getLocation());
            p.teleport(l.getSpawnLocation().add(0.5, 0, 0.5));
        }
        ensureGroup(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.sendMessage(Component.text("Willkommen auf TNT-Zockt! ", NamedTextColor.GOLD)
                    .append(Component.text("[Welt wählen]", NamedTextColor.AQUA, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/welten")))
                    .append(Component.text("  oder /welten", NamedTextColor.GRAY)));
        }, 30L);
    }

    // ---------------------------------------------------------------- Letzte Position je Welt

    private NamespacedKey lastKey(World w) {
        return new NamespacedKey(plugin, "last_" + w.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
    }

    private void saveLast(Player p, Location l) {
        if (l.getWorld() == null || l.getWorld().getName().equals(lobby)) return;
        String v = l.getWorld().getUID() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
        p.getPersistentDataContainer().set(lastKey(l.getWorld()), PersistentDataType.STRING, v);
    }

    private Location last(Player p, World w) {
        String v = p.getPersistentDataContainer().get(lastKey(w), PersistentDataType.STRING);
        if (v == null) return null;
        String[] s = v.split(";");
        if (s.length != 6 || !s[0].equals(w.getUID().toString())) return null; // Welt wurde zurückgesetzt
        return new Location(w, Double.parseDouble(s[1]), Double.parseDouble(s[2]), Double.parseDouble(s[3]),
                Float.parseFloat(s[4]), Float.parseFloat(s[5]));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo().getWorld() != null && !e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            saveLast(e.getPlayer(), e.getFrom());
        }
    }

    // ---------------------------------------------------------------- Lobby- & Kreativ-Regeln

    private boolean isLobby(World w) {
        return w != null && w.getName().equals(lobby);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyBreak(BlockBreakEvent e) {
        if (isLobby(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("tntshop.admin")) e.setCancelled(true);
        else if (protectedCreativeSpawn(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyPlace(BlockPlaceEvent e) {
        if (isLobby(e.getBlock().getWorld()) && !e.getPlayer().hasPermission("tntshop.admin")) e.setCancelled(true);
        else if (protectedCreativeSpawn(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    private boolean protectedCreativeSpawn(Player p, Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(creative) || p.hasPermission("tntshop.admin")) return false;
        Location s = l.getWorld().getSpawnLocation();
        return Math.abs(l.getBlockX() - s.getBlockX()) <= creativeSpawnProtect && Math.abs(l.getBlockZ() - s.getBlockZ()) <= creativeSpawnProtect;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && isLobby(e.getEntity().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        World w = e.getEntity().getWorld();
        if (isLobby(w) || isCreative(w)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        // aus der Lobby fallen → zurück zum Spawn
        if (isLobby(e.getTo().getWorld()) && e.getTo().getY() < Generators.SURFACE - 30) {
            e.getPlayer().teleport(e.getTo().getWorld().getSpawnLocation().add(0.5, 0, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        World w = e.getEntity().getWorld();
        if (isLobby(w) || isCreative(w)) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        World w = e.getEntity().getWorld();
        if (isLobby(w) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) { e.setCancelled(true); return; }
        if (isCreative(w)) {
            EntityType t = e.getEntityType();
            if (t == EntityType.WITHER || t == EntityType.ENDER_DRAGON || t == EntityType.WARDEN) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        World w = e.getFrom().getWorld();
        if (isLobby(w) || isCreative(w)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        World w = e.getFrom().getWorld();
        if (isLobby(w) || isCreative(w)) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- Menü

    private ItemStack icon(Material m, String name, NamedTextColor c, String world, String... lines) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, c, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String l : lines) lore.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        World w = Bukkit.getWorld(world);
        int n = w == null ? 0 : w.getPlayers().size();
        lore.add(Component.empty());
        lore.add(Component.text("● " + n + " Spieler hier", n > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("▶ Klicken zum Reisen", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, world);
        it.setItemMeta(meta);
        return it;
    }

    void openMenu(Player p) {
        Menu holder = new Menu();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Welten · TNT-Zockt", NamedTextColor.DARK_RED));
        holder.inventory = inv;
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        inv.setItem(10, icon(Material.BEACON, "Lobby", NamedTextColor.WHITE, lobby, "Treffpunkt und Startpunkt"));
        inv.setItem(11, icon(Material.GRASS_BLOCK, "Survival", NamedTextColor.GREEN, survival, "Die Hauptwelt zum Überleben", "Shop: /shop · Punkte: /punkte"));
        inv.setItem(12, icon(Material.IRON_PICKAXE, "Farmwelt", NamedTextColor.GRAY, farm, "Zum Abbauen von Ressourcen", "Wird ab und zu zurückgesetzt –", "nichts Wichtiges hier bauen!"));
        inv.setItem(14, icon(Material.OAK_SIGN, "Grundstücke", NamedTextColor.AQUA, plotWorld, "Deine eigene 64×64-Parzelle", "Kreativmodus · nur du baust dort", "/plot claim · /plot home"));
        inv.setItem(15, icon(Material.CRAFTING_TABLE, "Kreativwelt", NamedTextColor.LIGHT_PURPLE, creative, "Freies Bauen für alle", "Kreativmodus"));
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("Gut zu wissen", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(
                Component.text("Survival und Kreativ haben", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("getrennte Inventare.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Du kommst immer dorthin zurück,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("wo du die Welt verlassen hast.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        info.setItemMeta(im);
        inv.setItem(16, info);
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String world = it.getItemMeta().getPersistentDataContainer().get(menuKey, PersistentDataType.STRING);
        if (world == null) return;
        p.closeInventory();
        travel(p, world);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    void travel(Player p, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) { p.sendMessage(Component.text("Diese Welt ist gerade nicht verfügbar.", NamedTextColor.RED)); return; }
        Location target = null;
        if (worldName.equals(plotWorld)) target = plots.homeOf(p);
        if (target == null && !worldName.equals(lobby)) target = last(p, w);
        if (target == null) {
            Location s = w.getSpawnLocation();
            target = worldName.equals(lobby) || isCreative(w) ? s.add(0.5, 0, 0.5)
                    : w.getHighestBlockAt(s).getLocation().add(0.5, 1, 0.5);
        }
        p.teleport(target);
        p.sendActionBar(Component.text("→ " + label(worldName), NamedTextColor.GOLD));
    }

    private String label(String world) {
        if (world.equals(lobby)) return "Lobby";
        if (world.equals(survival)) return "Survival";
        if (world.equals(farm)) return "Farmwelt";
        if (world.equals(plotWorld)) return "Grundstücke";
        if (world.equals(creative)) return "Kreativwelt";
        return world;
    }

    // ---------------------------------------------------------------- Befehle

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("welten")) {
            if (sender instanceof Player p) openMenu(p); else sender.sendMessage("Nur im Spiel verfügbar.");
            return true;
        }
        if (name.equals("lobby")) {
            if (sender instanceof Player p) travel(p, lobby); else sender.sendMessage("Nur im Spiel verfügbar.");
            return true;
        }
        // /welt (Admin)
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase(farm)) {
            resetFarm(sender);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("tp") && sender instanceof Player p) {
            travel(p, args[1]);
            return true;
        }
        sender.sendMessage("/welt reset " + farm + "  – Farmwelt neu erzeugen");
        sender.sendMessage("/welt tp <welt>  – in eine Welt reisen");
        return true;
    }

    /** Farmwelt löschen und neu erzeugen. Spieler dort kommen in die Lobby. */
    private void resetFarm(CommandSender by) {
        World w = Bukkit.getWorld(farm);
        World l = Bukkit.getWorld(lobby);
        if (w != null) {
            for (Player p : w.getPlayers()) {
                if (l != null) p.teleport(l.getSpawnLocation().add(0.5, 0, 0.5));
                p.sendMessage(Component.text("Die Farmwelt wird zurückgesetzt – du bist jetzt in der Lobby.", NamedTextColor.YELLOW));
            }
            File folder = w.getWorldFolder();
            if (!Bukkit.unloadWorld(w, false)) {
                by.sendMessage(Component.text("Farmwelt konnte nicht entladen werden – bitte später nochmal.", NamedTextColor.RED));
                return;
            }
            try (Stream<Path> s = Files.walk(folder.toPath())) {
                s.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            } catch (IOException e) {
                by.sendMessage(Component.text("Fehler beim Löschen: " + e.getMessage(), NamedTextColor.RED));
            }
        }
        World neu = createNormal(farm);
        if (neu != null) neu.setDifficulty(Difficulty.NORMAL);
        by.sendMessage(Component.text("✔ Farmwelt wurde neu erzeugt.", NamedTextColor.GREEN));
        plugin.getLogger().info("Farmwelt zurückgesetzt von " + by.getName());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("welt")) return List.of();
        if (args.length == 1) return List.of("reset", "tp");
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) return List.of(farm);
        if (args.length == 2) {
            Collection<World> ws = Bukkit.getWorlds();
            return ws.stream().map(World::getName).toList();
        }
        return List.of();
    }
}

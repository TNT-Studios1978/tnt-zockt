package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Minispiele in einer eigenen Welt: TNT-Run, Spleef, PvP-Arena (Runden) und Parkour (Bestzeiten).
 */
final class Minigames implements Listener, CommandExecutor, TabCompleter {

    static final int HUB_Y = 64;
    private static final String TAG = "tnt_minigame";

    enum Kind { TNTRUN, SPLEEF, PVP }
    enum State { WAITING, COUNTDOWN, RUNNING }

    private final TntShopPlugin plugin;
    private final NamespacedKey menuKey;
    private String worldName = "minispiele";
    private final Map<Kind, Round> rounds = new HashMap<>();
    private final Parkour parkour = new Parkour();
    final ArenaGames arena;
    private File statsFile;
    private YamlConfiguration stats;

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    Minigames(TntShopPlugin plugin) {
        this.plugin = plugin;
        this.menuKey = new NamespacedKey(plugin, "menu_game");
        for (Kind k : Kind.values()) rounds.put(k, new Round(k));
        this.arena = new ArenaGames(plugin, this);
    }

    void configure(String worldName) {
        this.worldName = worldName;
    }

    World world() {
        return Bukkit.getWorld(worldName);
    }

    boolean isGameWorld(World w) {
        return w != null && w.getName().equals(worldName);
    }

    Location hub(World w) {
        return new Location(w, 0.5, HUB_Y + 1, 0.5, 180f, 0f);
    }

    // ---------------------------------------------------------------- Aufbau

    void setup(World w) {
        statsFile = new File(plugin.getDataFolder(), "minigames.yml");
        stats = YamlConfiguration.loadConfiguration(statsFile);
        NamespacedKey built = new NamespacedKey(plugin, "minigames_v1");
        if (!w.getPersistentDataContainer().has(built, PersistentDataType.BYTE)) {
            buildHub(w);
            parkour.build(w);
            for (Round r : rounds.values()) r.buildArena(w);
            buildPvpArena(w);
            w.getPersistentDataContainer().set(built, PersistentDataType.BYTE, (byte) 1);
            plugin.getLogger().info("Minispiel-Welt gebaut");
        }
        arena.setup(w);
        arena.repairIfDirty(w);
        w.setSpawnLocation(0, HUB_Y + 1, 0);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, parkour::tickTimer, 4L, 4L);
    }

    private static void fill(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    w.getBlockAt(x, y, z).setType(m, false);
    }

    private static void label(World w, double x, double y, double z, Component text, float scale) {
        w.spawn(new Location(w, x, y, z), TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(185, 10, 10, 18));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setLineWidth(240);
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            t.addScoreboardTag(TAG);
            t.setPersistent(true);
        });
    }

    private void buildHub(World w) {
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        int r = 10;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > r + 0.4) continue;
            Material m = d > r - 1 ? Material.RED_CONCRETE : ((x + z) & 1) == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE;
            w.getBlockAt(x, HUB_Y, z).setType(m, false);
            w.getBlockAt(x, HUB_Y - 1, z).setType(Material.TNT, false);
            if (d > r - 0.6 && (x + z) % 3 == 0) w.getBlockAt(x, HUB_Y + 1, z).setType(Material.LANTERN, false);
        }
        w.getBlockAt(0, HUB_Y, 0).setType(Material.SEA_LANTERN, false);
        label(w, 0.5, HUB_Y + 3.2, -3.5, Component.text("MINISPIELE", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("Tippe /spiele zum Mitspielen", NamedTextColor.WHITE)), 2.6f);
    }

    private void buildPvpArena(World w) {
        int cx = -300, cz = 0, r = 20, y = HUB_Y;
        fill(w, cx - r, y - 1, cz - r, cx + r, y - 1, cz + r, Material.STONE);
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            Material m = (Math.abs(x) + Math.abs(z)) % 7 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
            w.getBlockAt(cx + x, y, cz + z).setType(m, false);
            if (Math.abs(x) == r || Math.abs(z) == r) for (int h = 1; h <= 6; h++) w.getBlockAt(cx + x, y + h, cz + z).setType(Material.STONE_BRICKS, false);
        }
        for (int[] p : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}, {0, 0}}) {
            fill(w, cx + p[0] - 1, y + 1, cz + p[1] - 1, cx + p[0] + 1, y + 4, cz + p[1] + 1, Material.CHISELED_STONE_BRICKS);
            w.getBlockAt(cx + p[0], y + 5, cz + p[1]).setType(Material.GLOWSTONE, false);
        }
    }

    // ---------------------------------------------------------------- Runden-Spiele

    final class Round {
        final Kind kind;
        State state = State.WAITING;
        final Set<UUID> queue = new LinkedHashSet<>();
        final Set<UUID> players = new HashSet<>();
        final Set<UUID> alive = new HashSet<>();
        int countdown = 0;
        boolean forced = false;
        final Set<Long> crumbling = new HashSet<>();

        Round(Kind kind) { this.kind = kind; }

        String title() {
            return switch (kind) { case TNTRUN -> "TNT-Run"; case SPLEEF -> "Spleef"; case PVP -> "PvP-Arena"; };
        }

        int minPlayers() { return 2; }

        int cx() { return kind == Kind.TNTRUN ? 300 : kind == Kind.SPLEEF ? 0 : -300; }
        int cz() { return kind == Kind.SPLEEF ? 300 : 0; }

        /** Ausscheiden, wenn tiefer als diese Höhe. */
        int loseY() { return kind == Kind.TNTRUN ? 50 : kind == Kind.SPLEEF ? 66 : HUB_Y - 5; }

        void buildArena(World w) {
            int cx = cx(), cz = cz();
            if (kind == Kind.TNTRUN) {
                int r = 16;
                int[] layers = {80, 72, 64};
                Material[] top = {Material.SAND, Material.RED_SAND, Material.GRAVEL};
                for (int i = 0; i < layers.length; i++) {
                    for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                        if (x * x + z * z > r * r) continue;
                        w.getBlockAt(cx + x, layers[i], cz + z).setType(top[i], false);
                        w.getBlockAt(cx + x, layers[i] - 1, cz + z).setType(Material.TNT, false);
                    }
                }
                // Glasring, damit niemand seitlich hinausfällt
                for (int a = 0; a < 360; a += 2) {
                    int x = (int) Math.round(Math.cos(Math.toRadians(a)) * (r + 1)), z = (int) Math.round(Math.sin(Math.toRadians(a)) * (r + 1));
                    for (int y = 60; y <= 84; y++) w.getBlockAt(cx + x, y, cz + z).setType(Material.RED_STAINED_GLASS, false);
                }
            } else if (kind == Kind.SPLEEF) {
                int r = 14, y = 70;
                fill(w, cx - r - 3, 60, cz - r - 3, cx + r + 3, 60, cz + r + 3, Material.SLIME_BLOCK);
                fill(w, cx - r - 3, 61, cz - r - 3, cx + r + 3, 61, cz + r + 3, Material.WATER);
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    w.getBlockAt(cx + x, y, cz + z).setType(Material.SNOW_BLOCK, false);
                }
                for (int a = 0; a < 360; a += 2) {
                    int x = (int) Math.round(Math.cos(Math.toRadians(a)) * (r + 1)), z = (int) Math.round(Math.sin(Math.toRadians(a)) * (r + 1));
                    for (int yy = 62; yy <= 75; yy++) w.getBlockAt(cx + x, yy, cz + z).setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
                }
            }
        }

        Location spawnFor(World w, int i, int n) {
            double a = 2 * Math.PI * i / Math.max(1, n);
            double rad = kind == Kind.PVP ? 15 : 9;
            int y = kind == Kind.TNTRUN ? 81 : kind == Kind.SPLEEF ? 71 : HUB_Y + 1;
            Location l = new Location(w, cx() + 0.5 + Math.cos(a) * rad, y, cz() + 0.5 + Math.sin(a) * rad);
            l.setDirection(new org.bukkit.util.Vector(cx() - l.getX(), 0, cz() - l.getZ()));
            return l;
        }

        boolean contains(Location l) {
            return Math.abs(l.getX() - cx()) <= 30 && Math.abs(l.getZ() - cz()) <= 30;
        }

        void join(Player p) {
            leaveAll(p);
            queue.add(p.getUniqueId());
            p.teleport(hub(p.getWorld()));
            if (state == State.RUNNING) {
                msg(p, title() + " läuft gerade – du bist für die nächste Runde angemeldet.", NamedTextColor.YELLOW);
            } else {
                msg(p, "Angemeldet für " + title() + " (" + queue.size() + "/" + minPlayers() + " Spieler).", NamedTextColor.GREEN);
                broadcastQueue(Component.text(p.getName() + " spielt " + title() + " mit! (" + queue.size() + " angemeldet)", NamedTextColor.GRAY));
            }
        }

        void broadcastQueue(Component c) {
            for (UUID u : queue) { Player p = Bukkit.getPlayer(u); if (p != null) p.sendMessage(c); }
        }

        void tick(World w) {
            queue.removeIf(u -> { Player p = Bukkit.getPlayer(u); return p == null || !isGameWorld(p.getWorld()); });
            if (state == State.WAITING && (queue.size() >= minPlayers() || (forced && !queue.isEmpty()))) {
                state = State.COUNTDOWN;
                countdown = forced ? 5 : 15;
            }
            if (state == State.COUNTDOWN) {
                if (queue.size() < minPlayers() && !forced) { state = State.WAITING; broadcastQueue(Component.text("Zu wenige Spieler – Start abgebrochen.", NamedTextColor.RED)); return; }
                for (UUID u : queue) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) p.sendActionBar(Component.text(title() + " startet in " + countdown + " s", NamedTextColor.GOLD));
                    if (p != null && countdown <= 3) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
                if (--countdown <= 0) start(w);
            }
            if (state == State.RUNNING) {
                for (UUID u : new ArrayList<>(alive)) {
                    Player p = Bukkit.getPlayer(u);
                    if (p == null || !isGameWorld(p.getWorld())) { eliminate(u, null); continue; }
                    p.sendActionBar(Component.text(title() + " · noch " + alive.size() + " im Spiel", NamedTextColor.YELLOW));
                }
            }
        }

        void start(World w) {
            buildArena(w);
            crumbling.clear();
            players.clear();
            alive.clear();
            List<UUID> list = new ArrayList<>(queue);
            queue.clear();
            int i = 0;
            for (UUID u : list) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                players.add(u);
                alive.add(u);
                p.teleport(spawnFor(w, i++, list.size()));
                resetPlayer(p);
                p.setGameMode(kind == Kind.TNTRUN ? GameMode.ADVENTURE : GameMode.SURVIVAL);
                giveKit(p);
                p.showTitle(Title.title(Component.text(title(), NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(kind == Kind.TNTRUN ? "Lauf! Der Boden verschwindet!" : kind == Kind.SPLEEF ? "Schaufle den Boden unter den anderen weg!" : "Der Letzte gewinnt!", NamedTextColor.WHITE),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
            forced = false;
            state = State.RUNNING;
            if (alive.isEmpty()) state = State.WAITING;
        }

        void giveKit(Player p) {
            var inv = p.getInventory();
            if (kind == Kind.SPLEEF) {
                ItemStack sh = new ItemStack(Material.DIAMOND_SHOVEL);
                sh.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
                ItemMeta m = sh.getItemMeta();
                m.setUnbreakable(true);
                sh.setItemMeta(m);
                inv.addItem(sh);
            } else if (kind == Kind.PVP) {
                inv.setHelmet(new ItemStack(Material.IRON_HELMET));
                inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                inv.setBoots(new ItemStack(Material.IRON_BOOTS));
                inv.addItem(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 16),
                        new ItemStack(Material.GOLDEN_APPLE, 2), new ItemStack(Material.COOKED_BEEF, 8));
                inv.setItemInOffHand(new ItemStack(Material.SHIELD));
            }
        }

        void eliminate(UUID u, String reason) {
            if (!alive.remove(u)) return;
            Player p = Bukkit.getPlayer(u);
            if (p != null && isGameWorld(p.getWorld())) {
                resetPlayer(p);
                p.setGameMode(GameMode.ADVENTURE);
                p.teleport(hub(p.getWorld()));
                p.showTitle(Title.title(Component.text("Ausgeschieden", NamedTextColor.RED), Component.text(reason == null ? "" : reason, NamedTextColor.GRAY)));
            }
            for (UUID o : players) {
                Player op = Bukkit.getPlayer(o);
                if (op != null && p != null) op.sendMessage(Component.text("✖ " + p.getName() + " ist ausgeschieden – noch " + alive.size() + " übrig.", NamedTextColor.GRAY));
            }
            if (alive.size() <= 1) finish(alive.isEmpty() ? u : alive.iterator().next());
        }

        void finish(UUID winner) {
            Player w = Bukkit.getPlayer(winner);
            String name = w != null ? w.getName() : "?";
            if (w != null && players.size() > 1) addWin(kind.name().toLowerCase(Locale.ROOT), w);
            Component c = Component.text("★ " + name + " gewinnt " + title() + "!", NamedTextColor.GOLD, TextDecoration.BOLD);
            World gw = world();
            if (gw != null) for (Player p : gw.getPlayers()) p.sendMessage(c);
            for (UUID u : players) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                if (alive.contains(u) && isGameWorld(p.getWorld())) { resetPlayer(p); p.setGameMode(GameMode.ADVENTURE); p.teleport(hub(p.getWorld())); }
                p.showTitle(Title.title(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD), Component.text("hat gewonnen!", NamedTextColor.WHITE)));
            }
            alive.clear();
            players.clear();
            state = State.WAITING;
            if (gw != null) Bukkit.getScheduler().runTaskLater(plugin, () -> buildArena(gw), 40L);
        }
    }

    void resetPlayer(Player p) {
        p.getInventory().clear();
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
    }

    private Round roundOf(UUID u) {
        for (Round r : rounds.values()) if (r.alive.contains(u)) return r;
        return null;
    }

    void leaveAll(Player p) {
        UUID u = p.getUniqueId();
        for (Round r : rounds.values()) {
            r.queue.remove(u);
            if (r.alive.contains(u)) r.eliminate(u, "Spiel verlassen");
        }
        arena.leave(p);
        parkour.stop(p);
    }

    private void tick() {
        World w = world();
        if (w == null) return;
        for (Round r : rounds.values()) r.tick(w);
        arena.tick(w);
    }

    // ---------------------------------------------------------------- Parkour

    final class Parkour {
        final int sx = 0, sz = -300, sy = HUB_Y;
        final Map<UUID, Long> started = new HashMap<>();
        final Map<UUID, Location> checkpoint = new HashMap<>();

        Location start(World w) { return new Location(w, sx + 0.5, sy + 1, sz + 0.5, 180f, 0f); }

        void build(World w) {
            Random r = new Random(2026);
            fill(w, sx - 2, sy, sz - 2, sx + 2, sy, sz + 2, Material.QUARTZ_BLOCK);
            w.getBlockAt(sx, sy + 1, sz).setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, false);
            label(w, sx + 0.5, sy + 3, sz + 0.5, Component.text("PARKOUR", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.newline()).append(Component.text("Tritt auf die Platte zum Starten", NamedTextColor.WHITE)), 2.2f);
            int x = sx, y = sy, z = sz - 3;
            Material[] colors = {Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.RED_CONCRETE};
            for (int i = 1; i <= 40; i++) {
                int section = Math.min(3, (i - 1) / 10);
                int dy = r.nextInt(10) < 3 ? 1 : (r.nextInt(10) < 2 && y > sy ? -1 : 0);
                int maxStep = dy == 1 ? 3 : (section >= 2 ? 4 : 3);
                int step = 2 + r.nextInt(maxStep - 1);
                z -= step;
                x += r.nextInt(3) - 1;
                y += dy;
                if (i % 10 == 0 && i < 40) {
                    fill(w, x - 1, y, z - 1, x + 1, y, z + 1, Material.GOLD_BLOCK);
                    w.getBlockAt(x, y + 1, z).setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, false);
                    z -= 1;
                } else if (i == 40) {
                    fill(w, x - 2, y, z - 2, x + 2, y, z + 2, Material.DIAMOND_BLOCK);
                    w.getBlockAt(x, y + 1, z).setType(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, false);
                    label(w, x + 0.5, y + 3, z + 0.5, Component.text("ZIEL", NamedTextColor.AQUA, TextDecoration.BOLD), 2.5f);
                } else {
                    w.getBlockAt(x, y, z).setType(colors[section], false);
                }
            }
        }

        void stop(Player p) {
            started.remove(p.getUniqueId());
            checkpoint.remove(p.getUniqueId());
        }

        void onPlate(Player p, Block b) {
            UUID u = p.getUniqueId();
            Material t = b.getType();
            if (t == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
                if (!started.containsKey(u)) {
                    started.put(u, System.currentTimeMillis());
                    checkpoint.put(u, start(p.getWorld()));
                    p.sendMessage(Component.text("⏱ Parkour gestartet – los!", NamedTextColor.GREEN));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
                }
            } else if (t == Material.LIGHT_WEIGHTED_PRESSURE_PLATE && started.containsKey(u)) {
                Location cp = b.getLocation().add(0.5, 0, 0.5);
                cp.setYaw(180f);
                Location old = checkpoint.put(u, cp);
                if (old == null || old.distanceSquared(cp) > 1) {
                    p.sendActionBar(Component.text("✔ Checkpoint!", NamedTextColor.GOLD));
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
            } else if (t == Material.POLISHED_BLACKSTONE_PRESSURE_PLATE && started.containsKey(u)) {
                long ms = System.currentTimeMillis() - started.remove(u);
                checkpoint.remove(u);
                String time = fmtTime(ms);
                long best = stats.getLong("parkour." + u + ".best", Long.MAX_VALUE);
                boolean pb = ms < best;
                if (pb) {
                    stats.set("parkour." + u + ".best", ms);
                    stats.set("parkour." + u + ".name", p.getName());
                    saveStats();
                }
                long record = bestOverall();
                World gw = p.getWorld();
                Component c = Component.text("⚑ " + p.getName() + " hat den Parkour in " + time + " geschafft!", NamedTextColor.AQUA)
                        .append(Component.text(ms <= record ? "  NEUER REKORD!" : pb ? "  (persönliche Bestzeit)" : "", NamedTextColor.GOLD, TextDecoration.BOLD));
                for (Player o : gw.getPlayers()) o.sendMessage(c);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.teleport(hub(gw));
            }
        }

        long bestOverall() {
            long best = Long.MAX_VALUE;
            var sec = stats.getConfigurationSection("parkour");
            if (sec != null) for (String k : sec.getKeys(false)) best = Math.min(best, sec.getLong(k + ".best", Long.MAX_VALUE));
            return best;
        }

        void tickTimer() {
            for (var e : started.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) p.sendActionBar(Component.text("⏱ " + fmtTime(System.currentTimeMillis() - e.getValue()), NamedTextColor.GREEN));
            }
        }

        void onFall(Player p) {
            Location cp = checkpoint.get(p.getUniqueId());
            p.setFallDistance(0);
            p.teleport(cp != null ? cp : start(p.getWorld()));
        }

        boolean near(Location l) {
            return Math.abs(l.getX() - sx) <= 40 && l.getZ() <= sz + 5 && l.getZ() >= sz - 200;
        }
    }

    private static String fmtTime(long ms) {
        return String.format(Locale.ROOT, "%d:%02d.%02d", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 10);
    }

    // ---------------------------------------------------------------- Statistik

    void addWin(String game, Player p) {
        String k = "wins." + game + "." + p.getUniqueId();
        stats.set(k + ".count", stats.getInt(k + ".count") + 1);
        stats.set(k + ".name", p.getName());
        saveStats();
    }

    private void saveStats() {
        try { stats.save(statsFile); } catch (IOException e) { plugin.getLogger().warning("minigames.yml: " + e.getMessage()); }
    }

    private void showTop(CommandSender s) {
        s.sendMessage(Component.text("★ Bestenliste", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (Kind k : Kind.values()) {
            var sec = stats.getConfigurationSection("wins." + k.name().toLowerCase(Locale.ROOT));
            List<String[]> list = new ArrayList<>();
            if (sec != null) for (String u : sec.getKeys(false)) list.add(new String[]{sec.getString(u + ".name", "?"), String.valueOf(sec.getInt(u + ".count"))});
            list.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, list.size()); i++) sb.append(i + 1).append(". ").append(list.get(i)[0]).append(" (").append(list.get(i)[1]).append(")  ");
            s.sendMessage(Component.text(" " + rounds.get(k).title() + ": ", NamedTextColor.YELLOW).append(Component.text(sb.isEmpty() ? "noch keine Siege" : sb.toString(), NamedTextColor.WHITE)));
        }
        for (String[] g : new String[][]{{"bedwars", "Bedwars"}, {"skywars", "Skywars"}}) {
            var sec = stats.getConfigurationSection("wins." + g[0]);
            List<String[]> list = new ArrayList<>();
            if (sec != null) for (String u : sec.getKeys(false)) list.add(new String[]{sec.getString(u + ".name", "?"), String.valueOf(sec.getInt(u + ".count"))});
            list.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, list.size()); i++) sb.append(i + 1).append(". ").append(list.get(i)[0]).append(" (").append(list.get(i)[1]).append(")  ");
            s.sendMessage(Component.text(" " + g[1] + ": ", NamedTextColor.YELLOW).append(Component.text(sb.isEmpty() ? "noch keine Siege" : sb.toString(), NamedTextColor.WHITE)));
        }
        var pk = stats.getConfigurationSection("parkour");
        List<String[]> times = new ArrayList<>();
        if (pk != null) for (String u : pk.getKeys(false)) times.add(new String[]{pk.getString(u + ".name", "?"), String.valueOf(pk.getLong(u + ".best"))});
        times.sort((a, b) -> Long.compare(Long.parseLong(a[1]), Long.parseLong(b[1])));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, times.size()); i++) sb.append(i + 1).append(". ").append(times.get(i)[0]).append(" (").append(fmtTime(Long.parseLong(times.get(i)[1]))).append(")  ");
        s.sendMessage(Component.text(" Parkour: ", NamedTextColor.YELLOW).append(Component.text(sb.isEmpty() ? "noch keine Zeiten" : sb.toString(), NamedTextColor.WHITE)));
    }

    // ---------------------------------------------------------------- Menü

    private ItemStack icon(Material m, String id, String name, NamedTextColor c, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, c, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        l.add(Component.text("▶ Klicken zum Mitspielen", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private String status(Round r) {
        return switch (r.state) {
            case RUNNING -> "● Läuft (" + r.alive.size() + " im Spiel) – " + r.queue.size() + " warten";
            case COUNTDOWN -> "● Startet gleich! (" + r.queue.size() + " angemeldet)";
            default -> r.queue.size() + "/" + r.minPlayers() + " Spieler angemeldet";
        };
    }

    void openMenu(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("Minispiele · TNT-Zockt", NamedTextColor.DARK_RED));
        h.inventory = inv;
        inv.setItem(14, icon(Material.RED_BED, "bedwars", "Bedwars", NamedTextColor.RED, "4 Teams · Schütze dein Bett!", "Shop, Generatoren, bis 8 Spieler.", arena.bedwars.status()));
        inv.setItem(15, icon(Material.GRASS_BLOCK, "skywars", "Skywars", NamedTextColor.AQUA, "Jeder auf seiner Insel, Truhen plündern.", "Der Letzte gewinnt. Bis 8 Spieler.", arena.skywars.status()));
        inv.setItem(10, icon(Material.TNT, "tntrun", "TNT-Run", NamedTextColor.RED, "Der Boden verschwindet unter dir!", "Wer zuletzt steht, gewinnt.", status(rounds.get(Kind.TNTRUN))));
        inv.setItem(11, icon(Material.DIAMOND_SHOVEL, "spleef", "Spleef", NamedTextColor.AQUA, "Schaufle den Schnee unter den", "anderen weg!", status(rounds.get(Kind.SPLEEF))));
        inv.setItem(12, icon(Material.IRON_SWORD, "pvp", "PvP-Arena", NamedTextColor.GOLD, "Alle gegen alle, gleiche Ausrüstung.", "Der Letzte gewinnt.", status(rounds.get(Kind.PVP))));
        inv.setItem(16, icon(Material.FEATHER, "parkour", "Parkour", NamedTextColor.GREEN, "40 Sprünge, 4 Schwierigkeiten.", "Jag die Bestzeit!"));
        inv.setItem(22, icon(Material.GOLD_INGOT, "top", "Bestenliste", NamedTextColor.YELLOW, "Siege und Parkour-Bestzeiten"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
        String id = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(menuKey, PersistentDataType.STRING);
        if (id == null) return;
        p.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> p.performCommand("spiele " + id));
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- Events

    @EventHandler
    public void onEnter(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (isGameWorld(e.getFrom())) leaveAll(p);
        if (isGameWorld(p.getWorld())) {
            resetPlayer(p);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && isGameWorld(p.getWorld())) openMenu(p); }, 10L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leaveAll(e.getPlayer());
        if (isGameWorld(e.getPlayer().getWorld())) {
            World w = world();
            if (w != null) e.getPlayer().teleport(hub(w));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isGameWorld(p.getWorld())) return;
        Location to = e.getTo();
        if (arena.handleMove(p, to)) return;
        UUID u = p.getUniqueId();
        Round r = roundOf(u);
        if (r != null) {
            if (to.getY() < r.loseY()) { r.eliminate(u, "Hinuntergefallen"); return; }
            if (r.kind == Kind.TNTRUN && r.state == State.RUNNING) crumble(r, to);
            return;
        }
        if (parkour.started.containsKey(u) || parkour.near(to)) {
            if (to.getY() < parkour.sy - 6) parkour.onFall(p);
            return;
        }
        if (to.getY() < HUB_Y - 20) { p.setFallDistance(0); p.teleport(hub(p.getWorld())); }
    }

    /** Blöcke unter dem Spieler verschwinden kurz nach dem Betreten. */
    private void crumble(Round r, Location to) {
        World w = to.getWorld();
        double[][] offs = {{0, 0}, {0.3, 0.3}, {-0.3, 0.3}, {0.3, -0.3}, {-0.3, -0.3}};
        for (double[] o : offs) {
            Block b = w.getBlockAt((int) Math.floor(to.getX() + o[0]), (int) Math.floor(to.getY() - 0.1) - 1, (int) Math.floor(to.getZ() + o[1]));
            Material m = b.getType();
            if (m != Material.SAND && m != Material.RED_SAND && m != Material.GRAVEL) continue;
            long key = (((long) b.getX()) << 36) ^ (((long) b.getZ()) << 12) ^ b.getY();
            if (!r.crumbling.add(key)) continue;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (r.state != State.RUNNING) return;
                b.setType(Material.AIR, false);
                Block under = b.getRelative(0, -1, 0);
                if (under.getType() == Material.TNT) under.setType(Material.AIR, false);
            }, 7L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!isGameWorld(p.getWorld())) return;
        if (arena.handleBreak(e)) return;
        Round r = roundOf(p.getUniqueId());
        if (r != null && r.kind == Kind.SPLEEF && r.state == State.RUNNING && e.getBlock().getType() == Material.SNOW_BLOCK && r.contains(e.getBlock().getLocation())) {
            e.setCancelled(false);
            e.setDropItems(false);
            return;
        }
        if (!p.hasPermission("tntshop.admin") || p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (isGameWorld(e.getPlayer().getWorld()) && arena.handlePlace(e)) return;
        if (isGameWorld(e.getPlayer().getWorld()) && (!e.getPlayer().hasPermission("tntshop.admin") || e.getPlayer().getGameMode() != GameMode.CREATIVE)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (!isGameWorld(e.getPlayer().getWorld())) return;
        if (arena.handleInteract(e)) return;
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null) {
            parkour.onPlate(e.getPlayer(), e.getClickedBlock());
            return;
        }
        // TNT nicht anzünden, keine Truhen o.ä. in der Spielwelt
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !e.getPlayer().hasPermission("tntshop.admin")) {
            Material t = e.getClickedBlock() == null ? Material.AIR : e.getClickedBlock().getType();
            if (t == Material.TNT || t.isInteractable()) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isGameWorld(e.getPlayer().getWorld()) && !arena.handleDrop(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (isGameWorld(e.getEntity().getWorld()) && roundOf(e.getEntity().getUniqueId()) == null
                && !(e.getEntity() instanceof Player hp && arena.isPlaying(hp))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isGameWorld(p.getWorld())) return;
        if (arena.handleDamage(e, p)) return;
        Round r = roundOf(p.getUniqueId());
        if (r == null || r.kind != Kind.PVP || r.state != State.RUNNING) {
            if (e.getCause() != EntityDamageEvent.DamageCause.VOID) e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Player attacker = be.getDamager() instanceof Player a ? a
                    : be.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player a2 ? a2 : null;
            if (attacker != null && roundOf(attacker.getUniqueId()) != r) { e.setCancelled(true); return; }
        }
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            String by = e instanceof EntityDamageByEntityEvent be2 && be2.getDamager() instanceof Player k ? "von " + k.getName() : "";
            r.eliminate(p.getUniqueId(), by);
        }
    }

    // ---------------------------------------------------------------- Befehle

    private void msg(CommandSender s, String t, NamedTextColor c) {
        s.sendMessage(Component.text("[Minispiele] ", NamedTextColor.RED).append(Component.text(t, c)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("top")) { showTop(sender); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur im Spiel verfügbar."); return true; }
        World w = world();
        if (w == null) { msg(p, "Die Minispiel-Welt ist nicht geladen.", NamedTextColor.RED); return true; }
        if (!isGameWorld(p.getWorld())) {
            p.teleport(hub(w)); // Weltwechsel öffnet automatisch das Menü
            if (sub.isEmpty()) return true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.performCommand("spiele " + sub), 15L);
            return true;
        }
        switch (sub) {
            case "" -> openMenu(p);
            case "tntrun", "tnt" -> rounds.get(Kind.TNTRUN).join(p);
            case "spleef" -> rounds.get(Kind.SPLEEF).join(p);
            case "pvp", "arena" -> rounds.get(Kind.PVP).join(p);
            case "bedwars", "bw" -> arena.bedwars.join(p);
            case "skywars", "sw" -> arena.skywars.join(p);
            case "parkour" -> {
                leaveAll(p);
                p.teleport(parkour.start(w));
                msg(p, "Tritt auf die Eisenplatte, um die Zeit zu starten. Goldplatten sind Checkpoints.", NamedTextColor.GREEN);
            }
            case "leave", "verlassen" -> { leaveAll(p); p.teleport(hub(w)); msg(p, "Du hast das Spiel verlassen.", NamedTextColor.GRAY); }
            case "start" -> {
                if (!p.hasPermission("tntshop.admin")) return true;
                for (Round r : rounds.values()) if (r.queue.contains(p.getUniqueId())) { r.forced = true; msg(p, r.title() + " wird gestartet (Test).", NamedTextColor.YELLOW); }
                arena.forceStart(p);
            }
            case "neubauen", "rebuild" -> {
                if (!p.hasPermission("tntshop.admin")) return true;
                buildHub(w); parkour.build(w); for (Round r : rounds.values()) r.buildArena(w); buildPvpArena(w);
                arena.rebuild(w, p);
                msg(p, "Alle Minispiel-Arenen neu gebaut.", NamedTextColor.GREEN);
            }
            default -> {
                msg(p, "/spiele – Menü · /spiele <bedwars|skywars|tntrun|spleef|pvp|parkour> · /spiele leave · /spiele top", NamedTextColor.GRAY);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String a, String @NotNull [] args) {
        if (args.length == 1) return List.of("bedwars", "skywars", "tntrun", "spleef", "pvp", "parkour", "leave", "top");
        return List.of();
    }

    /** Für den Schloss-Hinweis */
    static Component hint() {
        return Component.text("[Minispiele]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/spiele"));
    }
}

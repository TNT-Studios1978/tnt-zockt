package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bedwars (4 Teams, Betten, Generatoren, Shop) und Skywars (8 Inseln, Truhen, Käfige)
 * in der Minispiel-Welt. Alle Blockänderungen werden protokolliert und nach jeder Runde
 * zurückgesetzt, damit die Arena immer wie neu ist.
 */
final class ArenaGames implements Listener {

    enum State { WAITING, COUNTDOWN, RUNNING, ENDING }

    record Pos(int x, int y, int z) {
        static Pos of(Block b) { return new Pos(b.getX(), b.getY(), b.getZ()); }
    }

    private final TntShopPlugin plugin;
    private final Minigames games;
    private final NamespacedKey shopKey;
    final Bedwars bedwars = new Bedwars();
    final Skywars skywars = new Skywars();
    private final List<Match> matches = List.of(bedwars, skywars);

    ArenaGames(TntShopPlugin plugin, Minigames games) {
        this.plugin = plugin;
        this.games = games;
        this.shopKey = new NamespacedKey(plugin, "bw_offer");
    }

    // ---------------------------------------------------------------- Allgemein

    void setup(World w) {
        NamespacedKey built = new NamespacedKey(plugin, "arenas_v1");
        if (!w.getPersistentDataContainer().has(built, PersistentDataType.BYTE)) {
            buildAll(w);
            w.getPersistentDataContainer().set(built, PersistentDataType.BYTE, (byte) 1);
            plugin.getLogger().info("Bedwars- und Skywars-Arena gebaut");
        }
    }

    void buildAll(World w) {
        for (Match m : matches) m.buildArena(w);
    }

    private NamespacedKey dirtyKey(Match m) { return new NamespacedKey(plugin, "dirty_" + m.id); }

    /** Nach einem Absturz mitten in einer Runde: Arena komplett leeren und neu bauen. */
    void repairIfDirty(World w) {
        for (Match m : matches) if (w.getPersistentDataContainer().has(dirtyKey(m), PersistentDataType.BYTE)) {
            plugin.getLogger().info(m.title + "-Arena war nicht zurückgesetzt – baue sie neu …");
            m.fullRebuild(w, null);
        }
    }

    /** Beim Herunterfahren laufende Runden beenden und Arenen zurücksetzen. */
    void shutdown() {
        World w = games.world();
        if (w == null) return;
        for (Match m : matches) {
            for (UUID u : new HashSet<>(m.players)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && games.isGameWorld(p.getWorld())) m.toHub(p);
            }
            m.players.clear();
            m.alive.clear();
            if (!m.originals.isEmpty() || m.state != State.WAITING) m.restore(w);
            m.state = State.WAITING;
        }
    }

    void rebuild(World w, Player by) {
        for (Match m : matches) {
            if (m.state != State.WAITING) { by.sendMessage(Component.text(m.title + " läuft gerade – später neu bauen.", NamedTextColor.RED)); continue; }
            m.fullRebuild(w, () -> by.sendMessage(Component.text("✔ " + m.title + "-Arena neu gebaut.", NamedTextColor.GREEN)));
        }
    }

    void tick(World w) {
        for (Match m : matches) m.tick(w);
    }

    Match matchOf(Player p) {
        for (Match m : matches) if (m.players.contains(p.getUniqueId())) return m;
        return null;
    }

    boolean isPlaying(Player p) {
        return matchOf(p) != null;
    }

    boolean isQueued(Player p) {
        for (Match m : matches) if (m.queue.contains(p.getUniqueId())) return true;
        return false;
    }

    void leave(Player p) {
        for (Match m : matches) {
            m.queue.remove(p.getUniqueId());
            if (m.players.contains(p.getUniqueId())) m.quit(p);
        }
    }

    void forceStart(Player p) {
        for (Match m : matches) if (m.queue.contains(p.getUniqueId())) {
            m.forced = true;
            if (m.state == State.COUNTDOWN && m.countdown > 5) m.countdown = 5;
            p.sendMessage(Component.text(m.title + " wird gestartet (Test).", NamedTextColor.YELLOW));
        }
    }

    private static ItemStack item(Material m, int amount, String name, NamedTextColor c) {
        ItemStack it = new ItemStack(m, amount);
        if (name != null) {
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.text(name, c).decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack unbreakable(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.setUnbreakable(true);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack ench(Material m, Enchantment e, int lvl) {
        ItemStack it = new ItemStack(m);
        it.addUnsafeEnchantment(e, lvl);
        return it;
    }

    private static void give(Player p, ItemStack... items) {
        for (ItemStack left : p.getInventory().addItem(items).values()) p.getWorld().dropItem(p.getLocation(), left);
    }

    private static TextDisplay floating(World w, Location l, Component text, float scale) {
        return w.spawn(l, TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(185, 10, 10, 18));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            t.setPersistent(false);
        });
    }

    // ---------------------------------------------------------------- Gemeinsame Rundenlogik

    abstract class Match {
        final String id, title;
        final NamedTextColor color;
        State state = State.WAITING;
        final Set<UUID> queue = new LinkedHashSet<>();
        final Set<UUID> players = new HashSet<>();
        final Set<UUID> alive = new HashSet<>();
        final Map<Pos, BlockData> originals = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitAt = new HashMap<>();
        final List<Entity> temp = new ArrayList<>();
        int countdown, seconds, startCount;
        boolean rebuilding;
        boolean forced;
        final int cx, cz, y, radius;

        Match(String id, String title, NamedTextColor color, int cx, int cz, int y, int radius) {
            this.id = id; this.title = title; this.color = color;
            this.cx = cx; this.cz = cz; this.y = y; this.radius = radius;
        }

        abstract int minPlayers();
        abstract int maxPlayers();
        abstract void buildArena(World w);
        abstract void begin(World w, List<Player> list);
        abstract void tickRunning(World w);
        abstract void die(Player p, Player killer, String cause);
        abstract Component kitTitle();

        boolean inBounds(Location l) {
            return Math.abs(l.getBlockX() - cx) <= radius && Math.abs(l.getBlockZ() - cz) <= radius
                    && l.getBlockY() >= y - 30 && l.getBlockY() <= y + 30;
        }

        boolean inBounds(Block b) { return inBounds(b.getLocation()); }

        int loseY() { return y - 30; }

        Location center(World w) { return new Location(w, cx + 0.5, y + 1, cz + 0.5); }

        void record(Block b) {
            originals.putIfAbsent(Pos.of(b), b.getBlockData().clone());
        }

        void msg(Player p, String t, NamedTextColor c) {
            p.sendMessage(Component.text("[" + title + "] ", color).append(Component.text(t, c)));
        }

        void broadcast(Component c) {
            for (UUID u : players) { Player p = Bukkit.getPlayer(u); if (p != null) p.sendMessage(c); }
        }

        void join(Player p) {
            games.leaveAll(p);
            queue.add(p.getUniqueId());
            p.teleport(games.hub(p.getWorld()));
            if (state == State.RUNNING || state == State.ENDING) {
                msg(p, title + " läuft gerade – du bist für die nächste Runde angemeldet.", NamedTextColor.YELLOW);
            } else {
                msg(p, "Angemeldet für " + title + " (" + queue.size() + "/" + minPlayers() + " Spieler, max. " + maxPlayers() + ").", NamedTextColor.GREEN);
                for (UUID u : queue) {
                    Player o = Bukkit.getPlayer(u);
                    if (o != null && o != p) o.sendMessage(Component.text(p.getName() + " spielt " + title + " mit! (" + queue.size() + " angemeldet)", NamedTextColor.GRAY));
                }
            }
        }

        String status() {
            return switch (state) {
                case RUNNING, ENDING -> "● Läuft (" + alive.size() + " im Spiel) – " + queue.size() + " warten";
                case COUNTDOWN -> "● Startet gleich! (" + queue.size() + " angemeldet)";
                default -> queue.size() + "/" + minPlayers() + " Spieler angemeldet (max. " + maxPlayers() + ")";
            };
        }

        void tick(World w) {
            queue.removeIf(u -> { Player p = Bukkit.getPlayer(u); return p == null || !games.isGameWorld(p.getWorld()); });
            if (rebuilding) return;
            if (state == State.WAITING && (queue.size() >= minPlayers() || (forced && !queue.isEmpty()))) {
                state = State.COUNTDOWN;
                countdown = forced ? 5 : 20;
            }
            if (state == State.COUNTDOWN) {
                if (queue.size() < minPlayers() && !forced) {
                    state = State.WAITING;
                    for (UUID u : queue) { Player p = Bukkit.getPlayer(u); if (p != null) msg(p, "Zu wenige Spieler – Start abgebrochen.", NamedTextColor.RED); }
                    return;
                }
                if (queue.size() >= maxPlayers() && countdown > 5) countdown = 5;
                for (UUID u : queue) {
                    Player p = Bukkit.getPlayer(u);
                    if (p == null) continue;
                    p.sendActionBar(Component.text(title + " startet in " + countdown + " s  (" + queue.size() + "/" + maxPlayers() + ")", NamedTextColor.GOLD));
                    if (countdown <= 3) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
                if (--countdown <= 0) start(w);
            }
            if (state == State.RUNNING) {
                seconds++;
                for (UUID u : new ArrayList<>(alive)) {
                    Player p = Bukkit.getPlayer(u);
                    if (p == null || !games.isGameWorld(p.getWorld())) { removePlayer(u, true); }
                }
                if (state == State.RUNNING) tickRunning(w);
            }
        }

        void start(World w) {
            restore(w);
            players.clear();
            alive.clear();
            lastHit.clear();
            lastHitAt.clear();
            List<Player> list = new ArrayList<>();
            for (UUID u : new ArrayList<>(queue)) {
                if (list.size() >= maxPlayers()) break;
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                list.add(p);
                queue.remove(u);
            }
            forced = false;
            seconds = 0;
            startCount = list.size();
            if (list.isEmpty()) { state = State.WAITING; return; }
            for (Player p : list) {
                players.add(p.getUniqueId());
                alive.add(p.getUniqueId());
                games.resetPlayer(p);
                p.setGameMode(GameMode.SURVIVAL);
            }
            state = State.RUNNING;
            w.getPersistentDataContainer().set(dirtyKey(this), PersistentDataType.BYTE, (byte) 1);
            begin(w, list);
            for (Player p : list) {
                p.showTitle(Title.title(Component.text(title, color, TextDecoration.BOLD), kitTitle(),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
            }
            if (!queue.isEmpty()) for (UUID u : queue) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) msg(p, "Die Runde ist voll – du bist bei der nächsten dabei.", NamedTextColor.YELLOW);
            }
        }

        /** Spieler verlässt das Spiel (Befehl, Weltwechsel, Ausloggen). */
        void quit(Player p) {
            if (state == State.RUNNING && alive.contains(p.getUniqueId())) {
                broadcast(Component.text("✖ " + p.getName() + " hat das Spiel verlassen.", NamedTextColor.GRAY));
            }
            removePlayer(p.getUniqueId(), true);
        }

        void removePlayer(UUID u, boolean check) {
            boolean was = alive.remove(u);
            players.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null && games.isGameWorld(p.getWorld())) toHub(p);
            onRemoved(u);
            if (was && check) checkWin();
        }

        void onRemoved(UUID u) { }

        void toHub(Player p) {
            games.resetPlayer(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(games.hub(p.getWorld()));
        }

        /** Ausgeschieden – bleibt als Zuschauer-Statistik in players, kommt in den Hub. */
        void eliminate(Player p, String reason) {
            if (!alive.remove(p.getUniqueId())) return;
            players.remove(p.getUniqueId());
            toHub(p);
            p.showTitle(Title.title(Component.text("Ausgeschieden", NamedTextColor.RED), Component.text(reason == null ? "" : reason, NamedTextColor.GRAY)));
            onRemoved(p.getUniqueId());
            checkWin();
        }

        abstract void checkWin();

        Player killerOf(Player p) {
            UUID k = lastHit.get(p.getUniqueId());
            Long t = lastHitAt.get(p.getUniqueId());
            if (k == null || t == null || System.currentTimeMillis() - t > 12000) return null;
            Player kp = Bukkit.getPlayer(k);
            return kp != null && players.contains(k) ? kp : null;
        }

        void finish(Collection<UUID> winners, Component headline) {
            if (state != State.RUNNING) return;
            state = State.ENDING;
            World gw = games.world();
            if (gw != null) for (Player p : gw.getPlayers()) p.sendMessage(headline);
            boolean real = startCount > 1;
            for (UUID u : winners) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && real) games.addWin(id, p);
            }
            Set<UUID> all = new HashSet<>(players);
            all.addAll(winners);
            for (UUID u : all) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                p.showTitle(Title.title(headline, Component.empty()));
                if (winners.contains(u)) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            // kurz feiern lassen, dann zurück in den Hub
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID u : new HashSet<>(players)) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && games.isGameWorld(p.getWorld())) toHub(p);
                }
                players.clear();
                alive.clear();
                if (gw != null) restore(gw);
                state = State.WAITING;
            }, 100L);
        }

        /** Arena in den Ursprungszustand zurücksetzen. */
        void restore(World w) {
            for (var e : originals.entrySet()) {
                Pos p = e.getKey();
                w.getBlockAt(p.x(), p.y(), p.z()).setBlockData(e.getValue(), false);
            }
            originals.clear();
            for (Entity e : temp) if (e.isValid()) e.remove();
            temp.clear();
            for (Entity e : w.getNearbyEntities(new Location(w, cx, y, cz), radius + 5, 60, radius + 5)) {
                if (e instanceof Item || e instanceof Arrow || e instanceof TNTPrimed
                        || e instanceof org.bukkit.entity.Snowball || e instanceof org.bukkit.entity.EnderPearl) e.remove();
            }
            afterRestore(w);
            w.getPersistentDataContainer().remove(dirtyKey(this));
        }

        void afterRestore(World w) { }

        /** Arena-Bereich scheibchenweise leeren (schont den Server) und neu bauen. */
        void fullRebuild(World w, Runnable done) {
            if (rebuilding) return;
            rebuilding = true;
            originals.clear();
            int[] x = {cx - radius};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                for (int n = 0; n < 4 && x[0] <= cx + radius; n++, x[0]++) {
                    for (int z = cz - radius; z <= cz + radius; z++)
                        for (int yy = y - 30; yy <= y + 30; yy++) {
                            Block b = w.getBlockAt(x[0], yy, z);
                            if (!b.getType().isAir()) b.setType(Material.AIR, false);
                        }
                }
                if (x[0] > cx + radius) {
                    task.cancel();
                    buildArena(w);
                    afterRestore(w);
                    w.getPersistentDataContainer().remove(dirtyKey(this));
                    rebuilding = false;
                    plugin.getLogger().info(title + "-Arena neu gebaut");
                    if (done != null) done.run();
                }
            }, 1L, 1L);
        }

        void hit(Player victim, Player attacker) {
            lastHit.put(victim.getUniqueId(), attacker.getUniqueId());
            lastHitAt.put(victim.getUniqueId(), System.currentTimeMillis());
        }

        String deathText(Player p, Player killer, String cause) {
            if (killer != null) return p.getName() + " wurde von " + killer.getName() + (cause.equals("void") ? " ins Leere gestossen" : " besiegt");
            return p.getName() + (cause.equals("void") ? " ist ins Leere gefallen" : " ist gestorben");
        }

        boolean mayPlace(Player p, Block b) { return inBounds(b); }
        boolean mayBreak(Player p, Block b) { return inBounds(b); }
    }

    // ================================================================ BEDWARS

    enum Team {
        ROT("Rot", NamedTextColor.RED, Material.RED_WOOL, Material.RED_BED, Material.RED_CONCRETE, Material.RED_STAINED_GLASS, Color.fromRGB(0xB02E26), 0, -1),
        BLAU("Blau", NamedTextColor.BLUE, Material.BLUE_WOOL, Material.BLUE_BED, Material.BLUE_CONCRETE, Material.BLUE_STAINED_GLASS, Color.fromRGB(0x3C44AA), 1, 0),
        GRUEN("Grün", NamedTextColor.GREEN, Material.LIME_WOOL, Material.LIME_BED, Material.LIME_CONCRETE, Material.LIME_STAINED_GLASS, Color.fromRGB(0x80C71F), 0, 1),
        GELB("Gelb", NamedTextColor.YELLOW, Material.YELLOW_WOOL, Material.YELLOW_BED, Material.YELLOW_CONCRETE, Material.YELLOW_STAINED_GLASS, Color.fromRGB(0xFED83D), -1, 0);

        final String label; final NamedTextColor color; final Material wool, bed, concrete, glass; final Color leather; final int dx, dz;

        Team(String label, NamedTextColor color, Material wool, Material bed, Material concrete, Material glass, Color leather, int dx, int dz) {
            this.label = label; this.color = color; this.wool = wool; this.bed = bed; this.concrete = concrete; this.glass = glass;
            this.leather = leather; this.dx = dx; this.dz = dz;
        }
    }

    private record Offer(ItemStack icon, Material currency, int price, Consumer<Player> action) { }

    private static final class ShopMenu implements InventoryHolder {
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    final class Bedwars extends Match {
        static final int DIST = 42, ISLAND = 6;
        final Map<Team, Boolean> bedAlive = new EnumMap<>(Team.class);
        final Map<UUID, Team> teamOf = new HashMap<>();
        final Map<UUID, Integer> armorTier = new HashMap<>();
        final Set<Pos> placed = new HashSet<>();
        final Set<UUID> respawning = new HashSet<>();
        final List<TextDisplay> genLabels = new ArrayList<>();
        final List<Offer> offers = new ArrayList<>();
        boolean bedsGone;

        Bedwars() {
            super("bedwars", "Bedwars", NamedTextColor.RED, 700, 700, 80, 64);
        }

        @Override int minPlayers() { return 2; }
        @Override int maxPlayers() { return 8; }
        @Override Component kitTitle() { return Component.text("Schütze dein Bett – zerstöre die anderen!", NamedTextColor.WHITE); }

        int ix(Team t) { return cx + t.dx * DIST; }
        int iz(Team t) { return cz + t.dz * DIST; }
        Location spawn(World w, Team t) {
            Location l = new Location(w, ix(t) + 0.5, y + 1, iz(t) + 0.5);
            l.setDirection(new Vector(-t.dx, 0, -t.dz));
            return l;
        }
        /** Bett: Fussteil 3 Richtung Mitte, Kopfteil 4 Richtung Mitte. */
        Block bedFoot(World w, Team t) { return w.getBlockAt(ix(t) - t.dx * 3, y + 1, iz(t) - t.dz * 3); }
        Block bedHead(World w, Team t) { return w.getBlockAt(ix(t) - t.dx * 4, y + 1, iz(t) - t.dz * 4); }
        Location ironGen(World w, Team t) { return new Location(w, ix(t) + t.dx * 4 + 0.5, y + 1.2, iz(t) + t.dz * 4 + 0.5); }
        Location shopSpot(World w, Team t) {
            Location l = new Location(w, ix(t) + (-t.dz) * 4 + 0.5, y + 1, iz(t) + t.dx * 4 + 0.5);
            l.setDirection(new Vector(t.dz, 0, -t.dx));
            return l;
        }
        int[][] diamonds() { return new int[][]{{cx + 27, cz + 27}, {cx - 27, cz + 27}, {cx + 27, cz - 27}, {cx - 27, cz - 27}}; }
        int[][] emeralds() { return new int[][]{{cx + 2, cz}, {cx - 2, cz}}; }

        void disc(World w, int x0, int z0, int r, Material top, Material ring) {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.4) continue;
                w.getBlockAt(x0 + x, y, z0 + z).setType(d > r - 1 ? ring : top, false);
                for (int dy = 1; dy <= 4; dy++) {
                    if (d > r - dy * 1.3) break;
                    w.getBlockAt(x0 + x, y - dy, z0 + z).setType(dy == 1 ? Material.DIRT : Material.STONE, false);
                }
            }
        }

        void placeBed(World w, Team t) {
            BlockFace face = t.dx == 1 ? BlockFace.WEST : t.dx == -1 ? BlockFace.EAST : t.dz == 1 ? BlockFace.NORTH : BlockFace.SOUTH;
            Bed foot = (Bed) t.bed.createBlockData();
            foot.setPart(Bed.Part.FOOT);
            foot.setFacing(face);
            Bed head = (Bed) t.bed.createBlockData();
            head.setPart(Bed.Part.HEAD);
            head.setFacing(face);
            bedFoot(w, t).setBlockData(foot, false);
            bedHead(w, t).setBlockData(head, false);
        }

        @Override
        void buildArena(World w) {
            for (Team t : Team.values()) {
                disc(w, ix(t), iz(t), ISLAND, Material.SMOOTH_STONE, t.concrete);
                // Generator
                w.getBlockAt(ix(t) + t.dx * 4, y, iz(t) + t.dz * 4).setType(Material.IRON_BLOCK, false);
                // Bettpodest
                w.getBlockAt(ix(t) - t.dx * 3, y, iz(t) - t.dz * 3).setType(t.concrete, false);
                w.getBlockAt(ix(t) - t.dx * 4, y, iz(t) - t.dz * 4).setType(t.concrete, false);
                placeBed(w, t);
                // Laternen an den Ecken
                for (int[] o : new int[][]{{-ISLAND + 1, -ISLAND + 1}, {ISLAND - 1, -ISLAND + 1}, {-ISLAND + 1, ISLAND - 1}, {ISLAND - 1, ISLAND - 1}}) {
                    Block b = w.getBlockAt(ix(t) + o[0], y + 1, iz(t) + o[1]);
                    if (w.getBlockAt(ix(t) + o[0], y, iz(t) + o[1]).getType() != Material.AIR) b.setType(Material.LANTERN, false);
                }
            }
            for (int[] d : diamonds()) {
                disc(w, d[0], d[1], 3, Material.POLISHED_ANDESITE, Material.LIGHT_BLUE_CONCRETE);
                w.getBlockAt(d[0], y, d[1]).setType(Material.DIAMOND_BLOCK, false);
            }
            disc(w, cx, cz, 8, Material.POLISHED_DIORITE, Material.QUARTZ_BRICKS);
            for (int[] e : emeralds()) w.getBlockAt(e[0], y, e[1]).setType(Material.EMERALD_BLOCK, false);
            w.getBlockAt(cx, y, cz).setType(Material.SEA_LANTERN, false);
        }

        @Override
        void afterRestore(World w) {
            for (Team t : Team.values()) placeBed(w, t);
            placed.clear();
        }

        @Override
        void begin(World w, List<Player> list) {
            teamOf.clear();
            armorTier.clear();
            respawning.clear();
            bedAlive.clear();
            bedsGone = false;
            Collections.shuffle(list);
            int teams = Math.min(4, list.size());
            Team[] all = Team.values();
            for (int i = 0; i < list.size(); i++) teamOf.put(list.get(i).getUniqueId(), all[i % teams]);
            for (int i = 0; i < all.length; i++) {
                Team t = all[i];
                if (i < teams) bedAlive.put(t, true);
                else { // Team unbesetzt: Bett entfernen
                    record(bedFoot(w, t)); record(bedHead(w, t));
                    bedHead(w, t).setType(Material.AIR, false);
                    bedFoot(w, t).setType(Material.AIR, false);
                }
            }
            // Shops, Inselschilder und Generator-Anzeigen (nicht gespeichert, nur während der Runde)
            for (Team t : bedAlive.keySet()) {
                temp.add(w.spawn(shopSpot(w, t), Villager.class, v -> {
                    v.setAI(false);
                    v.setInvulnerable(true);
                    v.setSilent(true);
                    v.setCollidable(false);
                    v.setPersistent(false);
                    v.setRemoveWhenFarAway(false);
                    v.setProfession(Villager.Profession.ARMORER);
                    v.customName(Component.text("SHOP", NamedTextColor.GOLD, TextDecoration.BOLD));
                    v.setCustomNameVisible(true);
                    v.addScoreboardTag("bw_shop");
                }));
                temp.add(floating(w, new Location(w, ix(t) + 0.5, y + 4.5, iz(t) + 0.5),
                        Component.text("Team " + t.label, t.color, TextDecoration.BOLD), 2.4f));
            }
            genLabels.clear();
            for (int[] d : diamonds()) genLabels.add(floating(w, new Location(w, d[0] + 0.5, y + 3.2, d[1] + 0.5), Component.text("Diamant", NamedTextColor.AQUA), 1.6f));
            for (int[] e : emeralds()) genLabels.add(floating(w, new Location(w, e[0] + 0.5, y + 3.2, e[1] + 0.5), Component.text("Smaragd", NamedTextColor.GREEN), 1.6f));
            temp.addAll(genLabels);

            for (Player p : list) {
                Team t = teamOf.get(p.getUniqueId());
                p.teleport(spawn(w, t));
                kit(p);
                p.sendMessage(Component.text("  ▶ Team " + t.label, t.color, TextDecoration.BOLD)
                        .append(Component.text("  – Eisen kommt am Generator hinter dir, Diamanten/Smaragde in der Mitte. Rechtsklick auf den Händler = Shop.", NamedTextColor.GRAY)));
            }
        }

        void kit(Player p) {
            Team t = teamOf.get(p.getUniqueId());
            var inv = p.getInventory();
            inv.clear();
            inv.setHelmet(leather(Material.LEATHER_HELMET, t));
            inv.setChestplate(leather(Material.LEATHER_CHESTPLATE, t));
            int tier = armorTier.getOrDefault(p.getUniqueId(), 0);
            Material legs = switch (tier) { case 1 -> Material.CHAINMAIL_LEGGINGS; case 2 -> Material.IRON_LEGGINGS; case 3 -> Material.DIAMOND_LEGGINGS; default -> Material.LEATHER_LEGGINGS; };
            Material boots = switch (tier) { case 1 -> Material.CHAINMAIL_BOOTS; case 2 -> Material.IRON_BOOTS; case 3 -> Material.DIAMOND_BOOTS; default -> Material.LEATHER_BOOTS; };
            inv.setLeggings(tier == 0 ? leather(legs, t) : unbreakable(new ItemStack(legs)));
            inv.setBoots(tier == 0 ? leather(boots, t) : unbreakable(new ItemStack(boots)));
            inv.addItem(unbreakable(new ItemStack(Material.WOODEN_SWORD)));
        }

        ItemStack leather(Material m, Team t) {
            ItemStack it = new ItemStack(m);
            LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
            meta.setColor(t.leather);
            meta.setUnbreakable(true);
            it.setItemMeta(meta);
            return it;
        }

        @Override
        void tickRunning(World w) {
            // Generatoren
            for (Team t : bedAlive.keySet()) {
                if (!teamHasPlayers(t)) continue;
                drop(w, ironGen(w, t), Material.IRON_INGOT, 48);
                if (seconds % 6 == 0) drop(w, ironGen(w, t), Material.GOLD_INGOT, 16);
            }
            int dEvery = seconds > 600 ? 20 : 30, eEvery = seconds > 600 ? 40 : 60;
            int[][] ds = diamonds(), es = emeralds();
            for (int i = 0; i < ds.length; i++) {
                Location l = new Location(w, ds[i][0] + 0.5, y + 1.2, ds[i][1] + 0.5);
                if (seconds % dEvery == 0) drop(w, l, Material.DIAMOND, 4);
                updateLabel(i, Component.text("Diamant", NamedTextColor.AQUA, TextDecoration.BOLD).append(Component.newline())
                        .append(Component.text("in " + (dEvery - seconds % dEvery) + " s", NamedTextColor.WHITE)));
            }
            for (int i = 0; i < es.length; i++) {
                Location l = new Location(w, es[i][0] + 0.5, y + 1.2, es[i][1] + 0.5);
                if (seconds % eEvery == 0) drop(w, l, Material.EMERALD, 2);
                updateLabel(ds.length + i, Component.text("Smaragd", NamedTextColor.GREEN, TextDecoration.BOLD).append(Component.newline())
                        .append(Component.text("in " + (eEvery - seconds % eEvery) + " s", NamedTextColor.WHITE)));
            }
            if (seconds == 600) broadcast(Component.text("⚡ Diamanten und Smaragde kommen jetzt schneller!", NamedTextColor.AQUA));
            if (seconds == 1200 && !bedsGone) {
                bedsGone = true;
                for (Team t : bedAlive.keySet()) {
                    if (!bedAlive.get(t)) continue;
                    bedAlive.put(t, false);
                    record(bedFoot(w, t)); record(bedHead(w, t));
                    bedHead(w, t).setType(Material.AIR, false);
                    bedFoot(w, t).setType(Material.AIR, false);
                }
                for (UUID u : players) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) p.showTitle(Title.title(Component.text("Alle Betten zerstört!", NamedTextColor.RED, TextDecoration.BOLD), Component.text("Ab jetzt gibt es keinen Respawn mehr", NamedTextColor.WHITE)));
                }
            }
            if (seconds >= 1800) { finish(List.of(), Component.text("⌛ Bedwars: Zeit abgelaufen – unentschieden!", NamedTextColor.GOLD, TextDecoration.BOLD)); return; }
            // Anzeige
            Component bar = Component.empty();
            for (Team t : bedAlive.keySet()) {
                int n = alivePlayers(t);
                bar = bar.append(Component.text("■ " + t.label + " ", t.color, TextDecoration.BOLD))
                        .append(Component.text(bedAlive.get(t) ? "✔" : n > 0 ? "✖ " + n : "–", bedAlive.get(t) ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .append(Component.text(bedAlive.get(t) ? " " + n + "   " : "   ", NamedTextColor.GRAY));
            }
            bar = bar.append(Component.text(String.format("%d:%02d", seconds / 60, seconds % 60), NamedTextColor.WHITE));
            for (UUID u : players) { Player p = Bukkit.getPlayer(u); if (p != null && !respawning.contains(u)) p.sendActionBar(bar); }
        }

        void updateLabel(int i, Component c) {
            if (i >= genLabels.size()) return;
            TextDisplay t = genLabels.get(i);
            if (t.isValid()) t.text(c);
        }

        void drop(World w, Location l, Material m, int cap) {
            int have = 0;
            for (Item it : w.getNearbyEntitiesByType(Item.class, l, 2.0)) if (it.getItemStack().getType() == m) have += it.getItemStack().getAmount();
            if (have >= cap) return;
            Item it = w.dropItem(l, new ItemStack(m));
            it.setVelocity(new Vector());
        }

        boolean teamHasPlayers(Team t) {
            for (UUID u : alive) if (teamOf.get(u) == t) return true;
            return false;
        }

        int alivePlayers(Team t) {
            int n = 0;
            for (UUID u : alive) if (teamOf.get(u) == t) n++;
            return n;
        }

        @Override
        void checkWin() {
            if (state != State.RUNNING) return;
            Set<Team> left = new HashSet<>();
            for (UUID u : alive) left.add(teamOf.get(u));
            if (left.size() > 1) return;
            if (left.isEmpty()) { finish(List.of(), Component.text("Bedwars ist vorbei – niemand ist übrig.", NamedTextColor.GRAY)); return; }
            Team t = left.iterator().next();
            List<UUID> winners = new ArrayList<>();
            for (var e : teamOf.entrySet()) if (e.getValue() == t && Bukkit.getPlayer(e.getKey()) != null) winners.add(e.getKey());
            StringBuilder names = new StringBuilder();
            for (UUID u : winners) { Player p = Bukkit.getPlayer(u); if (p != null) names.append(names.isEmpty() ? "" : ", ").append(p.getName()); }
            finish(winners, Component.text("★ Team " + t.label + " gewinnt Bedwars! (" + names + ")", t.color, TextDecoration.BOLD));
        }

        @Override
        void onRemoved(UUID u) {
            respawning.remove(u);
        }

        @Override
        void die(Player p, Player killer, String cause) {
            UUID u = p.getUniqueId();
            if (respawning.contains(u) || !alive.contains(u)) return;
            Team t = teamOf.get(u);
            boolean fin = !bedAlive.getOrDefault(t, false);
            // Rohstoffe gehen an den Killer
            if (killer != null) {
                for (Material m : new Material[]{Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD}) {
                    int n = count(p, m);
                    if (n > 0) give(killer, new ItemStack(m, n));
                }
                killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
            broadcast(Component.text("☠ " + deathText(p, killer, cause), t.color)
                    .append(Component.text(fin ? "  FINALER KILL!" : "", NamedTextColor.AQUA, TextDecoration.BOLD)));
            lastHit.remove(u);
            if (fin) { eliminate(p, "Dein Bett war zerstört"); return; }
            games.resetPlayer(p);
            respawning.add(u);
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(new Location(p.getWorld(), cx + 0.5, y + 25, cz + 0.5, 0f, 60f));
            for (int i = 5; i >= 1; i--) {
                int s = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (respawning.contains(u)) p.showTitle(Title.title(Component.text("Gestorben!", NamedTextColor.RED),
                            Component.text("Respawn in " + s + " s", NamedTextColor.WHITE), Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                }, (5 - i) * 20L);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!respawning.remove(u) || state != State.RUNNING || !alive.contains(u) || !p.isOnline() || !games.isGameWorld(p.getWorld())) return;
                p.teleport(spawn(p.getWorld(), t));
                p.setGameMode(GameMode.SURVIVAL);
                games.resetPlayer(p);
                kit(p);
            }, 100L);
        }

        int count(Player p, Material m) {
            int n = 0;
            for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.getType() == m) n += it.getAmount();
            return n;
        }

        Team bedTeam(Block b) {
            for (Team t : bedAlive.keySet()) {
                if (bedFoot(b.getWorld(), t).equals(b) || bedHead(b.getWorld(), t).equals(b)) return t;
            }
            return null;
        }

        void onBedBreak(BlockBreakEvent e, Team t) {
            Player p = e.getPlayer();
            Team own = teamOf.get(p.getUniqueId());
            if (t == own) { e.setCancelled(true); msg(p, "Das ist dein eigenes Bett!", NamedTextColor.RED); return; }
            if (!bedAlive.getOrDefault(t, false)) { e.setCancelled(true); return; }
            World w = e.getBlock().getWorld();
            record(bedFoot(w, t)); record(bedHead(w, t));
            e.setCancelled(false);
            e.setDropItems(false);
            bedAlive.put(t, false);
            broadcast(Component.text("✦ Das Bett von Team " + t.label + " wurde von " + p.getName() + " zerstört!", t.color, TextDecoration.BOLD));
            for (UUID u : players) {
                Player o = Bukkit.getPlayer(u);
                if (o == null) continue;
                if (teamOf.get(u) == t) {
                    o.showTitle(Title.title(Component.text("Bett zerstört!", NamedTextColor.RED, TextDecoration.BOLD), Component.text("Du kannst nicht mehr respawnen", NamedTextColor.WHITE)));
                    o.playSound(o.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.7f, 1f);
                } else o.playSound(o.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                bedHead(w, t).setType(Material.AIR, false);
                bedFoot(w, t).setType(Material.AIR, false);
            });
        }

        @Override
        boolean mayPlace(Player p, Block b) {
            if (!inBounds(b) || b.getY() > y + 25) return false;
            // nicht direkt auf Generatoren bauen
            for (Team t : Team.values()) if (b.getX() == ix(t) + t.dx * 4 && b.getZ() == iz(t) + t.dz * 4 && b.getY() <= y + 2) return false;
            return true;
        }

        @Override
        boolean mayBreak(Player p, Block b) {
            return placed.contains(Pos.of(b));
        }

        // ------------------------------------------------------------ Shop

        void buildOffers() {
            if (!offers.isEmpty()) return;
            offers.add(new Offer(item(Material.WHITE_WOOL, 16, "16 Wolle (Teamfarbe)", NamedTextColor.WHITE), Material.IRON_INGOT, 4,
                    p -> give(p, new ItemStack(teamOf.get(p.getUniqueId()).wool, 16))));
            offers.add(new Offer(item(Material.END_STONE, 12, "12 Endstein", NamedTextColor.YELLOW), Material.IRON_INGOT, 24, p -> give(p, new ItemStack(Material.END_STONE, 12))));
            offers.add(new Offer(item(Material.OAK_PLANKS, 16, "16 Holz", NamedTextColor.GOLD), Material.GOLD_INGOT, 4, p -> give(p, new ItemStack(Material.OAK_PLANKS, 16))));
            offers.add(new Offer(item(Material.OBSIDIAN, 4, "4 Obsidian", NamedTextColor.DARK_PURPLE), Material.EMERALD, 4, p -> give(p, new ItemStack(Material.OBSIDIAN, 4))));
            offers.add(new Offer(item(Material.COOKED_BEEF, 4, "4 Steaks", NamedTextColor.GOLD), Material.IRON_INGOT, 4, p -> give(p, new ItemStack(Material.COOKED_BEEF, 4))));
            offers.add(new Offer(item(Material.GOLDEN_APPLE, 1, "Goldapfel", NamedTextColor.GOLD), Material.GOLD_INGOT, 3, p -> give(p, new ItemStack(Material.GOLDEN_APPLE))));
            offers.add(new Offer(item(Material.TNT, 1, "TNT (zündet beim Platzieren)", NamedTextColor.RED), Material.GOLD_INGOT, 4, p -> give(p, new ItemStack(Material.TNT))));

            offers.add(new Offer(item(Material.STONE_SWORD, 1, "Steinschwert", NamedTextColor.GRAY), Material.IRON_INGOT, 10, p -> sword(p, Material.STONE_SWORD)));
            offers.add(new Offer(item(Material.IRON_SWORD, 1, "Eisenschwert", NamedTextColor.WHITE), Material.GOLD_INGOT, 7, p -> sword(p, Material.IRON_SWORD)));
            offers.add(new Offer(item(Material.DIAMOND_SWORD, 1, "Diamantschwert", NamedTextColor.AQUA), Material.EMERALD, 4, p -> sword(p, Material.DIAMOND_SWORD)));
            ItemStack kb = item(Material.STICK, 1, "Rückstoss-Stock", NamedTextColor.LIGHT_PURPLE);
            kb.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
            offers.add(new Offer(kb, Material.GOLD_INGOT, 5, p -> give(p, kb.clone())));
            offers.add(new Offer(item(Material.BOW, 1, "Bogen", NamedTextColor.WHITE), Material.GOLD_INGOT, 12, p -> give(p, unbreakable(new ItemStack(Material.BOW)))));
            offers.add(new Offer(item(Material.ARROW, 8, "8 Pfeile", NamedTextColor.WHITE), Material.GOLD_INGOT, 2, p -> give(p, new ItemStack(Material.ARROW, 8))));
            offers.add(new Offer(item(Material.ENDER_PEARL, 1, "Enderperle", NamedTextColor.DARK_AQUA), Material.EMERALD, 4, p -> give(p, new ItemStack(Material.ENDER_PEARL))));

            offers.add(new Offer(item(Material.CHAINMAIL_BOOTS, 1, "Kettenrüstung (bleibt nach Tod)", NamedTextColor.GRAY), Material.IRON_INGOT, 24, p -> armor(p, 1)));
            offers.add(new Offer(item(Material.IRON_BOOTS, 1, "Eisenrüstung (bleibt nach Tod)", NamedTextColor.WHITE), Material.GOLD_INGOT, 12, p -> armor(p, 2)));
            offers.add(new Offer(item(Material.DIAMOND_BOOTS, 1, "Diamantrüstung (bleibt nach Tod)", NamedTextColor.AQUA), Material.EMERALD, 6, p -> armor(p, 3)));
            offers.add(new Offer(item(Material.SHEARS, 1, "Schere (schnell durch Wolle)", NamedTextColor.WHITE), Material.IRON_INGOT, 20, p -> give(p, unbreakable(new ItemStack(Material.SHEARS)))));
            offers.add(new Offer(item(Material.WOODEN_PICKAXE, 1, "Holzspitzhacke", NamedTextColor.GOLD), Material.IRON_INGOT, 10,
                    p -> give(p, unbreakable(ench(Material.WOODEN_PICKAXE, Enchantment.EFFICIENCY, 1)))));
            offers.add(new Offer(item(Material.IRON_PICKAXE, 1, "Eisenspitzhacke", NamedTextColor.WHITE), Material.GOLD_INGOT, 3,
                    p -> give(p, unbreakable(ench(Material.IRON_PICKAXE, Enchantment.EFFICIENCY, 2)))));
            offers.add(new Offer(item(Material.STONE_AXE, 1, "Axt (schnell durch Holz)", NamedTextColor.GOLD), Material.IRON_INGOT, 10,
                    p -> give(p, unbreakable(ench(Material.STONE_AXE, Enchantment.EFFICIENCY, 1)))));
        }

        void sword(Player p, Material m) {
            p.getInventory().remove(Material.WOODEN_SWORD);
            give(p, unbreakable(new ItemStack(m)));
        }

        boolean armor(Player p, int tier) {
            if (armorTier.getOrDefault(p.getUniqueId(), 0) >= tier) return false;
            armorTier.put(p.getUniqueId(), tier);
            Material legs = tier == 1 ? Material.CHAINMAIL_LEGGINGS : tier == 2 ? Material.IRON_LEGGINGS : Material.DIAMOND_LEGGINGS;
            Material boots = tier == 1 ? Material.CHAINMAIL_BOOTS : tier == 2 ? Material.IRON_BOOTS : Material.DIAMOND_BOOTS;
            p.getInventory().setLeggings(unbreakable(new ItemStack(legs)));
            p.getInventory().setBoots(unbreakable(new ItemStack(boots)));
            return true;
        }

        String currencyName(Material m) {
            return switch (m) { case IRON_INGOT -> "Eisen"; case GOLD_INGOT -> "Gold"; case DIAMOND -> "Diamant"; default -> "Smaragde"; };
        }

        NamedTextColor currencyColor(Material m) {
            return switch (m) { case IRON_INGOT -> NamedTextColor.WHITE; case GOLD_INGOT -> NamedTextColor.GOLD; case DIAMOND -> NamedTextColor.AQUA; default -> NamedTextColor.GREEN; };
        }

        void openShop(Player p) {
            buildOffers();
            ShopMenu h = new ShopMenu();
            Inventory inv = Bukkit.createInventory(h, 45, Component.text("Bedwars-Shop", NamedTextColor.DARK_RED));
            h.inv = inv;
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < offers.size() && i < slots.length; i++) {
                Offer o = offers.get(i);
                ItemStack icon = o.icon().clone();
                ItemMeta m = icon.getItemMeta();
                boolean can = count(p, o.currency()) >= o.price();
                m.lore(List.of(Component.text("Preis: " + o.price() + " " + currencyName(o.currency()), currencyColor(o.currency())).decoration(TextDecoration.ITALIC, false),
                        Component.text(can ? "▶ Klicken zum Kaufen" : "✖ Zu wenig " + currencyName(o.currency()), can ? NamedTextColor.YELLOW : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
                m.getPersistentDataContainer().set(shopKey, PersistentDataType.INTEGER, i);
                icon.setItemMeta(m);
                inv.setItem(slots[i], icon);
            }
            p.openInventory(inv);
        }

        void buy(Player p, int i) {
            if (i < 0 || i >= offers.size() || state != State.RUNNING || !alive.contains(p.getUniqueId())) return;
            Offer o = offers.get(i);
            if (count(p, o.currency()) < o.price()) {
                msg(p, "Du brauchst " + o.price() + " " + currencyName(o.currency()) + ".", NamedTextColor.RED);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            Integer tierBefore = armorTier.get(p.getUniqueId());
            o.action().accept(p);
            if (o.icon().getType().name().endsWith("_BOOTS") && java.util.Objects.equals(tierBefore, armorTier.get(p.getUniqueId()))) {
                msg(p, "Diese Rüstung (oder eine bessere) hast du schon.", NamedTextColor.YELLOW);
                return;
            }
            p.getInventory().removeItem(new ItemStack(o.currency(), o.price()));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            Bukkit.getScheduler().runTask(plugin, () -> { if (p.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu) openShop(p); });
        }
    }

    // ================================================================ SKYWARS

    final class Skywars extends Match {
        static final int RING = 28, ISLANDS = 8;
        final List<Block> cages = new ArrayList<>();

        Skywars() {
            super("skywars", "Skywars", NamedTextColor.AQUA, -700, 700, 80, 46);
        }

        @Override int minPlayers() { return 2; }
        @Override int maxPlayers() { return ISLANDS; }
        @Override Component kitTitle() { return Component.text("Plündere die Truhen – der Letzte gewinnt!", NamedTextColor.WHITE); }

        int[] island(int i) {
            double a = 2 * Math.PI * i / ISLANDS;
            return new int[]{cx + (int) Math.round(Math.cos(a) * RING), cz + (int) Math.round(Math.sin(a) * RING)};
        }

        void blob(World w, int x0, int z0, int r) {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.4) continue;
                w.getBlockAt(x0 + x, y, z0 + z).setType(Material.GRASS_BLOCK, false);
                for (int dy = 1; dy <= r + 2; dy++) {
                    if (d > r - dy * 0.9) break;
                    w.getBlockAt(x0 + x, y - dy, z0 + z).setType(dy <= 2 ? Material.DIRT : (x * 7 + z * 3 + dy) % 9 == 0 ? Material.IRON_ORE : Material.STONE, false);
                }
            }
        }

        List<Block> chests(World w) {
            List<Block> list = new ArrayList<>();
            for (int i = 0; i < ISLANDS; i++) {
                int[] c = island(i);
                int ox = Integer.signum(c[0] - cx), oz = Integer.signum(c[1] - cz);
                list.add(w.getBlockAt(c[0] + ox * 2, y + 1, c[1] + oz * 2));
            }
            for (int[] o : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) list.add(w.getBlockAt(cx + o[0], y + 1, cz + o[1]));
            return list;
        }

        @Override
        void buildArena(World w) {
            for (int i = 0; i < ISLANDS; i++) {
                int[] c = island(i);
                blob(w, c[0], c[1], 3);
            }
            blob(w, cx, cz, 7);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) w.getBlockAt(cx + x, y, cz + z).setType(Material.MOSSY_STONE_BRICKS, false);
            w.getBlockAt(cx, y, cz).setType(Material.SEA_LANTERN, false);
            for (Block b : chests(w)) b.setType(Material.CHEST, false);
        }

        @Override
        void afterRestore(World w) {
            for (Block b : chests(w)) if (b.getType() != Material.CHEST) b.setType(Material.CHEST, false);
            cages.clear();
        }

        Location spawn(World w, int i) {
            int[] c = island(i);
            Location l = new Location(w, c[0] + 0.5, y + 1, c[1] + 0.5);
            l.setDirection(new Vector(cx - c[0], 0, cz - c[1]));
            return l;
        }

        @Override
        void begin(World w, List<Player> list) {
            Random r = new Random();
            List<Block> cs = chests(w);
            for (int i = 0; i < cs.size(); i++) {
                Block b = cs.get(i);
                if (b.getType() != Material.CHEST) b.setType(Material.CHEST, false);
                if (b.getState(false) instanceof Container c) {
                    c.getInventory().clear();
                    List<ItemStack> loot = i < ISLANDS ? islandLoot(r) : centerLoot(r);
                    List<Integer> slots = new ArrayList<>();
                    for (int s = 0; s < c.getInventory().getSize(); s++) slots.add(s);
                    Collections.shuffle(slots, r);
                    for (int k = 0; k < loot.size() && k < slots.size(); k++) c.getInventory().setItem(slots.get(k), loot.get(k));
                }
            }
            // Spieler auf zufällige Inseln, zuerst in Glaskäfige
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < ISLANDS; i++) idx.add(i);
            Collections.shuffle(idx, r);
            cages.clear();
            for (int k = 0; k < list.size(); k++) {
                Location s = spawn(w, idx.get(k));
                cage(w, s);
                list.get(k).teleport(s);
            }
            for (int i = 5; i >= 1; i--) {
                int s = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (UUID u : alive) {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) { p.sendActionBar(Component.text("Käfige öffnen in " + s + " s", NamedTextColor.GOLD)); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f); }
                    }
                }, (5 - i) * 20L);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state != State.RUNNING) return;
                for (Block b : cages) if (b.getType() == Material.GLASS) b.setType(Material.AIR, false);
                cages.clear();
                for (UUID u : alive) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) { p.showTitle(Title.title(Component.text("LOS!", NamedTextColor.GREEN, TextDecoration.BOLD), Component.empty())); p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f); }
                }
            }, 100L);
        }

        void cage(World w, Location s) {
            int x0 = s.getBlockX(), y0 = s.getBlockY(), z0 = s.getBlockZ();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 3; dy++) {
                boolean wall = dx != 0 || dz != 0;
                if (!wall && dy != 3) continue;
                Block b = w.getBlockAt(x0 + dx, y0 + dy, z0 + dz);
                if (!b.getType().isAir()) continue;
                record(b);
                b.setType(Material.GLASS, false);
                cages.add(b);
            }
        }

        List<ItemStack> islandLoot(Random r) {
            List<ItemStack> l = new ArrayList<>();
            Material[] blocks = {Material.STONE, Material.OAK_PLANKS, Material.COBBLESTONE};
            l.add(new ItemStack(blocks[r.nextInt(3)], 24 + r.nextInt(9)));
            if (r.nextBoolean()) l.add(new ItemStack(blocks[r.nextInt(3)], 16));
            int s = r.nextInt(100);
            l.add(new ItemStack(s < 15 ? Material.WOODEN_SWORD : s < 75 ? Material.STONE_SWORD : Material.IRON_SWORD));
            Material[][] armor = {
                    {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
                    {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
                    {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS}};
            Set<Integer> pieces = new HashSet<>();
            while (pieces.size() < 2) pieces.add(r.nextInt(4));
            for (int pc : pieces) { int t = r.nextInt(10); l.add(new ItemStack(armor[t < 3 ? 0 : t < 7 ? 1 : 2][pc])); }
            l.add(r.nextBoolean() ? new ItemStack(Material.COOKED_BEEF, 6) : new ItemStack(Material.BREAD, 8));
            if (r.nextInt(100) < 50) l.add(new ItemStack(Material.SNOWBALL, 8));
            if (r.nextInt(100) < 40) { l.add(new ItemStack(Material.BOW)); l.add(new ItemStack(Material.ARROW, 8)); }
            if (r.nextInt(100) < 40) l.add(new ItemStack(Material.GOLDEN_APPLE));
            if (r.nextInt(100) < 35) l.add(new ItemStack(r.nextBoolean() ? Material.STONE_PICKAXE : Material.IRON_PICKAXE));
            if (r.nextInt(100) < 20) l.add(new ItemStack(Material.WATER_BUCKET));
            if (r.nextInt(100) < 15) l.add(new ItemStack(Material.ENDER_PEARL));
            if (r.nextInt(100) < 20) l.add(new ItemStack(Material.TNT, 2));
            return l;
        }

        List<ItemStack> centerLoot(Random r) {
            List<ItemStack> l = new ArrayList<>();
            l.add(r.nextInt(100) < 35 ? new ItemStack(Material.DIAMOND_SWORD) : ench(Material.IRON_SWORD, Enchantment.SHARPNESS, 1));
            Material[] dia = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
            l.add(new ItemStack(dia[r.nextInt(4)]));
            if (r.nextBoolean()) l.add(ench(Material.IRON_CHESTPLATE, Enchantment.PROTECTION, 1));
            if (r.nextBoolean()) { l.add(ench(Material.BOW, Enchantment.POWER, 1)); l.add(new ItemStack(Material.ARROW, 16)); }
            l.add(new ItemStack(Material.GOLDEN_APPLE, 1 + r.nextInt(2)));
            if (r.nextInt(100) < 40) l.add(new ItemStack(Material.ENDER_PEARL, 2));
            if (r.nextInt(100) < 50) l.add(new ItemStack(Material.TNT, 4));
            l.add(new ItemStack(Material.OAK_PLANKS, 32));
            if (r.nextInt(100) < 30) l.add(new ItemStack(Material.SNOWBALL, 16));
            return l;
        }

        @Override
        void tickRunning(World w) {
            if (seconds == 300) for (UUID u : alive) { Player p = Bukkit.getPlayer(u); if (p != null) msg(p, "Noch 5 Minuten! Danach endet die Runde unentschieden.", NamedTextColor.YELLOW); }
            if (seconds >= 600) { finish(List.of(), Component.text("⌛ Skywars: Zeit abgelaufen – unentschieden!", NamedTextColor.GOLD, TextDecoration.BOLD)); return; }
            int left = 600 - seconds;
            for (UUID u : alive) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) p.sendActionBar(Component.text("Skywars · noch " + alive.size() + " im Spiel · " + String.format("%d:%02d", left / 60, left % 60), NamedTextColor.AQUA));
            }
        }

        @Override
        void die(Player p, Player killer, String cause) {
            if (!alive.contains(p.getUniqueId())) return;
            // Inventar fallen lassen (ausser im Leeren)
            if (!cause.equals("void")) {
                Location l = p.getLocation();
                for (ItemStack it : p.getInventory().getContents()) if (it != null && !it.getType().isAir()) p.getWorld().dropItemNaturally(l, it);
            }
            broadcast(Component.text("☠ " + deathText(p, killer, cause) + " – noch " + (alive.size() - 1) + " übrig", NamedTextColor.GRAY));
            if (killer != null) killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            eliminate(p, killer != null ? "Besiegt von " + killer.getName() : null);
        }

        @Override
        void checkWin() {
            if (state != State.RUNNING || alive.size() > 1) return;
            if (alive.isEmpty()) { finish(List.of(), Component.text("Skywars ist vorbei.", NamedTextColor.GRAY)); return; }
            UUID u = alive.iterator().next();
            Player p = Bukkit.getPlayer(u);
            finish(List.of(u), Component.text("★ " + (p != null ? p.getName() : "?") + " gewinnt Skywars!", NamedTextColor.AQUA, TextDecoration.BOLD));
        }
    }

    // ================================================================ Events (von Minigames weitergereicht)

    /** @return true, wenn der Spieler in einem Bedwars/Skywars-Spiel ist und das Ereignis hier behandelt wurde. */
    boolean handleDamage(EntityDamageEvent e, Player p) {
        Match m = matchOf(p);
        if (m == null) return false;
        if (m.state != State.RUNNING || !m.alive.contains(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR
                || (m == skywars && !skywars.cages.isEmpty())) {
            e.setCancelled(true);
            return true;
        }
        Player attacker = null;
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            if (d instanceof Player a) attacker = a;
            else if (d instanceof Projectile pr && pr.getShooter() instanceof Player a2) attacker = a2;
            else if (d instanceof TNTPrimed t && t.getSource() instanceof Player a3) attacker = a3;
            if (attacker != null && attacker != p) {
                if (!m.players.contains(attacker.getUniqueId())) { e.setCancelled(true); return true; }
                if (m == bedwars && bedwars.teamOf.get(attacker.getUniqueId()) == bedwars.teamOf.get(p.getUniqueId())) { e.setCancelled(true); return true; }
                m.hit(p, attacker);
            }
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) { e.setCancelled(true); return true; }
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            Player killer = attacker != null && attacker != p ? attacker : m.killerOf(p);
            m.die(p, killer, "kill");
        }
        return true;
    }

    boolean handleMove(Player p, Location to) {
        Match m = matchOf(p);
        if (m == null) return false;
        if (p.getGameMode() == GameMode.SPECTATOR) {
            if (!m.inBounds(to) && to.getY() > m.loseY()) p.teleport(m.center(p.getWorld()).add(0, 20, 0));
            return true;
        }
        if (to.getY() < m.loseY() && m.state == State.RUNNING) m.die(p, m.killerOf(p), "void");
        else if (to.getY() < m.loseY()) { p.setFallDistance(0); p.teleport(m.center(p.getWorld())); }
        return true;
    }

    boolean handlePlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Match m = matchOf(p);
        if (m == null) return false;
        Block b = e.getBlockPlaced();
        if (m.state != State.RUNNING || !m.mayPlace(p, b)) { e.setCancelled(true); return true; }
        if (b.getType() == Material.TNT) {
            // TNT zündet sofort
            e.setCancelled(true);
            ItemStack hand = p.getInventory().getItem(e.getHand());
            if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1); else hand = null;
            p.getInventory().setItem(e.getHand(), hand);
            Location l = b.getLocation().add(0.5, 0, 0.5);
            b.getWorld().spawn(l, TNTPrimed.class, t -> { t.setFuseTicks(45); t.setSource(p); });
            return true;
        }
        e.setCancelled(false);
        m.originals.putIfAbsent(Pos.of(b), e.getBlockReplacedState().getBlockData().clone());
        if (m == bedwars) bedwars.placed.add(Pos.of(b));
        return true;
    }

    boolean handleBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Match m = matchOf(p);
        if (m == null) return false;
        Block b = e.getBlock();
        if (m.state != State.RUNNING) { e.setCancelled(true); return true; }
        if (m == bedwars && b.getBlockData() instanceof Bed) {
            Team t = bedwars.bedTeam(b);
            if (t != null) { bedwars.onBedBreak(e, t); return true; }
        }
        if (!m.mayBreak(p, b)) {
            e.setCancelled(true);
            if (m == bedwars) p.sendActionBar(Component.text("Du kannst nur Blöcke abbauen, die Spieler gesetzt haben.", NamedTextColor.GRAY));
            return true;
        }
        e.setCancelled(false);
        m.record(b);
        if (m == bedwars) { bedwars.placed.remove(Pos.of(b)); e.setDropItems(b.getType().name().endsWith("_WOOL") || b.getType() == Material.END_STONE || b.getType() == Material.OAK_PLANKS); }
        return true;
    }

    boolean handleInteract(PlayerInteractEvent e) {
        Match m = matchOf(e.getPlayer());
        if (m == null) return false;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getBlockData() instanceof Bed) {
            e.setUseInteractedBlock(Event.Result.DENY); // nicht schlafen, aber Blöcke daneben setzen geht
        }
        return true;
    }

    boolean handleDrop(Player p) {
        Match m = matchOf(p);
        return m != null && m.state == State.RUNNING && p.getGameMode() != GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShopClick(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains("bw_shop")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (matchOf(p) == bedwars && bedwars.state == State.RUNNING && p.getGameMode() != GameMode.SPECTATOR) bedwars.openShop(p);
    }

    @EventHandler
    public void onShopMenu(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ShopMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        Integer i = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(shopKey, PersistentDataType.INTEGER);
        if (i != null) bedwars.buy(p, i);
    }

    @EventHandler
    public void onShopDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ShopMenu) e.setCancelled(true);
    }

    private Match runningAt(Location l) {
        for (Match m : matches) if (m.state == State.RUNNING && m.inBounds(l)) return m;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!games.isGameWorld(e.getEntity().getWorld())) return;
        Match m = runningAt(e.getLocation());
        if (m == null) { e.blockList().clear(); return; }
        if (m == bedwars) {
            e.blockList().removeIf(b -> !bedwars.placed.contains(Pos.of(b)) || b.getType() == Material.OBSIDIAN || b.getType().name().endsWith("_GLASS"));
            e.setYield(0f);
            for (Block b : e.blockList()) { m.record(b); bedwars.placed.remove(Pos.of(b)); }
        } else {
            e.blockList().removeIf(b -> !m.inBounds(b) || b.getType() == Material.CHEST);
            for (Block b : e.blockList()) m.record(b);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!games.isGameWorld(e.getBlock().getWorld())) return;
        Match m = runningAt(e.getToBlock().getLocation());
        if (m == null) { e.setCancelled(true); return; }
        m.record(e.getToBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!games.isGameWorld(e.getPlayer().getWorld())) return;
        Match m = matchOf(e.getPlayer());
        if (m == null || m.state != State.RUNNING || !m.inBounds(e.getBlock())) { e.setCancelled(true); return; }
        m.record(e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!games.isGameWorld(e.getPlayer().getWorld())) return;
        Match m = matchOf(e.getPlayer());
        if (m == null || m.state != State.RUNNING || !m.inBounds(e.getBlock())) { e.setCancelled(true); return; }
        m.record(e.getBlock());
    }
}

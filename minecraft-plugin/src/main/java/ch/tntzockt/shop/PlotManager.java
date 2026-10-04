package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Grundstück-Welt: Raster aus Parzellen. Jeder Spieler kann eine Parzelle beanspruchen,
 * nur Besitzer und Vertraute dürfen dort bauen.
 */
final class PlotManager implements Listener, CommandExecutor, TabCompleter {

    record PlotId(int x, int z) {
        String key() { return x + ";" + z; }
        static PlotId parse(String s) {
            String[] p = s.split(";");
            return new PlotId(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
    }

    static final class Plot {
        final PlotId id;
        UUID owner;
        String ownerName;
        final List<UUID> trusted = new ArrayList<>();
        Plot(PlotId id) { this.id = id; }
    }

    private final TntShopPlugin plugin;
    private final Map<String, Plot> plots = new HashMap<>();
    private File dataFile;
    private String worldName = "grundstuecke";
    private int size = 64, road = 7, cell = 71, maxPerPlayer = 1;
    private final Map<UUID, Long> clearConfirm = new HashMap<>();

    PlotManager(TntShopPlugin plugin) {
        this.plugin = plugin;
    }

    void configure(String worldName, int size, int road, int maxPerPlayer) {
        this.worldName = worldName;
        this.size = size;
        this.road = road;
        this.cell = size + road;
        this.maxPerPlayer = Math.max(1, maxPerPlayer);
    }

    Generators.Plots generator() {
        return new Generators.Plots(size, road);
    }

    void load() {
        plots.clear();
        dataFile = new File(plugin.getDataFolder(), "plots.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = y.getConfigurationSection("plots");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                Plot p = new Plot(PlotId.parse(key));
                p.owner = UUID.fromString(sec.getString(key + ".owner", ""));
                p.ownerName = sec.getString(key + ".name", "?");
                for (String t : sec.getStringList(key + ".trusted")) p.trusted.add(UUID.fromString(t));
                plots.put(key, p);
            } catch (Exception e) {
                plugin.getLogger().warning("Ungültiges Grundstück in plots.yml: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Plot p : plots.values()) {
            String k = "plots." + p.id.key();
            y.set(k + ".owner", p.owner.toString());
            y.set(k + ".name", p.ownerName);
            y.set(k + ".trusted", p.trusted.stream().map(UUID::toString).toList());
        }
        try {
            y.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("plots.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- Geometrie

    boolean isPlotWorld(World w) {
        return w != null && w.getName().equals(worldName);
    }

    /** Parzelle an dieser Position oder null (Weg / andere Welt). */
    PlotId idAt(Location l) {
        if (l == null || !isPlotWorld(l.getWorld())) return null;
        int bx = l.getBlockX(), bz = l.getBlockZ();
        if (Math.floorMod(bx, cell) >= size || Math.floorMod(bz, cell) >= size) return null;
        return new PlotId(Math.floorDiv(bx, cell), Math.floorDiv(bz, cell));
    }

    private Location home(World w, PlotId id) {
        double x = id.x() * cell + size / 2.0;
        double z = id.z() * cell - road / 2.0;
        Location l = new Location(w, x, Generators.SURFACE + 1, z);
        l.setYaw(0); // Blick nach Süden, auf die Parzelle
        return l;
    }

    List<Plot> plotsOf(UUID player) {
        List<Plot> out = new ArrayList<>();
        for (Plot p : plots.values()) if (p.owner.equals(player)) out.add(p);
        return out;
    }

    Location homeOf(Player p) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        List<Plot> own = plotsOf(p.getUniqueId());
        return own.isEmpty() ? null : home(w, own.get(0).id);
    }

    private boolean canBuild(Player p, Location l) {
        if (!isPlotWorld(l.getWorld())) return true;
        if (p.hasPermission("tntshop.admin")) return true;
        PlotId id = idAt(l);
        if (id == null) return false;
        Plot plot = plots.get(id.key());
        return plot != null && (plot.owner.equals(p.getUniqueId()) || plot.trusted.contains(p.getUniqueId()));
    }

    private boolean samePlot(Location a, Location b) {
        PlotId x = idAt(a), y = idAt(b);
        return x != null && x.equals(y);
    }

    private void deny(Player p, Cancellable e) {
        e.setCancelled(true);
        p.sendActionBar(Component.text("Hier darfst du nicht bauen – hol dir mit /plot claim deine eigene Parzelle!", NamedTextColor.RED));
    }

    // ---------------------------------------------------------------- Schutz

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) deny(e.getPlayer(), e);
        else if (e instanceof BlockMultiPlaceEvent m) {
            for (BlockState s : m.getReplacedBlockStates()) {
                if (!canBuild(e.getPlayer(), s.getLocation())) { deny(e.getPlayer(), e); return; }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) deny(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (!isPlotWorld(e.getPlayer().getWorld())) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            Location target = e.getAction() == Action.RIGHT_CLICK_BLOCK ? b.getRelative(e.getBlockFace()).getLocation() : b.getLocation();
            if (!canBuild(e.getPlayer(), b.getLocation()) || !canBuild(e.getPlayer(), target)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) deny(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) deny(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (e.getPlayer() != null && !canBuild(e.getPlayer(), e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && !canBuild(e.getPlayer(), e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = asPlayer(e.getRemover());
        if (isPlotWorld(e.getEntity().getWorld()) && (p == null || !canBuild(p, e.getEntity().getLocation()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!isPlotWorld(e.getEntity().getWorld()) || e.getEntity() instanceof Player) return;
        Player p = asPlayer(e.getDamager());
        if (p == null || !canBuild(p, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (!canBuild(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!canBuild(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    private static Player asPlayer(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof org.bukkit.entity.Projectile pr) {
            ProjectileSource s = pr.getShooter();
            if (s instanceof Player p) return p;
        }
        return null;
    }

    // Wasser/Lava, Kolben, Bäume, Werfer dürfen nicht aus der Parzelle hinaus
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (isPlotWorld(e.getBlock().getWorld()) && !samePlot(e.getBlock().getLocation(), e.getToBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (isPlotWorld(e.getBlock().getWorld()) && !samePlot(e.getSource().getLocation(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!isPlotWorld(e.getBlock().getWorld())) return;
        Location base = e.getBlock().getLocation();
        if (!samePlot(base, e.getBlock().getRelative(e.getDirection()).getLocation())) { e.setCancelled(true); return; }
        for (Block b : e.getBlocks()) {
            if (!samePlot(base, b.getLocation()) || !samePlot(base, b.getRelative(e.getDirection()).getLocation())) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!isPlotWorld(e.getBlock().getWorld())) return;
        Location base = e.getBlock().getLocation();
        for (Block b : e.getBlocks()) {
            if (!samePlot(base, b.getLocation())) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        if (!isPlotWorld(e.getWorld())) return;
        Location base = e.getLocation();
        e.getBlocks().removeIf(s -> !samePlot(base, s.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (!isPlotWorld(e.getBlock().getWorld())) return;
        if (e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional d) {
            BlockFace f = d.getFacing();
            if (!samePlot(e.getBlock().getLocation(), e.getBlock().getRelative(f).getLocation())) e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- Befehle

    private static Component pre() {
        return Component.text("[Grundstück] ", NamedTextColor.GREEN);
    }

    private void msg(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(pre().append(Component.text(text, c)));
    }

    private PlotId nextFree() {
        // Spirale um 0;0
        int x = 0, z = 0, dx = 0, dz = -1;
        for (int i = 0; i < 1_000_000; i++) {
            if (!plots.containsKey(x + ";" + z)) return new PlotId(x, z);
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int t = dx; dx = -dz; dz = t;
            }
            x += dx; z += dz;
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur im Spiel verfügbar."); return true; }
        World w = Bukkit.getWorld(worldName);
        if (w == null) { msg(p, "Die Grundstück-Welt ist nicht geladen.", NamedTextColor.RED); return true; }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "claim", "auto", "holen" -> {
                List<Plot> own = plotsOf(p.getUniqueId());
                if (own.size() >= maxPerPlayer && !p.hasPermission("tntshop.admin")) {
                    msg(p, "Du hast schon eine Parzelle. /plot home bringt dich hin.", NamedTextColor.YELLOW);
                    return true;
                }
                PlotId id = nextFree();
                Plot plot = new Plot(id);
                plot.owner = p.getUniqueId();
                plot.ownerName = p.getName();
                plots.put(id.key(), plot);
                save();
                p.teleport(home(w, id));
                msg(p, "Parzelle " + id.key() + " gehört jetzt dir! Viel Spass beim Bauen.", NamedTextColor.GREEN);
                plugin.getLogger().info(p.getName() + " hat Parzelle " + id.key() + " beansprucht");
            }
            case "home", "h" -> {
                if (args.length >= 2) return visit(p, w, args[1]);
                Location h = homeOf(p);
                if (h == null) {
                    p.sendMessage(pre().append(Component.text("Du hast noch keine Parzelle. ", NamedTextColor.YELLOW))
                            .append(Component.text("[Jetzt holen]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/plot claim"))));
                } else {
                    p.teleport(h);
                }
            }
            case "visit", "besuchen", "tp" -> {
                if (args.length < 2) { msg(p, "/plot visit <spieler>", NamedTextColor.GRAY); return true; }
                return visit(p, w, args[1]);
            }
            case "info", "i" -> {
                PlotId id = idAt(p.getLocation());
                if (id == null) { msg(p, "Du stehst auf keiner Parzelle.", NamedTextColor.GRAY); return true; }
                Plot plot = plots.get(id.key());
                if (plot == null) {
                    msg(p, "Parzelle " + id.key() + " ist frei.", NamedTextColor.GREEN);
                } else {
                    List<String> names = new ArrayList<>();
                    for (UUID t : plot.trusted) { OfflinePlayer o = Bukkit.getOfflinePlayer(t); names.add(o.getName() == null ? "?" : o.getName()); }
                    msg(p, "Parzelle " + id.key() + " · Besitzer: " + plot.ownerName
                            + (names.isEmpty() ? "" : " · Darf mitbauen: " + String.join(", ", names)), NamedTextColor.WHITE);
                }
            }
            case "trust", "add" -> {
                if (args.length < 2) { msg(p, "/plot trust <spieler>", NamedTextColor.GRAY); return true; }
                Plot plot = ownPlotHereOrFirst(p);
                if (plot == null) { msg(p, "Du hast keine Parzelle.", NamedTextColor.RED); return true; }
                OfflinePlayer t = findPlayer(args[1]);
                if (t == null) { msg(p, "Spieler nicht gefunden (muss schon einmal online gewesen sein).", NamedTextColor.RED); return true; }
                if (!plot.trusted.contains(t.getUniqueId())) plot.trusted.add(t.getUniqueId());
                save();
                msg(p, t.getName() + " darf jetzt auf deiner Parzelle mitbauen.", NamedTextColor.GREEN);
            }
            case "untrust", "remove" -> {
                if (args.length < 2) { msg(p, "/plot untrust <spieler>", NamedTextColor.GRAY); return true; }
                Plot plot = ownPlotHereOrFirst(p);
                if (plot == null) { msg(p, "Du hast keine Parzelle.", NamedTextColor.RED); return true; }
                OfflinePlayer t = findPlayer(args[1]);
                if (t != null) plot.trusted.remove(t.getUniqueId());
                save();
                msg(p, "Erledigt.", NamedTextColor.GREEN);
            }
            case "clear", "leeren" -> {
                PlotId id = idAt(p.getLocation());
                Plot plot = id == null ? null : plots.get(id.key());
                boolean admin = p.hasPermission("tntshop.admin");
                if (plot == null || (!plot.owner.equals(p.getUniqueId()) && !admin)) {
                    msg(p, "Stell dich auf deine eigene Parzelle.", NamedTextColor.RED);
                    return true;
                }
                Long t = clearConfirm.get(p.getUniqueId());
                if (t == null || System.currentTimeMillis() - t > 20_000) {
                    clearConfirm.put(p.getUniqueId(), System.currentTimeMillis());
                    p.sendMessage(pre().append(Component.text("Wirklich ALLES auf Parzelle " + id.key() + " löschen? ", NamedTextColor.RED))
                            .append(Component.text("[Ja, leeren]", NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand("/plot clear"))));
                    return true;
                }
                clearConfirm.remove(p.getUniqueId());
                clear(w, id, p);
            }
            case "delete", "unclaim" -> {
                if (!p.hasPermission("tntshop.admin")) { msg(p, "Nur für Admins.", NamedTextColor.RED); return true; }
                PlotId id = idAt(p.getLocation());
                if (id == null || plots.remove(id.key()) == null) { msg(p, "Hier ist keine beanspruchte Parzelle.", NamedTextColor.GRAY); return true; }
                save();
                msg(p, "Parzelle " + id.key() + " ist wieder frei (Bauten bleiben – /plot clear zum Leeren vorher).", NamedTextColor.YELLOW);
            }
            default -> {
                msg(p, "Befehle:", NamedTextColor.WHITE);
                p.sendMessage(Component.text(" /plot claim – eigene Parzelle holen", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /plot home – zu deiner Parzelle", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /plot visit <spieler> – Parzelle besuchen", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /plot trust <spieler> – Freund mitbauen lassen", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /plot untrust <spieler> – Recht wieder entziehen", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /plot info – wem gehört diese Parzelle?", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /plot clear – eigene Parzelle komplett leeren", NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private boolean visit(Player p, World w, String name) {
        OfflinePlayer t = findPlayer(name);
        List<Plot> list = t == null ? List.of() : plotsOf(t.getUniqueId());
        if (list.isEmpty()) { msg(p, name + " hat keine Parzelle.", NamedTextColor.RED); return true; }
        p.teleport(home(w, list.get(0).id));
        msg(p, "Willkommen auf der Parzelle von " + list.get(0).ownerName + ".", NamedTextColor.GREEN);
        return true;
    }

    private Plot ownPlotHereOrFirst(Player p) {
        PlotId here = idAt(p.getLocation());
        if (here != null) {
            Plot plot = plots.get(here.key());
            if (plot != null && plot.owner.equals(p.getUniqueId())) return plot;
        }
        List<Plot> own = plotsOf(p.getUniqueId());
        return own.isEmpty() ? null : own.get(0);
    }

    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    /** Parzelle schrittweise zurücksetzen (eine Reihe pro Tick, damit der Server nicht ruckelt). */
    private void clear(World w, PlotId id, Player by) {
        int x0 = id.x() * cell, z0 = id.z() * cell;
        int min = w.getMinHeight(), max = w.getMaxHeight();
        for (Entity e : w.getNearbyEntities(new org.bukkit.util.BoundingBox(x0, min, z0, x0 + size, max, z0 + size))) {
            if (!(e instanceof Player)) e.remove();
        }
        msg(by, "Parzelle wird geleert …", NamedTextColor.YELLOW);
        final int[] row = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (row[0] >= size) {
                task.cancel();
                if (by.isOnline()) msg(by, "Parzelle " + id.key() + " ist wieder leer.", NamedTextColor.GREEN);
                return;
            }
            int x = x0 + row[0]++;
            for (int z = z0; z < z0 + size; z++) {
                for (int y = min + 1; y < max; y++) {
                    Material m = y < Generators.SURFACE - 3 ? Material.STONE
                            : y < Generators.SURFACE ? Material.DIRT
                            : y == Generators.SURFACE ? Material.GRASS_BLOCK : Material.AIR;
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != m) b.setType(m, false);
                }
            }
        }, 1L, 1L);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) return List.of("claim", "home", "visit", "trust", "untrust", "info", "clear");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}

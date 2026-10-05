package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
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
 * Skyblock: jeder Spieler bekommt eine eigene kleine Insel im Leeren.
 * Inseln liegen in einem Raster (Abstand 250 Blöcke); nur Besitzer und Freunde bauen dort.
 */
final class SkyblockManager implements Listener, CommandExecutor, TabCompleter {

    static final int SPACING = 250, Y = 100, PROTECT = 110;

    static final class Island {
        int index;
        UUID owner;
        String ownerName;
        final List<UUID> trusted = new ArrayList<>();
    }

    private final TntShopPlugin plugin;
    private final Map<Integer, Island> islands = new HashMap<>();
    private File dataFile;
    private int nextIndex = 0;
    private String worldName = "skyblock";
    private final Map<UUID, Long> resetConfirm = new HashMap<>();

    SkyblockManager(TntShopPlugin plugin) {
        this.plugin = plugin;
    }

    void configure(String worldName) {
        this.worldName = worldName;
    }

    boolean isSkyWorld(World w) {
        return w != null && w.getName().equals(worldName);
    }

    void load() {
        islands.clear();
        dataFile = new File(plugin.getDataFolder(), "skyblock.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        nextIndex = y.getInt("next-index", 0);
        ConfigurationSection sec = y.getConfigurationSection("islands");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                Island is = new Island();
                is.index = Integer.parseInt(k);
                is.owner = UUID.fromString(sec.getString(k + ".owner", ""));
                is.ownerName = sec.getString(k + ".name", "?");
                for (String t : sec.getStringList(k + ".trusted")) is.trusted.add(UUID.fromString(t));
                islands.put(is.index, is);
            } catch (Exception e) {
                plugin.getLogger().warning("Ungültige Insel in skyblock.yml: " + k);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-index", nextIndex);
        for (Island is : islands.values()) {
            String k = "islands." + is.index;
            y.set(k + ".owner", is.owner.toString());
            y.set(k + ".name", is.ownerName);
            y.set(k + ".trusted", is.trusted.stream().map(UUID::toString).toList());
        }
        try { y.save(dataFile); } catch (IOException e) { plugin.getLogger().severe("skyblock.yml: " + e.getMessage()); }
    }

    // ---------------------------------------------------------------- Geometrie

    /** Inselmitte für Index (Spirale um 0;0). */
    private static int[] center(int index) {
        int x = 0, z = 0, dx = 0, dz = -1;
        for (int i = 0; i < index; i++) {
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) { int t = dx; dx = -dz; dz = t; }
            x += dx; z += dz;
        }
        return new int[]{x * SPACING, z * SPACING};
    }

    private Island islandAt(Location l) {
        if (l == null || !isSkyWorld(l.getWorld())) return null;
        int gx = Math.round((float) l.getBlockX() / SPACING), gz = Math.round((float) l.getBlockZ() / SPACING);
        for (Island is : islands.values()) {
            int[] c = center(is.index);
            if (c[0] == gx * SPACING && c[1] == gz * SPACING
                    && Math.abs(l.getBlockX() - c[0]) <= PROTECT && Math.abs(l.getBlockZ() - c[1]) <= PROTECT) return is;
        }
        return null;
    }

    Island islandOf(UUID p) {
        for (Island is : islands.values()) if (is.owner.equals(p)) return is;
        return null;
    }

    Location home(World w, Island is) {
        int[] c = center(is.index);
        return new Location(w, c[0] + 0.5, Y + 1, c[1] + 0.5, 0f, 0f);
    }

    Location homeOf(Player p) {
        World w = Bukkit.getWorld(worldName);
        Island is = islandOf(p.getUniqueId());
        return w == null || is == null ? null : home(w, is);
    }

    private boolean canBuild(Player p, Location l) {
        if (!isSkyWorld(l.getWorld()) || p.hasPermission("tntshop.admin")) return true;
        Island is = islandAt(l);
        return is != null && (is.owner.equals(p.getUniqueId()) || is.trusted.contains(p.getUniqueId()));
    }

    // ---------------------------------------------------------------- Insel bauen

    private void buildIsland(World w, int index) {
        int[] c = center(index);
        int cx = c[0], cz = c[1];
        // Klassische L-Form: 6x3 + 3x3, drei Schichten hoch
        for (int x = -1; x <= 4; x++) {
            for (int z = -1; z <= 4; z++) {
                boolean l = (z <= 1) || (x <= 1);
                if (!l) continue;
                for (int dy = 0; dy < 3; dy++) {
                    Material m = dy == 0 ? Material.GRASS_BLOCK : Material.DIRT;
                    w.getBlockAt(cx + x, Y - dy, cz + z).setType(m, false);
                }
            }
        }
        w.getBlockAt(cx + 3, Y - 2, cz).setType(Material.BEDROCK, false);
        // Baum in der Ecke
        w.generateTree(new Location(w, cx - 1, Y + 1, cz + 4), TreeType.TREE);
        // Start-Truhe
        Block b = w.getBlockAt(cx + 4, Y + 1, cz);
        b.setType(Material.CHEST);
        if (b.getState() instanceof Chest chest) {
            chest.getBlockInventory().addItem(
                    new ItemStack(Material.LAVA_BUCKET), new ItemStack(Material.ICE, 2), new ItemStack(Material.MELON_SLICE),
                    new ItemStack(Material.CACTUS), new ItemStack(Material.SUGAR_CANE), new ItemStack(Material.PUMPKIN_SEEDS),
                    new ItemStack(Material.WHEAT_SEEDS, 2), new ItemStack(Material.BONE_MEAL, 3), new ItemStack(Material.BREAD, 4));
        }
        // Kleine Sand-Insel daneben
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            w.getBlockAt(cx + 30 + x, Y - 5, cz + z).setType(Material.SAND, false);
            w.getBlockAt(cx + 30 + x, Y - 6, cz + z).setType(Material.SANDSTONE, false);
        }
        w.getBlockAt(cx + 31, Y - 4, cz + 1).setType(Material.CACTUS, false);
    }

    // ---------------------------------------------------------------- Schutz

    private void deny(Player p, Cancellable e) {
        e.setCancelled(true);
        p.sendActionBar(Component.text("Das ist nicht deine Insel – /is bringt dich zu deiner eigenen.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) deny(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) deny(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) { if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) deny(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { if (!canBuild(e.getPlayer(), e.getBlock().getLocation())) deny(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || !isSkyWorld(e.getPlayer().getWorld())) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            if (!canBuild(e.getPlayer(), e.getClickedBlock().getLocation())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (!canBuild(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && !(e.getEntity() instanceof Player)
                && !(e.getEntity() instanceof org.bukkit.entity.Monster) && !canBuild(p, e.getEntity().getLocation())) e.setCancelled(true);
        if (e.getDamager() instanceof Player && e.getEntity() instanceof Player && isSkyWorld(e.getEntity().getWorld())) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- Befehle

    private void msg(CommandSender s, String t, NamedTextColor c) {
        s.sendMessage(Component.text("[Skyblock] ", NamedTextColor.AQUA).append(Component.text(t, c)));
    }

    /** Eigene Insel holen oder hinreisen. */
    void goHome(Player p) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) { msg(p, "Die Skyblock-Welt ist nicht geladen.", NamedTextColor.RED); return; }
        Island is = islandOf(p.getUniqueId());
        if (is == null) {
            is = new Island();
            is.index = nextIndex++;
            is.owner = p.getUniqueId();
            is.ownerName = p.getName();
            islands.put(is.index, is);
            save();
            buildIsland(w, is.index);
            msg(p, "Deine eigene Insel ist bereit! In der Truhe findest du deine Startausrüstung. Viel Glück!", NamedTextColor.GREEN);
        }
        p.teleport(home(w, is));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur im Spiel verfügbar."); return true; }
        String sub = args.length == 0 ? "home" : args[0].toLowerCase(Locale.ROOT);
        World w = Bukkit.getWorld(worldName);
        switch (sub) {
            case "home", "h", "go" -> goHome(p);
            case "visit", "besuchen" -> {
                if (args.length < 2 || w == null) { msg(p, "/is visit <spieler>", NamedTextColor.GRAY); return true; }
                OfflinePlayer t = find(args[1]);
                Island is = t == null ? null : islandOf(t.getUniqueId());
                if (is == null) { msg(p, args[1] + " hat keine Insel.", NamedTextColor.RED); return true; }
                p.teleport(home(w, is));
                msg(p, "Willkommen auf der Insel von " + is.ownerName + ".", NamedTextColor.GREEN);
            }
            case "trust", "add" -> {
                Island is = islandOf(p.getUniqueId());
                OfflinePlayer t = args.length < 2 ? null : find(args[1]);
                if (is == null || t == null) { msg(p, "/is trust <spieler> (du brauchst eine Insel)", NamedTextColor.GRAY); return true; }
                if (!is.trusted.contains(t.getUniqueId())) is.trusted.add(t.getUniqueId());
                save();
                msg(p, t.getName() + " darf jetzt auf deiner Insel mitspielen.", NamedTextColor.GREEN);
            }
            case "untrust", "remove" -> {
                Island is = islandOf(p.getUniqueId());
                OfflinePlayer t = args.length < 2 ? null : find(args[1]);
                if (is != null && t != null) is.trusted.remove(t.getUniqueId());
                save();
                msg(p, "Erledigt.", NamedTextColor.GREEN);
            }
            case "reset", "neu" -> {
                Island is = islandOf(p.getUniqueId());
                if (is == null) { msg(p, "Du hast noch keine Insel – /is", NamedTextColor.GRAY); return true; }
                Long t = resetConfirm.get(p.getUniqueId());
                if (t == null || System.currentTimeMillis() - t > 20_000) {
                    resetConfirm.put(p.getUniqueId(), System.currentTimeMillis());
                    p.sendMessage(Component.text("[Skyblock] ", NamedTextColor.AQUA)
                            .append(Component.text("Wirklich neu anfangen? Deine alte Insel ist dann weg. ", NamedTextColor.RED))
                            .append(Component.text("[Ja, neue Insel]", NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand("/is reset"))));
                    return true;
                }
                resetConfirm.remove(p.getUniqueId());
                islands.remove(is.index);
                save();
                p.getInventory().clear();
                goHome(p);
            }
            default -> {
                msg(p, "Befehle:", NamedTextColor.WHITE);
                p.sendMessage(Component.text(" /is – zu deiner Insel (erstellt sie beim ersten Mal)", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /is visit <spieler> – Insel besuchen", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /is trust <spieler> – Freund mitspielen lassen", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /is untrust <spieler> – Recht entziehen", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" /is reset – komplett neu anfangen", NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private static OfflinePlayer find(String name) {
        Player o = Bukkit.getPlayerExact(name);
        return o != null ? o : Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String a, String @NotNull [] args) {
        if (args.length == 1) return List.of("home", "visit", "trust", "untrust", "reset");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}

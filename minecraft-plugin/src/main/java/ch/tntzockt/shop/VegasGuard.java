package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Las Vegas: freies Bauen im Kreativmodus – ausser auf dem Strip und an den Casinos/Hotels.
 * Dazu Lift im STRAT und Casino-Atmosphäre (Geräusche, blinkende Automaten, Jackpot-Anzeige).
 */
final class VegasGuard implements Listener {

    static final int G = Generators.SURFACE;

    /** Geschützte Flächen (x1, z1, x2, z2), volle Höhe. */
    static final int[][] PROTECT = {
            {-16, -345, 15, 350},      // Las Vegas Boulevard
            {-116, 174, 121, 185},     // Tropicana Ave
            {-112, 238, -20, 300},     // Mandalay Bay
            {-93, 182, -16, 244},      // Luxor
            {-91, 124, -28, 176},      // Excalibur
            {-93, 70, -22, 116},       // New York-New York
            {-101, -28, -16, 70},      // Bellagio, See, Gewächshaus
            {-101, -95, -19, -29},     // Caesars Palace
            {-96, -166, -14, -104},    // The Mirage
            {16, 74, 93, 126},         // MGM Grand
            {16, -8, 95, 56},          // Paris
            {16, -76, 82, -2},         // High Roller
            {16, -157, 102, -77},      // The Venetian
            {100, -150, 176, -80},     // Sphere
            {16, -170, 111, -160},     // Zufahrt Sphere
            {28, -284, 92, -157},      // Wynn & Encore
            {16, -324, 60, -279},      // The STRAT
            {16, 126, 72, 174},        // Hotels Ostseite
            {16, 185, 77, 317},
            {-82, -332, -16, -173},    // Hotels Westseite
            {16, -278, 36, -260},
    };

    private final Plugin plugin;
    private final String world;
    private final Random rnd = new Random();
    private final List<List<int[]>> slots = new ArrayList<>();
    private final Map<Integer, TextDisplay> jackpotSigns = new HashMap<>();
    private final long[] jackpot = new long[VegasInteriors.HALLS.size()];
    private final Map<UUID, Long> liftCooldown = new HashMap<>();

    VegasGuard(Plugin plugin, String world) {
        this.plugin = plugin;
        this.world = world;
        for (int i = 0; i < jackpot.length; i++) jackpot[i] = 750_000 + rnd.nextInt(400_000);
    }

    static boolean isProtected(int x, int z) {
        for (int[] r : PROTECT) if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) return true;
        return false;
    }

    private boolean inWorld(World w) { return w != null && w.getName().equals(world); }

    private boolean guarded(Block b) { return inWorld(b.getWorld()) && isProtected(b.getX(), b.getZ()); }

    private static boolean admin(Player p) { return p.hasPermission("tntshop.admin"); }

    private void deny(Player p) {
        p.sendActionBar(Component.text("Der Strip und die Casinos sind geschützt – bau gerne daneben!", NamedTextColor.RED));
    }

    /** Automatenpositionen aus vegas.yml laden (beim Bauen gespeichert). */
    void load() {
        slots.clear();
        File f = new File(plugin.getDataFolder(), "vegas.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (int i = 0; i < VegasInteriors.HALLS.size(); i++) {
            List<int[]> l = new ArrayList<>();
            for (String s : y.getStringList("casinos." + i + ".slots")) {
                String[] p = s.split(",");
                if (p.length == 3) l.add(new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])});
            }
            slots.add(l);
        }
    }

    void start() {
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::ambient, 40L, 20L);
    }

    // ---------------------------------------------------------------- Schutz

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (guarded(e.getBlock()) && !admin(e.getPlayer())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (guarded(e.getBlock()) && !admin(e.getPlayer())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (guarded(e.getBlock()) && !admin(e.getPlayer())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (guarded(e.getBlock()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !inWorld(b.getWorld())) return;
        if (e.getAction() == Action.PHYSICAL && b.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) { lift(e.getPlayer(), b); return; }
        if (!isProtected(b.getX(), b.getZ()) || admin(e.getPlayer())) return;
        if (e.getAction() == Action.PHYSICAL) { e.setCancelled(true); return; }
        // Türen, Truhen, Knöpfe usw. am Strip nicht benutzen – Blöcke daneben setzen geht weiter
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) e.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (guarded(e.getToBlock()) && !guarded(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonOut(BlockPistonExtendEvent e) {
        if (!inWorld(e.getBlock().getWorld())) return;
        if (guarded(e.getBlock().getRelative(e.getDirection()))) { e.setCancelled(true); return; }
        for (Block b : e.getBlocks()) if (guarded(b) || guarded(b.getRelative(e.getDirection()))) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonIn(BlockPistonRetractEvent e) {
        if (!inWorld(e.getBlock().getWorld())) return;
        for (Block b : e.getBlocks()) if (guarded(b)) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityBlock(EntityChangeBlockEvent e) {
        if (guarded(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        if (!inWorld(e.getWorld())) return;
        e.getBlocks().removeIf((BlockState s) -> isProtected(s.getX(), s.getZ()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) { if (guarded(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) { if (guarded(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) { if (guarded(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (guarded(e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (inWorld(e.getBlock().getWorld())) e.blockList().removeIf(this::guarded);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (!inWorld(e.getBlock().getWorld())) return;
        if (e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional d && guarded(e.getBlock().getRelative(d.getFacing()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!inWorld(e.getEntity().getWorld()) || !isProtected(e.getEntity().getLocation().getBlockX(), e.getEntity().getLocation().getBlockZ())) return;
        if (!(e.getRemover() instanceof Player p) || !admin(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && guarded(e.getBlock()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurtEntity(EntityDamageByEntityEvent e) {
        Entity v = e.getEntity();
        if (!inWorld(v.getWorld()) || v instanceof Player) return;
        boolean npc = v.getScoreboardTags().contains(VegasInteriors.NPC_TAG);
        boolean prot = isProtected(v.getLocation().getBlockX(), v.getLocation().getBlockZ());
        if (!npc && !prot) return;
        if (e.getDamager() instanceof Player p && admin(p) && !npc) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTouchNpc(PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(VegasInteriors.NPC_TAG)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnEgg(CreatureSpawnEvent e) {
        if (!inWorld(e.getLocation().getWorld())) return;
        var r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (isProtected(e.getLocation().getBlockX(), e.getLocation().getBlockZ())) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- Lift im STRAT

    static Location liftTop(World w) {
        return new Location(w, VegasInteriors.LIFT_X + 0.5, VegasInteriors.LIFT_TOP_Y, VegasInteriors.LIFT_Z + 4.5, 0f, 0f);
    }

    static Location liftBottom(World w) {
        return new Location(w, VegasInteriors.LIFT_X - 3.5, G + 1, VegasInteriors.LIFT_Z + 0.5, 90f, 0f);
    }

    private void lift(Player p, Block b) {
        if (b.getX() != VegasInteriors.LIFT_X || b.getZ() != VegasInteriors.LIFT_Z) return;
        long now = System.currentTimeMillis();
        Long last = liftCooldown.get(p.getUniqueId());
        if (last != null && now - last < 2500) return;
        liftCooldown.put(p.getUniqueId(), now);
        boolean up = b.getY() < G + 50;
        Location to = up ? liftTop(b.getWorld()) : liftBottom(b.getWorld());
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.teleport(to);
            p.playSound(to, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 2f);
            if (up) p.showTitle(Title.title(Component.text("TOP OF THE WORLD", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("Restaurant im STRAT – geniess die Aussicht!", NamedTextColor.WHITE)));
            else p.sendActionBar(Component.text("▼ Zurück im STRAT-Casino", NamedTextColor.AQUA));
        }, 10L);
    }

    // ---------------------------------------------------------------- Casino-Atmosphäre

    private void ambient() {
        World w = Bukkit.getWorld(world);
        if (w == null || w.getPlayers().isEmpty()) return;
        boolean[] active = new boolean[VegasInteriors.HALLS.size()];
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            for (int i = 0; i < VegasInteriors.HALLS.size(); i++) {
                VegasInteriors.Hall h = VegasInteriors.HALLS.get(i);
                if (!h.contains(l.getX(), l.getY(), l.getZ())) continue;
                active[i] = true;
                // Geräusche: Glocken, Münzen, ab und zu Jubel
                int r = rnd.nextInt(10);
                Location s = l.clone().add(rnd.nextInt(13) - 6, 1, rnd.nextInt(13) - 6);
                if (r < 4) p.playSound(s, Sound.BLOCK_NOTE_BLOCK_BELL, 0.35f, 0.8f + rnd.nextFloat() * 1.2f);
                else if (r < 7) p.playSound(s, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 0.6f + rnd.nextFloat());
                else if (r < 8) p.playSound(s, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1f + rnd.nextFloat());
            }
        }
        for (int i = 0; i < active.length; i++) {
            jackpot[i] += 37 + rnd.nextInt(120);
            if (!active[i]) continue;
            VegasInteriors.Hall h = VegasInteriors.HALLS.get(i);
            // blinkende Automaten
            List<int[]> ls = i < slots.size() ? slots.get(i) : List.of();
            for (int k = 0; k < Math.min(6, ls.size()); k++) {
                int[] s = ls.get(rnd.nextInt(ls.size()));
                Block b = w.getBlockAt(s[0], s[1], s[2]);
                if (b.getType().name().endsWith("STAINED_GLASS")) b.setType(VegasInteriors.randomTopper(rnd), false);
            }
            // Jackpot-Anzeige
            TextDisplay t = jackpotSigns.get(i);
            if (t == null || !t.isValid()) {
                Location c = new Location(w, (h.x1() + h.x2()) / 2.0 + 0.5, G + h.h() - 1.6, (h.z1() + h.z2()) / 2.0 + 0.5);
                t = w.spawn(c, TextDisplay.class, d -> {
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setAlignment(TextDisplay.TextAlignment.CENTER);
                    d.setDefaultBackground(false);
                    d.setBackgroundColor(org.bukkit.Color.fromARGB(200, 20, 0, 30));
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                            new org.joml.Vector3f(1.3f, 1.3f, 1.3f), new org.joml.AxisAngle4f()));
                    d.setPersistent(false);
                });
                jackpotSigns.put(i, t);
            }
            // selten knackt ein Gast den Jackpot
            if (rnd.nextInt(900) == 0) {
                for (Player p : w.getPlayers()) if (h.contains(p.getX(), p.getY(), p.getZ())) {
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                    p.sendMessage(Component.text("✦ JACKPOT! Ein Gast im " + h.name() + " hat " + fmt(jackpot[i]) + " Chips gewonnen!", NamedTextColor.GOLD));
                }
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, t.getLocation(), 80, 2, 1, 2, 0.3);
                jackpot[i] = 500_000 + rnd.nextInt(200_000);
            }
            t.text(Component.text("★ JACKPOT ★", NamedTextColor.GOLD, TextDecoration.BOLD).append(Component.newline())
                    .append(Component.text(fmt(jackpot[i]) + " Chips", TextColor.color(rnd.nextBoolean() ? 0xFF4FD8 : 0xFFE24F), TextDecoration.BOLD)));
        }
        // Anzeigen ohne Zuschauer entfernen
        for (int i = 0; i < active.length; i++) {
            TextDisplay t = jackpotSigns.get(i);
            if (!active[i] && t != null && t.isValid() && rnd.nextInt(30) == 0) { t.remove(); jackpotSigns.remove(i); }
        }
    }

    private static String fmt(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n).replace(',', '\'');
    }
}

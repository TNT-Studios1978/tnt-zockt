package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Innenräume am Strip: begehbare Casinos (Automaten, Tische, Bar, Kasse – nur zum Anschauen),
 * das Restaurant oben im STRAT, das Bellagio-Gewächshaus und Besucher auf den Gehwegen.
 */
final class VegasInteriors {

    static final int G = Generators.SURFACE;
    static final String NPC_TAG = "tnt_vegas_npc";

    /** Farbschema eines Casinos. */
    record Theme(Material carpet, Material accent, Material wall, Material trim, Material ceiling, Material[] slotColors, int textColor) { }

    /** Casino-Saal: Grundriss inkl. Aussenwänden, Höhe bis zur Decke, Türen (Punkte auf der Wand). */
    record Hall(String name, int x1, int z1, int x2, int z2, int h, Theme t, int[][] doors, boolean walls, boolean ceiling, int[][] keepFree) {
        boolean contains(double x, double y, double z) {
            return x >= x1 && x <= x2 + 1 && z >= z1 && z <= z2 + 1 && y >= G && y <= G + h + 1;
        }
    }

    private static Material[] slots(Material... m) { return m; }

    static final List<Hall> HALLS = List.of(
            new Hall("Mandalay Bay", -45, 250, -26, 292, 7,
                    new Theme(Material.ORANGE_CARPET, Material.YELLOW_CARPET, Material.SMOOTH_SANDSTONE, Material.GOLD_BLOCK, Material.SMOOTH_SANDSTONE,
                            slots(Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.RED_TERRACOTTA), 0xF6C445),
                    new int[][]{{-26, 270}}, true, true, new int[0][]),
            new Hall("Luxor", -84, 191, -40, 235, 7,
                    new Theme(Material.BLUE_CARPET, Material.YELLOW_CARPET, Material.CUT_SANDSTONE, Material.GOLD_BLOCK, Material.SMOOTH_SANDSTONE,
                            slots(Material.BLUE_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.YELLOW_TERRACOTTA), 0x6AD1FF),
                    new int[][]{{-40, 200}}, true, true, new int[0][]),
            new Hall("Excalibur", -44, 131, -34, 171, 7,
                    new Theme(Material.RED_CARPET, Material.BLUE_CARPET, Material.WHITE_CONCRETE, Material.GOLD_BLOCK, Material.DARK_OAK_PLANKS,
                            slots(Material.RED_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA), 0xFF5555),
                    new int[][]{{-34, 151}}, true, true, new int[0][]),
            new Hall("New York-New York", -49, 86, -31, 99, 7,
                    new Theme(Material.RED_CARPET, Material.BLACK_CARPET, Material.BRICKS, Material.GOLD_BLOCK, Material.DARK_OAK_PLANKS,
                            slots(Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA, Material.WHITE_TERRACOTTA), 0xFFFFFF),
                    new int[][]{{-31, 96}}, true, true, new int[0][]),
            new Hall("MGM Grand", 36, 82, 58, 118, 7,
                    new Theme(Material.GREEN_CARPET, Material.BLACK_CARPET, Material.GREEN_TERRACOTTA, Material.GOLD_BLOCK, Material.SMOOTH_STONE,
                            slots(Material.GREEN_TERRACOTTA, Material.LIME_TERRACOTTA, Material.YELLOW_TERRACOTTA), 0x55FF55),
                    new int[][]{{36, 88}, {36, 112}}, true, true, new int[0][]),
            new Hall("Bellagio", -62, -18, -44, 31, 7,
                    new Theme(Material.LIGHT_GRAY_CARPET, Material.YELLOW_CARPET, Material.SMOOTH_SANDSTONE, Material.GOLD_BLOCK, Material.SMOOTH_QUARTZ,
                            slots(Material.WHITE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA), 0xF3E3B5),
                    new int[][]{{-44, 22}, {-53, 31}}, true, true, new int[0][]),
            new Hall("Paris", 58, 6, 90, 50, 7,
                    new Theme(Material.BLUE_CARPET, Material.YELLOW_CARPET, Material.SMOOTH_SANDSTONE, Material.GOLD_BLOCK, Material.SMOOTH_QUARTZ,
                            slots(Material.BLUE_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.RED_TERRACOTTA), 0xFF9F43),
                    new int[][]{{58, 28}}, true, true, new int[0][]),
            new Hall("Caesars Palace", -58, -91, -37, -63, 7,
                    new Theme(Material.PURPLE_CARPET, Material.YELLOW_CARPET, Material.QUARTZ_BRICKS, Material.GOLD_BLOCK, Material.SMOOTH_QUARTZ,
                            slots(Material.PURPLE_TERRACOTTA, Material.MAGENTA_TERRACOTTA, Material.YELLOW_TERRACOTTA), 0xE8E8FF),
                    new int[][]{{-37, -77}}, true, true, new int[0][]),
            new Hall("The Mirage", -52, -158, -42, -112, 7,
                    new Theme(Material.CYAN_CARPET, Material.LIME_CARPET, Material.WHITE_CONCRETE, Material.GOLD_BLOCK, Material.SMOOTH_STONE,
                            slots(Material.CYAN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.YELLOW_TERRACOTTA), 0xF6C445),
                    new int[][]{{-42, -151}}, true, true, new int[0][]),
            new Hall("The Venetian", 45, -121, 79, -89, 7,
                    new Theme(Material.RED_CARPET, Material.YELLOW_CARPET, Material.SMOOTH_SANDSTONE, Material.GOLD_BLOCK, Material.SMOOTH_QUARTZ,
                            slots(Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA), 0xFFD27F),
                    new int[][]{{45, -106}}, true, true, new int[0][]),
            new Hall("Wynn", 36, -236, 56, -172, 7,
                    new Theme(Material.RED_CARPET, Material.ORANGE_CARPET, Material.BROWN_TERRACOTTA, Material.GOLD_BLOCK, Material.SMOOTH_QUARTZ,
                            slots(Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.BROWN_TERRACOTTA), 0xE0B07A),
                    new int[][]{{36, -204}}, true, true, new int[0][]),
            new Hall("The STRAT", 20, -320, 56, -284, 8,
                    new Theme(Material.BLACK_CARPET, Material.MAGENTA_CARPET, Material.WHITE_CONCRETE, Material.IRON_BLOCK, Material.SMOOTH_STONE,
                            slots(Material.MAGENTA_TERRACOTTA, Material.BLACK_TERRACOTTA, Material.WHITE_TERRACOTTA), 0xFF5555),
                    new int[][]{{20, -302}}, true, true, new int[][]{{34, -306, 42, -298}}));

    // Lift im STRAT
    static final int LIFT_X = 38, LIFT_Z = -302, LIFT_TOP_Y = G + 166;

    private final World w;
    private final Random rnd = new Random(777);
    private final List<List<int[]>> slotTops = new ArrayList<>();

    VegasInteriors(World w) { this.w = w; }

    // ---------------------------------------------------------------- Bausteine

    private void set(int x, int y, int z, Material m) { w.getBlockAt(x, y, z).setType(m, false); }
    private void set(int x, int y, int z, BlockData d) { w.getBlockAt(x, y, z).setBlockData(d, false); }
    private void set(int x, int y, int z, String d) { w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(d), false); }

    private static final BlockData LIGHT = Bukkit.createBlockData("minecraft:light[level=15]");

    private void light(int x, int y, int z) { if (w.getBlockAt(x, y, z).getType().isAir()) set(x, y, z, LIGHT); }

    private static final Villager.Type[] TYPES = {Villager.Type.PLAINS, Villager.Type.DESERT, Villager.Type.SAVANNA, Villager.Type.TAIGA,
            Villager.Type.SNOW, Villager.Type.JUNGLE, Villager.Type.SWAMP};
    private static final Villager.Profession[] GUEST = {Villager.Profession.NONE, Villager.Profession.NITWIT, Villager.Profession.FARMER,
            Villager.Profession.FISHERMAN, Villager.Profession.SHEPHERD, Villager.Profession.FLETCHER, Villager.Profession.CARTOGRAPHER,
            Villager.Profession.MASON, Villager.Profession.CLERIC, Villager.Profession.LEATHERWORKER};

    /** Statische Figur: schaut in eine Richtung; einige drehen den Kopf zu Besuchern. */
    void npc(double x, double y, double z, double lookX, double lookZ, Villager.Profession prof, boolean aware, String name) {
        if (w.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).getType().isSolid()) return;
        Location l = new Location(w, x, y, z);
        l.setDirection(new org.bukkit.util.Vector(lookX - x, 0, lookZ - z));
        w.spawn(l, Villager.class, v -> {
            v.setVillagerType(TYPES[rnd.nextInt(TYPES.length)]);
            v.setProfession(prof != null ? prof : GUEST[rnd.nextInt(GUEST.length)]);
            v.setVillagerLevel(5); // keine Berufswechsel
            v.setInvulnerable(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setSilent(rnd.nextInt(4) != 0);
            v.setAware(aware);
            if (aware) {
                var sp = v.getAttribute(Attribute.MOVEMENT_SPEED);
                if (sp != null) sp.setBaseValue(0);
            } else v.setAI(false);
            if (name != null) { v.customName(Component.text(name, NamedTextColor.GOLD)); v.setCustomNameVisible(false); }
            v.addScoreboardTag(NPC_TAG);
        });
    }

    private void sign(double x, double y, double z, Component c, float scale) {
        w.spawn(new Location(w, x, y, z), TextDisplay.class, t -> {
            t.text(c);
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(160, 8, 6, 20));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            t.addScoreboardTag(VegasStrip.TAG);
            t.setPersistent(true);
        });
    }

    // ---------------------------------------------------------------- Casino-Saal

    /** Lokale Koordinaten: u = lange Achse, v = kurze Achse. */
    private final class Frame {
        final boolean alongX;
        Frame(Hall h) { alongX = (h.x2() - h.x1()) >= (h.z2() - h.z1()); }
        int x(int u, int v) { return alongX ? u : v; }
        int z(int u, int v) { return alongX ? v : u; }
        void put(int u, int v, int y, Material m) { set(x(u, v), y, z(u, v), m); }
        void put(int u, int v, int y, String d) { set(x(u, v), y, z(u, v), d); }
        void guest(double u, double v, double lu, double lv, Villager.Profession p, boolean aware) {
            npc((alongX ? u : v) + 0.5, G + 1, (alongX ? v : u) + 0.5, (alongX ? lu : lv) + 0.5, (alongX ? lv : lu) + 0.5, p, aware, null);
        }
        void label(double u, double v, double y, Component c, float s) {
            sign((alongX ? u : v) + 0.5, y, (alongX ? v : u) + 0.5, c, s);
        }
    }

    void casino(Hall h, int index) {
        Theme t = h.t();
        Frame f = new Frame(h);
        int u1 = f.alongX ? h.x1() : h.z1(), u2 = f.alongX ? h.x2() : h.z2();
        int v1 = f.alongX ? h.z1() : h.x1(), v2 = f.alongX ? h.z2() : h.x2();
        int top = G + h.h();
        // leeren, Boden, Wände, Decke
        for (int x = h.x1(); x <= h.x2(); x++) for (int z = h.z1(); z <= h.z2(); z++) {
            boolean wall = x == h.x1() || x == h.x2() || z == h.z1() || z == h.z2();
            set(x, G, z, wall ? t.wall() : Material.POLISHED_ANDESITE);
            for (int y = G + 1; y < top; y++) {
                if (wall && h.walls()) set(x, y, z, y == top - 1 ? t.trim() : t.wall());
                else if (!wall) set(x, y, z, Material.AIR);
            }
            if (h.ceiling()) set(x, top, z, wall ? t.trim() : t.ceiling());
            if (!wall) {
                // Teppichmuster
                int a = Math.floorMod(x, 6) - 3, b = Math.floorMod(z, 6) - 3;
                set(x, G + 1, z, Math.abs(a) + Math.abs(b) == 3 ? t.accent() : t.carpet());
            }
        }
        // Deckenlichter & Kronleuchter
        if (h.ceiling()) for (int x = h.x1() + 3; x < h.x2() - 1; x += 5) for (int z = h.z1() + 3; z < h.z2() - 1; z += 5) {
            set(x, top, z, (x + z) % 2 == 0 ? Material.GLOWSTONE : Material.PEARLESCENT_FROGLIGHT);
            if ((x / 5 + z / 5) % 3 == 0) {
                set(x, top - 1, z, "minecraft:iron_chain[axis=y]");
                set(x, top - 2, z, "minecraft:lantern[hanging=true]");
            }
        }
        for (int x = h.x1() + 2; x < h.x2() - 1; x += 4) for (int z = h.z1() + 2; z < h.z2() - 1; z += 4) light(x, G + 4, z);
        // Türen
        for (int[] d : h.doors()) door(h, d[0], d[1]);

        // Aufteilung in Inseln (7 lang, 6 breit), mit Gängen dazwischen und einem Mittelgang in breiten Sälen
        int minor = v2 - v1 + 1;
        int vc = (v1 + v2) / 2;
        boolean mainAisle = minor >= 17;
        List<int[]> free = new ArrayList<>();
        for (int[] k : h.keepFree()) free.add(k);
        for (int[] d : h.doors()) {
            boolean onX = d[0] == h.x1() || d[0] == h.x2();
            int in = onX ? (d[0] == h.x1() ? 1 : -1) : (d[1] == h.z1() ? 1 : -1);
            if (onX) free.add(new int[]{Math.min(d[0], d[0] + in * 4), d[1] - 2, Math.max(d[0], d[0] + in * 4), d[1] + 2});
            else free.add(new int[]{d[0] - 2, Math.min(d[1], d[1] + in * 4), d[0] + 2, Math.max(d[1], d[1] + in * 4)});
        }
        List<int[]> tops = new ArrayList<>();
        int pod = 0;
        boolean barDone = false, cashDone = false;
        for (int sv = v1 + 2; sv + 5 <= v2 - 2; sv += 7) {
            if (mainAisle && sv <= vc + 1 && sv + 5 >= vc - 1) { sv = vc + 2 - 7; continue; }
            for (int cu = u1 + 2; cu + 6 <= u2 - 2; cu += 8) {
                if (blocked(f, cu, sv, cu + 6, sv + 5, free)) continue;
                String type;
                if (!barDone) { type = "BAR"; barDone = true; }
                else if (pod == 1) type = "SLOTS";
                else if (!cashDone && pod >= 2) { type = "KASSE"; cashDone = true; }
                else {
                    int r = rnd.nextInt(10);
                    type = r < 4 ? "SLOTS" : r < 6 ? "BLACKJACK" : r < 8 ? "ROULETTE" : r < 9 ? "POKER" : "CRAPS";
                }
                switch (type) {
                    case "BAR" -> bar(f, cu, sv, 6, t);
                    case "KASSE" -> cashier(f, cu, sv, 6, t);
                    case "SLOTS" -> slotBank(f, cu, sv, 6, t, tops);
                    case "BLACKJACK" -> blackjack(f, cu, sv, 6);
                    case "ROULETTE" -> roulette(f, cu, sv, 6);
                    case "POKER" -> poker(f, cu, sv, 6);
                    default -> craps(f, cu, sv, 6);
                }
                pod++;
            }
        }
        slotTops.add(tops);
        // Saalname über dem Eingang innen
        int[] d0 = h.doors()[0];
        double cx = (h.x1() + h.x2()) / 2.0, cz = (h.z1() + h.z2()) / 2.0;
        double lx = d0[0] + Math.signum(cx - d0[0]) * 3, lz = d0[1] + Math.signum(cz - d0[1]) * 3;
        sign(lx + 0.5, G + 5.2, lz + 0.5, Component.text(h.name().toUpperCase(), TextColor.color(t.textColor()), TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("Casino", NamedTextColor.WHITE)), 1.6f);
    }

    private boolean blocked(Frame f, int ua, int va, int ub, int vb, List<int[]> free) {
        int xa = f.x(ua, va), za = f.z(ua, va), xb = f.x(ub, vb), zb = f.z(ub, vb);
        int x1 = Math.min(xa, xb), x2 = Math.max(xa, xb), z1 = Math.min(za, zb), z2 = Math.max(za, zb);
        for (int[] r : free) if (x1 <= r[2] && x2 >= r[0] && z1 <= r[3] && z2 >= r[1]) return true;
        return false;
    }

    private void door(Hall h, int x, int z) {
        boolean onX = x == h.x1() || x == h.x2(); // Tür in einer Wand, die entlang z läuft
        for (int i = -2; i <= 2; i++) {
            int bx = onX ? x : x + i, bz = onX ? z + i : z;
            for (int y = G + 1; y <= G + 4; y++) set(bx, y, bz, Material.AIR);
            set(bx, G, bz, Material.RED_CONCRETE);
            set(bx, G + 5, bz, Math.abs(i) == 2 ? h.t().trim() : Material.OCHRE_FROGLIGHT);
        }
        // roter Teppich nach draussen
        int out = onX ? (x == h.x1() ? -1 : 1) : (z == h.z1() ? -1 : 1);
        for (int k = 1; k <= 3; k++) for (int i = -1; i <= 1; i++) {
            int bx = onX ? x + out * k : x + i, bz = onX ? z + i : z + out * k;
            if (w.getBlockAt(bx, G + 1, bz).getType().isAir()) set(bx, G + 1, bz, Material.RED_CARPET);
        }
    }

    // ---------------------------------------------------------------- Einrichtung

    private Material screen() {
        int r = rnd.nextInt(4);
        return r == 0 ? Material.SEA_LANTERN : r == 1 ? Material.PEARLESCENT_FROGLIGHT : r == 2 ? Material.OCHRE_FROGLIGHT : Material.VERDANT_FROGLIGHT;
    }

    private static final Material[] TOPPER = {Material.RED_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS};

    static Material randomTopper(Random r) { return TOPPER[r.nextInt(TOPPER.length)]; }

    private void slotBank(Frame f, int cu, int sv, int width, Theme t, List<int[]> tops) {
        int a = sv + width / 2 - 1, b = a + 1;
        for (int u = cu; u <= cu + 6; u++) {
            for (int v : new int[]{a, b}) {
                Material cab = t.slotColors()[rnd.nextInt(t.slotColors().length)];
                f.put(u, v, G + 1, cab);
                f.put(u, v, G + 2, screen());
                f.put(u, v, G + 3, randomTopper(rnd));
                tops.add(new int[]{f.x(u, v), G + 3, f.z(u, v)});
            }
            // Hocker vor den Automaten, manchmal ein Gast
            for (int side : new int[]{a - 1, b + 1}) {
                if (side < sv || side > sv + width - 1) continue;
                if (rnd.nextInt(3) == 0) f.guest(u, side, u, side == a - 1 ? a : b, null, rnd.nextInt(3) == 0);
                else if (u % 2 == 0) f.put(u, side, G + 1, Material.DARK_OAK_FENCE);
            }
        }
        f.label(cu + 3.5, a + 0.5, G + 4.6, Component.text("SLOTS", NamedTextColor.YELLOW, TextDecoration.BOLD), 0.9f);
    }

    private void felt(Frame f, int u, int v) {
        f.put(u, v, G + 1, Material.DARK_OAK_PLANKS);
        f.put(u, v, G + 2, Material.GREEN_CARPET);
    }

    private void blackjack(Frame f, int cu, int sv, int width) {
        int cu0 = cu + 3, cv = sv + 1;
        for (int du = -3; du <= 3; du++) for (int dv = 0; dv <= 2; dv++)
            if (du * du + dv * dv * 2.2 <= 10) felt(f, cu0 + du, cv + dv);
        for (int du : new int[]{-2, 0, 2}) f.put(cu0 + du, cv + 1, G + 2, Material.WHITE_CARPET); // Karten
        f.guest(cu0, cv - 1, cu0, cv + 2, Villager.Profession.LIBRARIAN, true); // Dealer
        int[][] seats = {{-3, 2}, {-2, 3}, {0, 4}, {2, 3}, {3, 2}};
        for (int[] s : seats) {
            if (cv + s[1] > sv + width - 1) continue;
            if (rnd.nextBoolean()) f.guest(cu0 + s[0], cv + s[1], cu0, cv, null, false);
            else f.put(cu0 + s[0], cv + s[1], G + 1, Material.DARK_OAK_FENCE);
        }
        f.label(cu0, cv + 1, G + 4.2, Component.text("Blackjack", NamedTextColor.WHITE, TextDecoration.BOLD), 0.8f);
    }

    private void roulette(Frame f, int cu, int sv, int width) {
        int cv = sv + width / 2;
        for (int u = cu; u <= cu + 5; u++) for (int v = cv - 1; v <= cv + 1; v++) {
            f.put(u, v, G + 1, Material.DARK_OAK_PLANKS);
            if (u <= cu + 1) f.put(u, v, G + 2, (u == cu && v == cv) || (u == cu + 1 && v == cv) ? Material.GOLD_BLOCK
                    : ((u + v) % 2 == 0 ? Material.RED_CARPET : Material.BLACK_CARPET));
            else f.put(u, v, G + 2, (u + v) % 3 == 0 ? Material.RED_CARPET : (u + v) % 3 == 1 ? Material.BLACK_CARPET : Material.GREEN_CARPET);
        }
        f.guest(cu, cv - 2, cu, cv, Villager.Profession.LIBRARIAN, true);
        for (int u = cu + 2; u <= cu + 5; u++) {
            if (rnd.nextInt(3) == 0) f.guest(u, cv + 2, u, cv, null, false);
            if (rnd.nextInt(4) == 0 && cv - 2 >= sv) f.guest(u, cv - 2, u, cv, null, false);
        }
        f.label(cu + 3.5, cv, G + 4.2, Component.text("Roulette", NamedTextColor.RED, TextDecoration.BOLD), 0.8f);
    }

    private void poker(Frame f, int cu, int sv, int width) {
        int cv = sv + width / 2;
        for (int u = cu + 1; u <= cu + 5; u++) for (int v = cv - 1; v <= cv + 1; v++) {
            if ((u == cu + 1 || u == cu + 5) && v != cv) continue;
            felt(f, u, v);
        }
        f.put(cu + 3, cv, G + 2, Material.WHITE_CARPET);
        f.put(cu + 2, cv, G + 2, Material.RED_CARPET);
        f.put(cu + 4, cv, G + 2, Material.BLUE_CARPET);
        f.guest(cu, cv, cu + 3, cv, Villager.Profession.LIBRARIAN, true);
        int[][] seats = {{2, -2}, {4, -2}, {2, 2}, {4, 2}, {6, 0}, {3, -2}, {3, 2}};
        for (int[] s : seats) {
            if (cv + s[1] < sv || cv + s[1] > sv + width - 1) continue;
            if (rnd.nextInt(5) < 3) f.guest(cu + s[0], cv + s[1], cu + 3, cv, null, false);
            else f.put(cu + s[0], cv + s[1], G + 1, Material.DARK_OAK_FENCE);
        }
        f.label(cu + 3, cv, G + 4.2, Component.text("Poker", NamedTextColor.AQUA, TextDecoration.BOLD), 0.8f);
    }

    private void craps(Frame f, int cu, int sv, int width) {
        int cv = sv + width / 2;
        for (int u = cu; u <= cu + 6; u++) for (int v = cv - 1; v <= cv + 1; v++) {
            boolean rim = u == cu || u == cu + 6 || v == cv - 1 || v == cv + 1;
            f.put(u, v, G + 1, Material.DARK_OAK_PLANKS);
            f.put(u, v, G + 2, rim ? "minecraft:dark_oak_slab[type=bottom]" : (u == cu + 3 ? "minecraft:white_carpet" : "minecraft:green_carpet"));
        }
        f.guest(cu + 3, cv - 2, cu + 3, cv, Villager.Profession.LIBRARIAN, true);
        f.guest(cu + 5, cv - 2, cu + 5, cv, Villager.Profession.LIBRARIAN, false);
        for (int u = cu + 1; u <= cu + 5; u++) if (rnd.nextBoolean() && cv + 2 < sv + width) f.guest(u, cv + 2, u, cv, null, rnd.nextInt(4) == 0);
        f.label(cu + 3.5, cv, G + 4.2, Component.text("Craps", NamedTextColor.GREEN, TextDecoration.BOLD), 0.8f);
    }

    private void bar(Frame f, int cu, int sv, int width, Theme t) {
        int cv = sv + Math.min(width - 1, 2);
        // Rückwand mit Flaschenregal
        for (int u = cu; u <= cu + 6; u++) for (int y = G + 1; y <= G + 4; y++)
            f.put(u, sv, y, y == G + 1 ? Material.DARK_OAK_PLANKS : (u + y) % 3 == 0 ? Material.OCHRE_FROGLIGHT
                    : TOPPER[Math.floorMod(u * 7 + y, TOPPER.length)]);
        for (int u = cu; u <= cu + 6; u++) {
            f.put(u, cv, G + 1, Material.POLISHED_BLACKSTONE);
            f.put(u, cv, G + 2, "minecraft:smooth_quartz_slab[type=bottom]");
            if (cv + 1 <= sv + width - 1) {
                if (rnd.nextInt(3) == 0) f.guest(u, cv + 1, u, cv, null, rnd.nextBoolean());
                else if (u % 2 == 1) f.put(u, cv + 1, G + 1, Material.DARK_OAK_FENCE);
            }
        }
        f.guest(cu + 3, cv - 1, cu + 3, cv + 2, Villager.Profession.BUTCHER, true);
        f.label(cu + 3.5, cv, G + 4.4, Component.text("BAR", t.textColor() == 0xFFFFFF ? NamedTextColor.GOLD : TextColor.color(t.textColor()), TextDecoration.BOLD), 1.0f);
    }

    private void cashier(Frame f, int cu, int sv, int width, Theme t) {
        int cv = sv + Math.min(width - 1, 2);
        for (int u = cu; u <= cu + 6; u++) {
            f.put(u, cv, G + 1, Material.POLISHED_BLACKSTONE_BRICKS);
            f.put(u, cv, G + 2, Material.IRON_BARS);
            f.put(u, cv, G + 3, Material.IRON_BARS);
            f.put(u, cv, G + 4, t.trim());
            for (int v = sv; v < cv; v++) f.put(u, v, G + 1, Material.SMOOTH_STONE);
        }
        f.guest(cu + 2, cv - 1, cu + 2, cv + 1, Villager.Profession.LIBRARIAN, true);
        f.guest(cu + 5, cv - 1, cu + 5, cv + 1, Villager.Profession.CARTOGRAPHER, false);
        if (cv + 1 <= sv + width - 1) f.guest(cu + 4, cv + 1, cu + 4, cv, null, false);
        f.label(cu + 3.5, cv, G + 5.2, Component.text("KASSE", NamedTextColor.GOLD, TextDecoration.BOLD), 1.0f);
    }

    // ---------------------------------------------------------------- Bellagio-Gewächshaus

    void conservatory() {
        int x1 = -62, x2 = -44, z1 = 31, z2 = 64, top = G + 9;
        Material[] flowers = {Material.POPPY, Material.DANDELION, Material.AZURE_BLUET, Material.ALLIUM, Material.RED_TULIP,
                Material.ORANGE_TULIP, Material.PINK_TULIP, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.LILY_OF_THE_VALLEY};
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
            for (int y = G + 1; y < top; y++) set(x, y, z, wall && z != z1 ? (y < G + 3 ? Material.SMOOTH_SANDSTONE : Material.GLASS) : wall ? Material.SMOOTH_SANDSTONE : Material.AIR);
            set(x, top, z, wall ? Material.SMOOTH_SANDSTONE : ((x + z) % 4 == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.GLASS));
            if (wall) continue;
            boolean path = x == -53 || x == -52 || x == -54 || z == 47 || z == 48;
            if (path) { set(x, G, z, Material.SMOOTH_QUARTZ); continue; }
            set(x, G, z, Material.GRASS_BLOCK);
            int r = rnd.nextInt(10);
            if (r < 7) set(x, G + 1, z, flowers[rnd.nextInt(flowers.length)]);
            else if (r < 8) set(x, G + 1, z, rnd.nextBoolean() ? Material.FLOWERING_AZALEA : Material.AZALEA);
        }
        // Brunnen in der Mitte
        int fx = -53, fz = 47;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
            set(fx + x, G, fz + z, Material.SMOOTH_QUARTZ);
            set(fx + x, G + 1, fz + z, rim ? Material.QUARTZ_SLAB : Material.WATER);
        }
        set(fx, G + 1, fz, Material.QUARTZ_PILLAR);
        set(fx, G + 2, fz, Material.QUARTZ_PILLAR);
        set(fx, G + 3, fz, Material.GOLD_BLOCK);
        for (int x = x1 + 2; x < x2; x += 4) for (int z = z1 + 2; z < z2; z += 4) light(x, G + 5, z);
        // Durchgang zum Casino
        for (int x = -55; x <= -51; x++) for (int y = G + 1; y <= G + 4; y++) set(x, y, z1, Material.AIR);
        for (int i = 0; i < 8; i++) npc(-53.5 + (rnd.nextInt(5) - 2), G + 1, 36.5 + rnd.nextInt(24), -53, 47, null, rnd.nextInt(3) == 0, null);
        sign(-52.5, G + 6.5, 45.5, Component.text("Bellagio Conservatory", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("Botanischer Garten", NamedTextColor.WHITE)), 1.4f);
        // Brücke über den See zum Eingang
        for (int x = -43; x >= -41; x--) for (int z = 20; z <= 24; z++) set(x, G, z, Material.SMOOTH_QUARTZ);
        for (int x = -40; x <= -18; x++) for (int z = 20; z <= 24; z++) {
            set(x, G, z, Material.SMOOTH_QUARTZ);
            if (z == 20 || z == 24) set(x, G + 1, z, x % 4 == 0 ? Material.LANTERN : Material.DIORITE_WALL);
            else set(x, G + 1, z, Material.AIR);
        }
        set(-17, G + 1, 21, Material.AIR); set(-17, G + 1, 22, Material.AIR); set(-17, G + 1, 23, Material.AIR);
    }

    // ---------------------------------------------------------------- Zugänge

    /** Eingangstunnel in die Luxor-Pyramide. */
    void luxorEntrance() {
        for (int x = -39; x <= -30; x++) for (int z = 197; z <= 203; z++) for (int y = G; y <= G + 6; y++) {
            boolean side = z == 197 || z == 203, roof = y == G + 6;
            if (y == G) set(x, y, z, Material.SMOOTH_SANDSTONE);
            else if (side || roof) set(x, y, z, roof && x % 3 == 0 ? Material.OCHRE_FROGLIGHT : Material.CHISELED_SANDSTONE);
            else set(x, y, z, Material.AIR);
        }
        for (int z = 198; z <= 202; z++) set(-30, G + 6, z, Material.GOLD_BLOCK);
        for (int x = -39; x <= -31; x++) set(x, G + 1, 200, Material.BLUE_CARPET);
    }

    /** Durchgang Dogenpalast → Venetian-Casino, mit Boden im Palast. */
    void venetianLobby() {
        for (int x = 33; x <= 43; x++) for (int z = -127; z <= -93; z++) {
            set(x, G, z, (x + z) % 2 == 0 ? Material.WHITE_TERRACOTTA : Material.RED_TERRACOTTA);
            for (int y = G + 1; y <= G + 13; y++) if (!w.getBlockAt(x, y, z).getType().isAir() && x > 33) set(x, y, z, Material.AIR);
        }
        for (int x = 33; x <= 43; x += 5) for (int z = -125; z <= -95; z += 5) light(x, G + 6, z);
        for (int x = 44; x <= 45; x++) for (int z = -108; z <= -104; z++) for (int y = G + 1; y <= G + 4; y++) set(x, y, z, Material.AIR);
        for (int x = 33; x <= 45; x++) for (int z = -107; z <= -105; z++) set(x, G + 1, z, Material.RED_CARPET);
        for (int i = 0; i < 6; i++) npc(35.5 + rnd.nextInt(8), G + 1, -122.5 + rnd.nextInt(28), 31, -110, null, rnd.nextBoolean(), null);
        sign(38.5, G + 7.5, -106.5, Component.text("The Venetian", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("Grand Canal Shoppes ▸ Casino", NamedTextColor.WHITE)), 1.3f);
    }

    // ---------------------------------------------------------------- STRAT: Lift & Restaurant

    void stratLift() {
        // Liftkabine im Casino (Glas, offen nach Westen)
        int x = LIFT_X, z = LIFT_Z;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = G + 1; y <= G + 5; y++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            boolean opening = dx == -2 && Math.abs(dz) <= 1 && y <= G + 3;
            set(x + dx, y, z + dz, edge && !opening ? (y == G + 5 ? Material.IRON_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS) : Material.AIR);
        }
        set(x, G + 1, z, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        set(x, G + 5, z, Material.SEA_LANTERN);
        sign(x - 2.5 + 0.5, G + 4.6, z + 0.5, Component.text("▲ LIFT", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("Top of the World – Restaurant", NamedTextColor.WHITE)), 1.0f);
        // Kabine oben
        int ty = LIFT_TOP_Y;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = ty; y <= ty + 4; y++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            boolean opening = dz == 2 && Math.abs(dx) <= 1 && y <= ty + 2;
            set(x + dx, y, z + dz, edge && !opening ? (y == ty + 4 ? Material.IRON_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS) : Material.AIR);
        }
        set(x, ty, z, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        sign(x + 0.5, ty + 3.6, z + 3.0, Component.text("▼ LIFT", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("zurück ins Casino", NamedTextColor.WHITE)), 0.9f);
    }

    void restaurant() {
        int cx = LIFT_X, cz = LIFT_Z, y = LIFT_TOP_Y;
        // Teppich
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 12.2 || d < 3.0) continue;
            set(cx + x, y, cz + z, d > 11 ? Material.BLACK_CARPET : ((int) d % 3 == 0 ? Material.RED_CARPET : Material.GRAY_CARPET));
        }
        // runde Bar um den Lift
        for (int a = 0; a < 360; a += 8) {
            int x = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * 4), z = cz + (int) Math.round(Math.sin(Math.toRadians(a)) * 4);
            if (z > cz + 2 && Math.abs(x - cx) <= 1) continue; // Lift-Ausgang frei
            set(x, y, z, Material.POLISHED_BLACKSTONE);
            set(x, y + 1, z, "minecraft:smooth_quartz_slab[type=bottom]");
        }
        // Tische am Fenster
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(i * 36 + 18);
            int tx = cx + (int) Math.round(Math.cos(a) * 9), tz = cz + (int) Math.round(Math.sin(a) * 9);
            set(tx, y, tz, Material.DARK_OAK_FENCE);
            set(tx, y + 1, tz, Material.WHITE_CARPET);
            int ox = (int) Math.round(-Math.sin(a) * 1.4), oz = (int) Math.round(Math.cos(a) * 1.4);
            if (rnd.nextInt(4) != 0) npc(tx + ox + 0.5, y, tz + oz + 0.5, tx + 0.5, tz + 0.5, null, rnd.nextInt(3) == 0, null);
            else set(tx + ox, y, tz + oz, "minecraft:spruce_stairs[facing=north]");
            if (rnd.nextBoolean()) npc(tx - ox + 0.5, y, tz - oz + 0.5, tx + 0.5, tz + 0.5, null, false, null);
            else set(tx - ox, y, tz - oz, "minecraft:spruce_stairs[facing=south]");
            set(tx, y + 2, tz, "minecraft:candle[candles=3,lit=true]");
        }
        // Kellner & Barkeeper
        npc(cx + 6.5, y, cz + 0.5, cx + 9, cz, Villager.Profession.BUTCHER, true, "Kellner");
        npc(cx - 6.5, y, cz - 2.5, cx - 9, cz - 3, Villager.Profession.BUTCHER, true, "Kellnerin");
        npc(cx + 2.5, y, cz - 2.5, cx + 5, cz - 5, Villager.Profession.CLERIC, true, "Barkeeper");
        for (int x = -10; x <= 10; x += 5) for (int z = -10; z <= 10; z += 5) light(cx + x, y + 4, cz + z);
        sign(cx + 0.5, y + 4.5, cz + 6.5, Component.text("TOP OF THE WORLD", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("Restaurant · 260 Blöcke über dem Strip", NamedTextColor.WHITE)), 1.2f);
    }

    // ---------------------------------------------------------------- Strip-Besucher

    void crowds() {
        for (int z = -330; z <= 330; z += 11) {
            for (int side = -1; side <= 1; side += 2) {
                if (rnd.nextInt(3) == 0) continue;
                int n = 1 + rnd.nextInt(3);
                double bx = side < 0 ? -14 : 13;
                for (int i = 0; i < n; i++) {
                    double x = bx + 0.5 + (rnd.nextInt(3) - 1) * 0.8, zz = z + 0.5 + i * 1.1;
                    npc(x, G + 1, zz, x + (rnd.nextBoolean() ? 3 : -3), zz + (rnd.nextInt(5) - 2), null, rnd.nextInt(4) == 0, null);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Bauen & Daten

    void build(List<Runnable> steps, Plugin plugin) {
        for (int i = 0; i < HALLS.size(); i++) { int k = i; steps.add(() -> casino(HALLS.get(k), k)); }
        steps.add(this::luxorEntrance);
        steps.add(this::conservatory);
        steps.add(this::venetianLobby);
        steps.add(this::stratLift);
        steps.add(this::restaurant);
        steps.add(this::crowds);
        steps.add(() -> save(plugin));
    }

    private void save(Plugin plugin) {
        YamlConfiguration y = new YamlConfiguration();
        for (int i = 0; i < slotTops.size(); i++) {
            List<String> l = new ArrayList<>();
            for (int[] p : slotTops.get(i)) l.add(p[0] + "," + p[1] + "," + p[2]);
            y.set("casinos." + i + ".slots", l);
        }
        try { y.save(new File(plugin.getDataFolder(), "vegas.yml")); } catch (IOException e) { plugin.getLogger().warning("vegas.yml: " + e.getMessage()); }
    }
}

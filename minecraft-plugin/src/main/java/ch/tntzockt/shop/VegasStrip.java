package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Las Vegas Strip: Boulevard von Süd (Welcome-Schild) nach Nord (The STRAT) mit den
 * bekanntesten Bauten im Minecraft-Stil. Ewige Nacht, damit die Lichter wirken.
 * Westseite = negative x, Süden = positive z.
 */
final class VegasStrip {

    static final int G = Generators.SURFACE;
    static final String TAG = "tnt_vegas";

    interface H { int h(int x, int z); }

    private final World w;
    private final Random rnd = new Random(1955);
    private static final BlockData LIGHT = Bukkit.createBlockData("minecraft:light[level=15]");
    private static final BlockData LANTERN = Bukkit.createBlockData("minecraft:lantern[hanging=false]");
    private static final BlockData PALM_LEAF = Bukkit.createBlockData("minecraft:jungle_leaves[persistent=true]");

    VegasStrip(World w) { this.w = w; }

    // ---------------------------------------------------------------- Orte für /vegas <ort>

    record Place(String key, String name, double x, double z, float yaw) { }

    static final List<Place> PLACES = List.of(
            new Place("schild", "Welcome-Schild", 0.5, 338.5, 180f),
            new Place("mandalay", "Mandalay Bay", -9.5, 270.5, 90f),
            new Place("luxor", "Luxor", -9.5, 213.5, 90f),
            new Place("excalibur", "Excalibur", -9.5, 150.5, 90f),
            new Place("newyork", "New York-New York", -9.5, 92.5, 90f),
            new Place("mgm", "MGM Grand", 9.5, 100.5, -90f),
            new Place("bellagio", "Bellagio", -9.5, 20.5, 90f),
            new Place("paris", "Paris", 9.5, 28.5, -90f),
            new Place("highroller", "High Roller", 9.5, -32.5, -90f),
            new Place("caesars", "Caesars Palace", -9.5, -60.5, 90f),
            new Place("mirage", "The Mirage", -9.5, -132.5, 90f),
            new Place("venetian", "The Venetian", 9.5, -110.5, -90f),
            new Place("sphere", "Sphere", 95.5, -115.5, -90f),
            new Place("wynn", "Wynn & Encore", 9.5, -215.5, -90f),
            new Place("strat", "The STRAT", 9.5, -300.5, -90f));

    static Location spawn(World w) {
        return new Location(w, 0.5, G + 1, 338.5, 180f, 0f);
    }

    static Location place(World w, Place p) {
        return new Location(w, p.x(), G + 1, p.z(), p.yaw(), -10f);
    }

    // ---------------------------------------------------------------- Grundbausteine

    private void set(int x, int y, int z, Material m) { w.getBlockAt(x, y, z).setType(m, false); }
    private void set(int x, int y, int z, BlockData d) { w.getBlockAt(x, y, z).setBlockData(d, false); }

    private void light(int x, int y, int z) {
        if (w.getBlockAt(x, y, z).getType().isAir()) set(x, y, z, LIGHT);
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
    }

    private Material lit() {
        int r = rnd.nextInt(10);
        return r < 4 ? Material.OCHRE_FROGLIGHT : r < 6 ? Material.GLOWSTONE : r < 8 ? Material.PEARLESCENT_FROGLIGHT : Material.SEA_LANTERN;
    }

    private Material facade(int x, int y, int z, Material wall, Material glass, double litChance) {
        int lvl = y - G;
        if (lvl % 4 == 0 || ((x + z) & 1) == 1) return wall;
        return rnd.nextDouble() < litChance ? lit() : glass;
    }

    /** Gebäude aus einer Höhenkarte: Fassade mit Fenstern, Dach, oberste Kante leuchtet. */
    private void prism(int x1, int z1, int x2, int z2, H hf, Material wall, Material glass, Material roof, Material crown, double litChance) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            int h = hf.h(x, z);
            if (h <= 0) continue;
            int n = Math.min(Math.min(hf.h(x + 1, z), hf.h(x - 1, z)), Math.min(hf.h(x, z + 1), hf.h(x, z - 1)));
            for (int y = G + 1 + Math.max(0, n); y < G + h; y++) set(x, y, z, facade(x, y, z, wall, glass, litChance));
            set(x, G + h, z, n < h && crown != null ? crown : roof);
        }
    }

    private static H rect(int x1, int z1, int x2, int z2, int h) {
        return (x, z) -> x >= x1 && x <= x2 && z >= z1 && z <= z2 ? h : 0;
    }

    private static H circle(double cx, double cz, double r, int h) {
        return (x, z) -> (x - cx) * (x - cx) + (z - cz) * (z - cz) <= (r + 0.4) * (r + 0.4) ? h : 0;
    }

    private static H max(H... hs) {
        return (x, z) -> { int m = 0; for (H h : hs) m = Math.max(m, h.h(x, z)); return m; };
    }

    /** Y-förmiger Hotelturm (drei Flügel, zur Spitze hin abgestuft). */
    private static H yTower(int cx, int cz, int len, int half, int maxH, double rotDeg) {
        return (x, z) -> {
            int dx = x - cx, dz = z - cz, best = 0;
            if (Math.abs(dx) <= half + 1 && Math.abs(dz) <= half + 1) best = maxH;
            for (int k = 0; k < 3; k++) {
                double a = Math.toRadians(rotDeg + k * 120);
                double along = dx * Math.cos(a) + dz * Math.sin(a), across = -dx * Math.sin(a) + dz * Math.cos(a);
                if (along >= 0 && along <= len && Math.abs(across) <= half + 0.3) best = Math.max(best, maxH - ((int) (along / 6)) * 4);
            }
            return best;
        };
    }

    /** Gebogene Hotelscheibe (Bellagio, Wynn): Kreisring-Ausschnitt, Mitte am höchsten. */
    private static H arc(double ox, double oz, double r1, double r2, double a1, double a2, int h) {
        return (x, z) -> {
            double d = Math.hypot(x - ox, z - oz);
            if (d < r1 || d > r2) return 0;
            double a = Math.toDegrees(Math.atan2(z - oz, x - ox));
            if (a < 0) a += 360;
            if (a < a1 || a > a2) return 0;
            double mid = (a1 + a2) / 2, half = (a2 - a1) / 2;
            return h - (int) (Math.abs(a - mid) / half * 3) * 4;
        };
    }

    private void cone(int cx, int cz, double r, int y0, int height, Material m) {
        for (int k = 0; k <= height; k++) {
            double rk = r * (1.0 - (double) k / (height + 1));
            int ri = (int) Math.ceil(rk);
            for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++)
                if (x * x + z * z <= (rk + 0.3) * (rk + 0.3)) set(cx + x, y0 + k, cz + z, m);
        }
    }

    private void sphere(double cx, double cy, double cz, double r, java.util.function.Function<double[], Material> mat) {
        int ri = (int) Math.ceil(r) + 1;
        for (int x = -ri; x <= ri; x++) for (int y = -ri; y <= ri; y++) for (int z = -ri; z <= ri; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (Math.abs(d - r) <= 0.55) {
                Material m = mat.apply(new double[]{x / d, y / d, z / d});
                if (m != null) set((int) Math.floor(cx) + x, (int) Math.floor(cy) + y, (int) Math.floor(cz) + z, m);
            }
        }
    }

    private void line(double x1, double y1, double z1, double x2, double y2, double z2, Material m) {
        double len = Math.max(1, Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1)));
        for (double t = 0; t <= 1.0001; t += 0.5 / len)
            set((int) Math.round(x1 + (x2 - x1) * t), (int) Math.round(y1 + (y2 - y1) * t), (int) Math.round(z1 + (z2 - z1) * t), m);
    }

    private void palm(int x, int z, int height) {
        int lean = rnd.nextInt(3) - 1;
        int top = G + height;
        for (int y = G + 1; y <= top; y++) set(x + (y > top - 2 ? lean : 0), y, z, Material.STRIPPED_JUNGLE_LOG);
        int tx = x + lean;
        set(tx, top + 1, z, PALM_LEAF);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] d : dirs) {
            int len = (d[0] != 0 && d[1] != 0) ? 2 : 3;
            for (int i = 1; i <= len; i++) set(tx + d[0] * i, top + (i == len ? 0 : 1), z + d[1] * i, PALM_LEAF);
            if (len == 3) set(tx + d[0] * 3, top - 1, z + d[1] * 3, PALM_LEAF);
        }
    }

    private void lamp(int x, int z) {
        for (int y = G + 1; y <= G + 5; y++) set(x, y, z, Material.POLISHED_BLACKSTONE_WALL);
        set(x, G + 6, z, LANTERN);
    }

    /** Neon-Leuchtband am Gebäudesockel (abwechselnd Farbe/Licht). */
    private void neon(int x1, int z1, int x2, int z2, int y, Material glass) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                set(x, y, z, ((x + z) % 3 == 0) ? Material.PEARLESCENT_FROGLIGHT : glass);
    }

    // ---------------------------------------------------------------- Strasse

    private void road(int z1, int z2) {
        for (int z = z1; z <= z2; z++) {
            for (int x = -16; x <= 15; x++) {
                Material m;
                if (x <= -13 || x >= 12) m = (x == -16 || x == 15) ? Material.SAND : Material.SMOOTH_STONE;
                else if (x == -1 || x == 0) m = Material.POLISHED_ANDESITE;
                else if (x == -2 || x == 1) m = Material.YELLOW_CONCRETE;
                else if ((x == -9 || x == -5 || x == 4 || x == 8) && Math.floorMod(z, 8) < 4) m = Material.WHITE_CONCRETE;
                else m = Material.GRAY_CONCRETE;
                set(x, G, z, m);
            }
            if (Math.floorMod(z, 14) == 0) palm(rnd.nextBoolean() ? -1 : 0, z, 8 + rnd.nextInt(3));
            if (Math.floorMod(z, 16) == 8) { lamp(-14, z); lamp(13, z); }
        }
    }

    private void crossStreet(int z1, int z2, int xFrom, int xTo) {
        for (int x = xFrom; x <= xTo; x++) {
            if (x >= -16 && x <= 15) {
                // Zebrastreifen über den Boulevard
                for (int z = z1; z <= z2; z++) if (Math.floorMod(x, 2) == 0) set(x, G, z, Material.WHITE_CONCRETE);
                continue;
            }
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean walk = z == z1 - 1 || z == z2 + 1;
                set(x, G, z, walk ? Material.SMOOTH_STONE : (z == (z1 + z2) / 2 && Math.floorMod(x, 6) < 3 ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE));
            }
        }
    }

    // ---------------------------------------------------------------- Wahrzeichen

    private void welcomeSign() {
        int z = 325, y0 = G + 1;
        box(-1, y0, z, -1, y0 + 6, z, Material.WHITE_CONCRETE);
        box(0, y0, z, 0, y0 + 6, z, Material.WHITE_CONCRETE);
        // Raute: Breite 17, Höhe 11
        for (int dy = 0; dy <= 10; dy++) {
            int half = dy <= 5 ? 6 + dy / 2 : 6 + (10 - dy) / 2;
            for (int x = -half - 1; x <= half; x++) {
                boolean rim = x == -half - 1 || x == half || dy == 0 || dy == 10;
                Material m = rim ? ((x + dy) % 2 == 0 ? Material.OCHRE_FROGLIGHT : Material.RED_CONCRETE) : Material.WHITE_CONCRETE;
                set(x, y0 + 6 + dy, z, m);
            }
        }
        // Stern oben
        set(-1, y0 + 17, z, Material.RED_CONCRETE); set(0, y0 + 17, z, Material.RED_CONCRETE);
        set(-1, y0 + 18, z, Material.OCHRE_FROGLIGHT);
        box(-3, G, z - 3, 2, G, z + 3, Material.SMOOTH_STONE);
        for (int x = -3; x <= 2; x += 5) for (int zz = z - 3; zz <= z + 3; zz += 6) light(x, G + 1, zz);
    }

    private void mandalayBay() {
        H tower = max(yTower(-58, 270, 22, 3, 62, 0), rect(-45, 250, -26, 292, 8));
        prism(-90, 240, -24, 300, tower, Material.YELLOW_TERRACOTTA, Material.YELLOW_STAINED_GLASS, Material.SMOOTH_SANDSTONE, Material.OCHRE_FROGLIGHT, 0.35);
        // Strand & Pool hinter dem Hotel
        box(-110, G, 245, -92, G, 295, Material.SAND);
        box(-108, G, 255, -95, G, 285, Material.WATER);
        neon(-26, 252, -26, 290, G + 9, Material.YELLOW_STAINED_GLASS);
        for (int z = 254; z <= 290; z += 9) palm(-22, z, 8);
    }

    private void luxor() {
        int cx = -62, cz = 213;
        for (int k = 0; k <= 30; k++) {
            int half = 30 - k, y = G + 1 + k;
            for (int x = cx - half; x <= cx + half; x++) for (int z = cz - half; z <= cz + half; z++) {
                boolean edge = Math.abs(x - cx) == half || Math.abs(z - cz) == half;
                if (half <= 1) { set(x, y, z, k == 30 ? Material.BEACON : Material.IRON_BLOCK); continue; }
                if (!edge) continue;
                boolean corner = Math.abs(x - cx) == half && Math.abs(z - cz) == half;
                set(x, y, z, corner && k % 2 == 0 ? Material.SEA_LANTERN : Material.BLACK_STAINED_GLASS);
            }
        }
        // Sphinx vor der Pyramide, Blick zum Strip (Osten)
        int sz = 213;
        box(-29, G + 1, sz - 2, -21, G + 3, sz + 2, Material.SANDSTONE);
        box(-20, G + 1, sz - 2, -18, G + 1, sz - 1, Material.SMOOTH_SANDSTONE);
        box(-20, G + 1, sz + 1, -18, G + 1, sz + 2, Material.SMOOTH_SANDSTONE);
        box(-23, G + 4, sz - 1, -21, G + 7, sz + 1, Material.SMOOTH_SANDSTONE);
        box(-23, G + 4, sz - 2, -22, G + 7, sz - 2, Material.BLUE_TERRACOTTA);
        box(-23, G + 4, sz + 2, -22, G + 7, sz + 2, Material.BLUE_TERRACOTTA);
        box(-24, G + 8, sz - 1, -22, G + 8, sz + 1, Material.GOLD_BLOCK);
        set(-20, G + 6, sz, Material.CUT_SANDSTONE);
        light(-17, G + 2, sz); light(-17, G + 6, sz);
        // Obelisk
        box(-24, G + 1, 190, -24, G + 14, 190, Material.SMOOTH_SANDSTONE);
        set(-24, G + 15, 190, Material.GOLD_BLOCK);
    }

    private void excalibur() {
        H towers = max(rect(-55, 128, -44, 174, 34), rect(-88, 163, -55, 174, 34), rect(-88, 128, -76, 163, 30));
        prism(-90, 125, -40, 176, towers, Material.WHITE_CONCRETE, Material.LIGHT_GRAY_STAINED_GLASS, Material.RED_TERRACOTTA, Material.RED_CONCRETE, 0.3);
        // Burgmauer an der Strasse
        for (int z = 132; z <= 170; z++) for (int y = G + 1; y <= G + 12; y++) set(-33, y, z, y >= G + 9 && z % 2 == 0 ? Material.AIR : Material.WHITE_CONCRETE);
        for (int z = 132; z <= 170; z++) { set(-33, G + 8, z, (z % 3 == 0) ? Material.OCHRE_FROGLIGHT : Material.GOLD_BLOCK); }
        box(-33, G + 1, 149, -33, G + 6, 153, Material.AIR);
        Material[] roofs = {Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE};
        int[] tz = {130, 140, 146, 156, 162, 172};
        for (int i = 0; i < tz.length; i++) {
            int h = 16 + (i % 3) * 4;
            prism(-37, tz[i] - 3, -29, tz[i] + 3, circle(-33, tz[i], 2.6, h), Material.WHITE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS, Material.WHITE_CONCRETE, null, 0.5);
            cone(-33, tz[i], 3.6, G + h + 1, 7, roofs[i]);
            set(-33, G + h + 9, tz[i], Material.GOLD_BLOCK);
        }
        // runde Türme auf dem Hotel
        for (int[] t : new int[][]{{-50, 133}, {-50, 169}, {-82, 169}}) {
            prism(t[0] - 4, t[1] - 4, t[0] + 4, t[1] + 4, circle(t[0], t[1], 3, 40), Material.WHITE_CONCRETE, Material.LIGHT_GRAY_STAINED_GLASS, Material.WHITE_CONCRETE, null, 0.4);
            cone(t[0], t[1], 4.2, G + 41, 9, Material.RED_CONCRETE);
        }
    }

    private void newYork() {
        // Empire State
        H empire = max(rect(-62, 86, -52, 97, 46), rect(-60, 88, -54, 95, 58), rect(-59, 89, -55, 94, 64));
        prism(-64, 84, -50, 99, empire, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_STAINED_GLASS, Material.SMOOTH_STONE, Material.SEA_LANTERN, 0.35);
        for (int y = G + 65; y <= G + 76; y++) set(-57, y, 91, y > G + 73 ? Material.RED_STAINED_GLASS : Material.IRON_BARS);
        set(-57, G + 77, 91, Material.REDSTONE_LAMP);
        light(-57, G + 78, 91);
        // Chrysler
        H chrysler = max(rect(-74, 99, -66, 107, 44), rect(-72, 101, -68, 105, 50));
        prism(-76, 97, -64, 109, chrysler, Material.WHITE_CONCRETE, Material.LIGHT_GRAY_STAINED_GLASS, Material.WHITE_CONCRETE, null, 0.35);
        for (int k = 0; k < 6; k++) {
            int r = 2 - k / 3;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
                set(-70 + x, G + 51 + k, 103 + z, Math.abs(x) == r || Math.abs(z) == r ? (k % 2 == 0 ? Material.IRON_BLOCK : Material.SEA_LANTERN) : Material.IRON_BLOCK);
        }
        for (int y = G + 57; y <= G + 64; y++) set(-70, y, 103, Material.IRON_BARS);
        // weitere Wolkenkratzer
        Material[][] pal = {{Material.BRICKS, Material.BROWN_STAINED_GLASS}, {Material.TERRACOTTA, Material.ORANGE_STAINED_GLASS},
                {Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_STAINED_GLASS}, {Material.STONE_BRICKS, Material.GRAY_STAINED_GLASS}};
        int[][] blds = {{-48, 74, -38, 84, 30}, {-62, 72, -50, 82, 38}, {-80, 74, -66, 84, 26}, {-48, 100, -38, 112, 34}, {-62, 102, -52, 113, 24},
                {-90, 88, -78, 100, 40}, {-88, 104, -78, 114, 30}};
        for (int i = 0; i < blds.length; i++) {
            int[] b = blds[i];
            prism(b[0], b[1], b[2], b[3], rect(b[0], b[1], b[2], b[3], b[4]), pal[i % 4][0], pal[i % 4][1], Material.SMOOTH_STONE, Material.SEA_LANTERN, 0.35);
        }
        // Freiheitsstatue
        int sx = -26, sz = 92;
        box(sx - 2, G + 1, sz - 2, sx + 2, G + 5, sz + 2, Material.STONE_BRICKS);
        box(sx - 1, G + 6, sz - 1, sx + 1, G + 13, sz + 1, Material.WAXED_OXIDIZED_COPPER);
        set(sx, G + 14, sz, Material.WAXED_OXIDIZED_COPPER);
        set(sx, G + 15, sz, Material.WAXED_OXIDIZED_CUT_COPPER);
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) set(sx + c[0], G + 15, sz + c[1], Material.WAXED_OXIDIZED_COPPER_GRATE);
        box(sx, G + 14, sz + 2, sx, G + 18, sz + 2, Material.WAXED_OXIDIZED_COPPER);
        set(sx, G + 19, sz + 2, Material.GOLD_BLOCK);
        set(sx, G + 20, sz + 2, Material.SHROOMLIGHT);
        set(sx + 1, G + 10, sz - 2, Material.WAXED_OXIDIZED_COPPER);
        light(sx + 3, G + 4, sz); light(sx + 3, G + 12, sz);
    }

    private void mgmGrand() {
        H plus = (x, z) -> {
            int dx = x - 62, dz = z - 100;
            if (Math.abs(dx) <= 4 && Math.abs(dz) <= 20) return 52 - (Math.abs(dz) / 7) * 4;
            if (Math.abs(dz) <= 4 && Math.abs(dx) <= 22) return 52 - (Math.abs(dx) / 7) * 4;
            return 0;
        };
        prism(36, 76, 90, 124, max(plus, rect(36, 84, 48, 116, 10)), Material.GREEN_TERRACOTTA, Material.GREEN_STAINED_GLASS, Material.SMOOTH_STONE, Material.VERDANT_FROGLIGHT, 0.3);
        neon(36, 84, 36, 116, G + 11, Material.LIME_STAINED_GLASS);
        // Goldener Löwe, Blick nach Westen
        box(24, G + 1, 96, 34, G + 3, 104, Material.SMOOTH_QUARTZ);
        box(27, G + 4, 98, 33, G + 7, 102, Material.GOLD_BLOCK);
        box(25, G + 4, 97, 26, G + 5, 98, Material.GOLD_BLOCK);
        box(25, G + 4, 102, 26, G + 5, 103, Material.GOLD_BLOCK);
        box(25, G + 7, 98, 28, G + 11, 102, Material.GOLD_BLOCK);
        box(24, G + 8, 99, 24, G + 9, 101, Material.RAW_GOLD_BLOCK);
        set(24, G + 10, 99, Material.BLACK_CONCRETE); set(24, G + 10, 101, Material.BLACK_CONCRETE);
        box(29, G + 8, 97, 29, G + 12, 103, Material.RAW_GOLD_BLOCK);
        light(22, G + 5, 100); light(22, G + 11, 100);
    }

    private void bellagio() {
        // See mit Balustrade
        for (int x = -41; x <= -17; x++) for (int z = -26; z <= 66; z++) {
            boolean rim = x == -41 || x == -17 || z == -26 || z == 66;
            if (rim) { set(x, G, z, Material.SMOOTH_QUARTZ); set(x, G + 1, z, Material.DIORITE_WALL); }
            else { set(x, G - 2, z, Material.SAND); set(x, G - 1, z, Material.WATER); set(x, G, z, Material.WATER); }
        }
        for (int z = -20; z <= 60; z += 4) light(-29, G + 1, z);
        // Hotel: Bogen, Sockelbau mit Ziegeldach
        prism(-100, -20, -42, 70, max(arc(-15, 20, 57, 66, 148, 212, 58), rect(-72, -18, -44, 64, 10)),
                Material.SMOOTH_SANDSTONE, Material.LIGHT_GRAY_STAINED_GLASS, Material.ORANGE_TERRACOTTA, Material.OCHRE_FROGLIGHT, 0.4);
        neon(-44, -18, -44, 64, G + 11, Material.WHITE_STAINED_GLASS);
        for (int z = -16; z <= 62; z += 13) palm(-43, z, 7);
    }

    private void paris() {
        prism(58, 4, 92, 52, max(rect(62, 6, 90, 50, 34), rect(58, 4, 70, 52, 12)), Material.SMOOTH_SANDSTONE, Material.YELLOW_STAINED_GLASS, Material.SMOOTH_STONE, null, 0.35);
        // Mansardendach
        for (int k = 0; k < 4; k++) for (int x = 62 + k; x <= 90 - k; x++) for (int z = 6 + k; z <= 50 - k; z++)
            if (x == 62 + k || x == 90 - k || z == 6 + k || z == 50 - k) set(x, G + 35 + k, z, Material.DEEPSLATE_TILES);
        // Eiffelturm
        int cx = 40, cz = 28, H = 82;
        for (int y = 0; y <= H; y++) {
            double t = (double) y / H;
            double half = 1.2 + 10.5 * Math.pow(1 - t, 1.9);
            int hi = (int) Math.round(half);
            int yy = G + 1 + y;
            if (y < 20) {
                // vier Beine
                for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
                    for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++)
                        set(cx + sx * (hi - a), yy, cz + sz * (hi - b), (a + b) % 2 == 0 ? Material.WAXED_CUT_COPPER : Material.IRON_BARS);
            } else {
                for (int x = -hi; x <= hi; x++) for (int z = -hi; z <= hi; z++) {
                    boolean edge = Math.abs(x) == hi || Math.abs(z) == hi;
                    if (!edge) continue;
                    boolean corner = Math.abs(x) == hi && Math.abs(z) == hi;
                    set(cx + x, yy, cz + z, corner ? (y % 4 == 0 ? Material.OCHRE_FROGLIGHT : Material.WAXED_CUT_COPPER) : ((x + z + y) % 3 == 0 ? Material.WAXED_CUT_COPPER : Material.IRON_BARS));
                }
            }
            if (y == 19 || y == 20 || y == 44) {
                for (int x = -hi - 1; x <= hi + 1; x++) for (int z = -hi - 1; z <= hi + 1; z++) {
                    boolean edge = Math.abs(x) == hi + 1 || Math.abs(z) == hi + 1;
                    set(cx + x, yy, cz + z, edge ? Material.OCHRE_FROGLIGHT : Material.WAXED_CUT_COPPER);
                }
            }
        }
        for (int y = G + H + 2; y <= G + H + 8; y++) set(cx, y, cz, Material.IRON_BARS);
        set(cx, G + H + 9, cz, Material.SHROOMLIGHT);
        // Heissluftballon-Schild
        sphere(26, G + 15, 0, 5.5, d -> d[1] > 0.6 ? Material.ORANGE_CONCRETE : d[1] > 0.1 ? Material.BLUE_CONCRETE : d[1] > -0.3 ? Material.WHITE_CONCRETE : Material.ORANGE_CONCRETE);
        box(26, G + 1, 0, 26, G + 8, 0, Material.WAXED_CUT_COPPER);
        box(25, G + 7, -1, 27, G + 8, 1, Material.ORANGE_TERRACOTTA);
        light(21, G + 15, 0); light(26, G + 22, 0);
    }

    private void highRoller() {
        int x0 = 64, hy = G + 46, hz = -32, R = 38;
        for (int a = 0; a < 720; a++) {
            double ang = Math.toRadians(a / 2.0);
            int y = (int) Math.round(hy + R * Math.sin(ang)), z = (int) Math.round(hz + R * Math.cos(ang));
            set(x0, y, z, a % 12 == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.WHITE_CONCRETE);
            set(x0 + 1, y, z, Material.WHITE_CONCRETE);
        }
        for (int s = 0; s < 16; s++) {
            double ang = Math.toRadians(s * 22.5);
            line(x0, hy, hz, x0, hy + R * Math.sin(ang), hz + R * Math.cos(ang), Material.IRON_BARS);
        }
        for (int c = 0; c < 14; c++) {
            double ang = Math.toRadians(c * (360.0 / 14));
            int y = (int) Math.round(hy + (R + 2) * Math.sin(ang)), z = (int) Math.round(hz + (R + 2) * Math.cos(ang));
            box(x0 - 1, y - 1, z - 1, x0 + 2, y + 1, z + 1, Material.LIGHT_BLUE_STAINED_GLASS);
            set(x0, y, z, Material.SEA_LANTERN);
            set(x0 + 1, y, z, Material.SEA_LANTERN);
        }
        box(x0 - 2, hy - 2, hz - 2, x0 + 3, hy + 2, hz + 2, Material.IRON_BLOCK);
        set(x0 - 3, hy, hz, Material.SEA_LANTERN);
        // A-Stützen
        for (int side = -1; side <= 1; side += 2) {
            int xs = side < 0 ? x0 - 3 : x0 + 4;
            line(xs, hy, hz, xs + side * 3, G + 1, hz - 16, Material.WHITE_CONCRETE);
            line(xs, hy, hz, xs + side * 3, G + 1, hz + 16, Material.WHITE_CONCRETE);
        }
        // Einstiegsgebäude
        prism(52, -60, 78, -50, rect(52, -60, 78, -50, 7), Material.WHITE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS, Material.SMOOTH_STONE, Material.PEARLESCENT_FROGLIGHT, 0.5);
        // Promenade zum Strip
        for (int x = 16; x <= 52; x++) for (int z = -58; z <= -52; z++) set(x, G, z, (x + z) % 5 == 0 ? Material.SEA_LANTERN : Material.SMOOTH_STONE);
        for (int x = 20; x <= 50; x += 10) { lamp(x, -59); lamp(x, -51); }
    }

    private void caesars() {
        prism(-100, -95, -55, -30, max(rect(-88, -92, -60, -77, 46), rect(-98, -76, -84, -32, 40), rect(-62, -92, -58, -78, 50)),
                Material.QUARTZ_BLOCK, Material.LIGHT_BLUE_STAINED_GLASS, Material.SMOOTH_QUARTZ, Material.SEA_LANTERN, 0.35);
        // Kolosseum
        int kx = -60, kz = -48;
        prism(kx - 15, kz - 15, kx + 15, kz + 15, circle(kx, kz, 13, 18), Material.SMOOTH_QUARTZ, Material.BLACK_STAINED_GLASS, Material.SMOOTH_STONE, Material.OCHRE_FROGLIGHT, 0.2);
        // Säulengang an der Strasse
        for (int z = -80; z <= -64; z += 3) for (int y = G + 1; y <= G + 10; y++) set(-31, y, z, Material.QUARTZ_PILLAR);
        box(-36, G + 11, -81, -30, G + 11, -63, Material.SMOOTH_QUARTZ);
        box(-36, G + 12, -81, -30, G + 12, -63, Material.CHISELED_QUARTZ_BLOCK);
        box(-40, G + 1, -81, -37, G + 10, -63, Material.QUARTZ_BRICKS);
        for (int z = -80; z <= -64; z += 4) light(-33, G + 8, z);
        // Brunnen mit Statue
        int fx = -24, fz = -50;
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d <= 4.4 && d > 3.5) set(fx + x, G + 1, fz + z, Material.QUARTZ_SLAB);
            else if (d <= 3.5) set(fx + x, G, fz + z, Material.WATER);
        }
        box(fx, G, fz, fx, G + 4, fz, Material.QUARTZ_PILLAR);
        set(fx, G + 5, fz, Material.GOLD_BLOCK);
        light(fx + 2, G + 2, fz);
    }

    private void mirage() {
        prism(-95, -165, -40, -105, yTower(-64, -135, 22, 3, 52, 180), Material.WHITE_CONCRETE, Material.YELLOW_STAINED_GLASS, Material.SMOOTH_STONE, Material.OCHRE_FROGLIGHT, 0.4);
        // Vulkan in einer Lagune
        int vx = -28, vz = -132;
        for (int x = -13; x <= 13; x++) for (int z = -13; z <= 13; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d <= 12.4 && d > 10.5) { set(vx + x, G - 1, vz + z, Material.SAND); set(vx + x, G, vz + z, Material.WATER); }
        }
        for (int k = 0; k <= 12; k++) {
            double r = 10 - k * 0.6;
            int ri = (int) Math.ceil(r);
            for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.3) continue;
                Material m = k >= 11 && d < 2.6 ? (k == 12 ? Material.LAVA : Material.MAGMA_BLOCK)
                        : rnd.nextInt(4) == 0 ? Material.BASALT : rnd.nextBoolean() ? Material.BLACKSTONE : Material.TUFF;
                set(vx + x, G + 1 + k, vz + z, m);
            }
        }
        for (int[] p : new int[][]{{-16, -146}, {-16, -118}, {-40, -146}, {-40, -118}}) palm(p[0], p[1], 9);
    }

    private void venetian() {
        prism(55, -155, 100, -78, max(rect(60, -142, 98, -126, 50), rect(82, -154, 98, -84, 46), rect(56, -122, 80, -86, 14)),
                Material.SMOOTH_SANDSTONE, Material.ORANGE_STAINED_GLASS, Material.SMOOTH_STONE, Material.OCHRE_FROGLIGHT, 0.35);
        // Dogenpalast an der Strasse
        for (int x = 32; x <= 44; x++) for (int z = -128; z <= -92; z++) for (int y = G + 1; y <= G + 14; y++) {
            boolean shell = x == 32 || x == 44 || z == -128 || z == -92 || y == G + 14;
            if (!shell) continue;
            Material m;
            if (y <= G + 5) m = (z % 3 == 0) ? Material.QUARTZ_PILLAR : (y == G + 5 ? Material.QUARTZ_BRICKS : Material.AIR);
            else m = ((z + y) % 2 == 0) ? Material.PINK_TERRACOTTA : Material.WHITE_TERRACOTTA;
            if (x != 32 && y <= G + 5) m = Material.QUARTZ_BRICKS;
            set(x, y, z, m);
        }
        for (int z = -126; z <= -94; z += 4) light(31, G + 3, z);
        // Kanal mit Rialto-Brücke
        for (int x = 18; x <= 24; x++) for (int z = -132; z <= -88; z++) {
            boolean rim = x == 18 || x == 24 || z == -132 || z == -88;
            if (rim) set(x, G, z, Material.SMOOTH_QUARTZ);
            else { set(x, G - 2, z, Material.CYAN_TERRACOTTA); set(x, G - 1, z, Material.WATER); set(x, G, z, Material.WATER); }
        }
        for (int x = 17; x <= 25; x++) {
            int lift = 3 - Math.abs(x - 21) / 2;
            box(x, G + lift, -112, x, G + lift, -108, Material.SMOOTH_QUARTZ);
            set(x, G + lift + 1, -112, Material.QUARTZ_SLAB);
            set(x, G + lift + 1, -108, Material.QUARTZ_SLAB);
        }
        // Campanile
        int tx = 27, tz = -84;
        box(tx - 2, G + 1, tz - 2, tx + 2, G + 34, tz + 2, Material.BRICKS);
        box(tx - 2, G + 35, tz - 2, tx + 2, G + 39, tz + 2, Material.QUARTZ_BLOCK);
        box(tx - 1, G + 35, tz - 2, tx + 1, G + 38, tz + 2, Material.AIR);
        box(tx - 2, G + 35, tz - 1, tx + 2, G + 38, tz + 1, Material.AIR);
        set(tx, G + 36, tz, Material.GOLD_BLOCK);
        cone(tx, tz, 2.6, G + 40, 6, Material.WAXED_OXIDIZED_COPPER);
        set(tx, G + 47, tz, Material.GOLD_BLOCK);
        light(tx, G + 37, tz - 1);
    }

    private void sphereVenue() {
        double cx = 140, cy = G + 31, cz = -115, R = 28;
        sphere(cx, cy, cz, R, d -> {
            double c = -d[0]; // Blick nach Westen zum Strip
            double hx = -d[0] * 0.98 - d[1] * 0.2 + d[2] * 0.15;
            if (c > 0.975) return Material.BLACK_CONCRETE;
            if (hx > 0.955 && d[1] > 0.06 && d[2] < -0.02) return Material.WHITE_CONCRETE;
            if (c > 0.90) return Material.BLUE_CONCRETE;
            if (c > 0.80) return Material.LIGHT_BLUE_CONCRETE;
            if (c > 0.77) return Material.CYAN_CONCRETE;
            if (c > 0.55 && rnd.nextInt(30) == 0) return Material.RED_CONCRETE;
            return Material.WHITE_CONCRETE;
        });
        // unsichtbare Lichter rundherum, damit die "LEDs" nachts leuchten
        int ri = (int) R + 3;
        for (int x = -ri; x <= ri; x += 4) for (int y = -ri; y <= ri; y += 4) for (int z = -ri; z <= ri; z += 4) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d > R + 0.8 && d < R + 3) light((int) cx + x, (int) cy + y, (int) cz + z);
        }
        box((int) cx - 8, G + 1, (int) cz - 8, (int) cx + 8, G + 3, (int) cz + 8, Material.POLISHED_BLACKSTONE);
        // Zufahrt vom Strip
        for (int x = 16; x <= 110; x++) for (int z = -168; z <= -162; z++)
            if (x < 55 || x > 100) set(x, G, z, z == -168 || z == -162 ? Material.SMOOTH_STONE : Material.GRAY_CONCRETE);
    }

    private void wynn() {
        prism(40, -280, 80, -160, max(arc(112, -195, 55, 62, 148, 212, 62), arc(112, -250, 54, 60, 158, 202, 56)),
                Material.BROWN_TERRACOTTA, Material.BROWN_STAINED_GLASS, Material.SMOOTH_STONE, Material.OCHRE_FROGLIGHT, 0.25);
        box(48, G + 1, -230, 60, G + 9, -218, Material.CUT_RED_SANDSTONE);
        for (int z = -275; z <= -170; z += 12) palm(32, z, 8);
    }

    private void strat() {
        int cx = 38, cz = -302;
        prism(18, -322, 58, -282, rect(20, -320, 56, -284, 10), Material.WHITE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS, Material.SMOOTH_STONE, Material.SEA_LANTERN, 0.4);
        int top = 160;
        for (int y = 11; y <= top; y++) {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 3.4 && d > 2.4) set(cx + x, G + y, cz + z, y % 10 == 0 ? Material.SEA_LANTERN : Material.WHITE_CONCRETE);
            }
            // drei Stützrippen
            int L = (int) Math.round(7 * Math.max(0, 1 - (y - 11) / 110.0));
            for (int k = 0; k < 3; k++) {
                double a = Math.toRadians(90 + k * 120);
                for (int i = 3; i <= 3 + L; i++) set(cx + (int) Math.round(Math.cos(a) * i), G + y, cz + (int) Math.round(Math.sin(a) * i), Material.WHITE_CONCRETE);
            }
        }
        // Aussichtskapsel
        for (int k = 0; k <= 18; k++) {
            double r = k < 5 ? 5 + k * 1.6 : k < 14 ? 13 : 13 - (k - 14) * 2;
            int ri = (int) Math.ceil(r);
            int y = G + top + k;
            for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.4) continue;
                boolean edge = d > r - 0.8;
                Material m = null;
                if (k == 0 || k == 18) m = Material.WHITE_CONCRETE;
                else if (edge) m = (k >= 6 && k <= 12) ? (k % 3 == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.LIGHT_BLUE_STAINED_GLASS) : Material.WHITE_CONCRETE;
                if (m != null) set(cx + x, y, cz + z, m);
            }
        }
        for (int y = G + top + 19; y <= G + top + 46; y++) set(cx, y, cz, y % 6 == 0 ? Material.SEA_LANTERN : Material.IRON_BLOCK);
        set(cx, G + top + 47, cz, Material.REDSTONE_BLOCK);
        set(cx, G + top + 48, cz, Material.SHROOMLIGHT);
    }

    /** Neutrale Füll-Hotels ohne Namen, damit der Strip dicht wirkt. */
    private void filler(int x1, int z1, int x2, int z2, long seed) {
        Random r = new Random(seed);
        Material[][] pal = {{Material.WHITE_CONCRETE, Material.CYAN_STAINED_GLASS}, {Material.RED_CONCRETE, Material.RED_STAINED_GLASS},
                {Material.LIGHT_GRAY_CONCRETE, Material.BLUE_STAINED_GLASS}, {Material.PURPLE_TERRACOTTA, Material.MAGENTA_STAINED_GLASS},
                {Material.SMOOTH_SANDSTONE, Material.ORANGE_STAINED_GLASS}};
        Material[] neon = {Material.MAGENTA_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.LIME_STAINED_GLASS};
        int depth = Math.abs(x2 - x1);
        boolean west = x1 < 0;
        for (int z = z1; z + 12 <= z2; ) {
            int len = 12 + r.nextInt(14);
            int ze = Math.min(z2, z + len);
            int h = 18 + r.nextInt(30);
            Material[] p = pal[r.nextInt(pal.length)];
            int xa = west ? x2 - 6 - r.nextInt(Math.max(1, depth / 3)) : x1 + 6 + r.nextInt(Math.max(1, depth / 3));
            int xb = west ? x1 : x2;
            int lo = Math.min(xa, xb), hi = Math.max(xa, xb);
            prism(lo, z, hi, ze, rect(lo, z, hi, ze, h), p[0], p[1], Material.SMOOTH_STONE, Material.PEARLESCENT_FROGLIGHT, 0.35);
            int front = west ? hi : lo;
            neon(front + (west ? 1 : -1), z, front + (west ? 1 : -1), ze, G + 4, neon[r.nextInt(neon.length)]);
            z = ze + 3;
        }
    }

    // ---------------------------------------------------------------- Schilder

    private static void sign(World w, double x, double y, double z, Component text, float scale) {
        w.spawn(new Location(w, x, y, z), TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setDefaultBackground(false);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(150, 8, 6, 20));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setLineWidth(260);
            t.setViewRange(4.0f);
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            t.addScoreboardTag(TAG);
            t.setPersistent(true);
        });
    }

    private static Component neonText(String name, TextColor c, String sub) {
        Component t = Component.text(name, c, TextDecoration.BOLD);
        if (sub != null) t = t.append(Component.newline()).append(Component.text(sub, NamedTextColor.WHITE));
        return t;
    }

    private void signs() {
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        sign(w, -0.5, G + 13.5, 323.5, Component.text("Welcome", NamedTextColor.WHITE, TextDecoration.ITALIC)
                .append(Component.newline()).append(Component.text("to Fabulous", NamedTextColor.WHITE))
                .append(Component.newline()).append(Component.text("LAS VEGAS", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline()).append(Component.text("NEVADA", NamedTextColor.WHITE)), 3.2f);
        Object[][] s = {
                {-21.5, 270.5, "MANDALAY BAY", 0xF6C445, null},
                {-21.5, 238.5, "LUXOR", 0x6AD1FF, "Pyramide mit Himmelsstrahl"},
                {-24.5, 150.5, "EXCALIBUR", 0xFF5555, null},
                {-21.5, 80.5, "NEW YORK-NEW YORK", 0xFFFFFF, null},
                {20.5, 88.5, "MGM GRAND", 0x55FF55, null},
                {-21.5, 20.5, "BELLAGIO", 0xF3E3B5, "Wasserspiele jede 1½ Minuten"},
                {20.5, 12.5, "PARIS", 0xFF9F43, null},
                {20.5, -40.5, "HIGH ROLLER", 0xD86BFF, "Riesenrad"},
                {-21.5, -60.5, "CAESARS PALACE", 0xE8E8FF, null},
                {-21.5, -150.5, "THE MIRAGE", 0xF6C445, "Vulkan bricht alle 2 Minuten aus"},
                {20.5, -100.5, "THE VENETIAN", 0xFFD27F, null},
                {108.5, -115.5, "SPHERE", 0x4FC3FF, null},
                {22.5, -195.5, "WYNN", 0xE0B07A, null},
                {22.5, -250.5, "ENCORE", 0xE0B07A, null},
                {20.5, -295.5, "THE STRAT", 0xFF5555, "Höchster Turm am Strip"},
                {-60.5, 179.5, "Tropicana Ave", 0xBBBBBB, null},
        };
        for (Object[] o : s) {
            boolean small = ((String) o[2]).contains("Ave");
            sign(w, (double) o[0], G + (small ? 4.5 : 15.5), (double) o[1], neonText((String) o[2], TextColor.color((int) o[3]), (String) o[4]), small ? 2.0f : 5.0f);
        }
    }

    // ---------------------------------------------------------------- Bauen

    void build(Plugin plugin, Runnable done) {
        List<Runnable> steps = new ArrayList<>();
        for (int z = -340; z <= 345; z += 100) { int a = z; steps.add(() -> road(a, Math.min(345, a + 99))); }
        steps.add(() -> crossStreet(177, 182, -115, 120));
        steps.add(this::welcomeSign);
        steps.add(this::mandalayBay);
        steps.add(this::luxor);
        steps.add(this::excalibur);
        steps.add(this::newYork);
        steps.add(this::mgmGrand);
        steps.add(this::bellagio);
        steps.add(this::paris);
        steps.add(this::highRoller);
        steps.add(this::caesars);
        steps.add(this::mirage);
        steps.add(this::venetian);
        steps.add(this::sphereVenue);
        steps.add(this::wynn);
        steps.add(this::strat);
        steps.add(() -> filler(20, 128, 70, 172, 1));
        steps.add(() -> filler(20, 186, 75, 315, 2));
        steps.add(() -> filler(-80, -330, -20, -175, 3));
        steps.add(() -> filler(20, -276, 34, -262, 4));
        steps.add(this::signs);
        final int[] i = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (i[0] >= steps.size()) { task.cancel(); done.run(); return; }
            steps.get(i[0]++).run();
        }, 1L, 2L);
    }

    // ---------------------------------------------------------------- Shows

    /** Bellagio-Wasserspiele und Mirage-Vulkan – nur wenn jemand in der Welt ist. */
    static void startShows(Plugin plugin, String worldName) {
        final int[] sec = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World w = Bukkit.getWorld(worldName);
            if (w == null || w.getPlayers().isEmpty()) return;
            sec[0]++;
            if (sec[0] % 90 == 0) fountainShow(plugin, w);
            if (sec[0] % 120 == 45) volcano(plugin, w);
        }, 20L, 20L);
    }

    private static void fountainShow(Plugin plugin, World w) {
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(new Location(w, -29, G, 20)) < 200 * 200)
            p.sendActionBar(Component.text("✦ Die Bellagio-Wasserspiele beginnen!", NamedTextColor.AQUA));
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (++t[0] > 200) { task.cancel(); return; }
            for (int i = 0; i < 22; i++) {
                double z = -22 + i * 3.9;
                double phase = t[0] * 0.09 + (t[0] > 120 ? i * 0.6 : i * 0.25);
                double h = 4 + 16 * Math.abs(Math.sin(phase));
                double x = -29 + (i % 2 == 0 ? -4 : 4) * (t[0] > 60 && t[0] < 120 ? 1 : 0);
                for (double y = 0; y < h; y += 1.0) w.spawnParticle(Particle.CLOUD, x, G + 1 + y, z, 1, 0.08, 0.05, 0.08, 0.0);
                w.spawnParticle(Particle.SPLASH, x, G + 1 + h, z, 6, 0.4, 0.2, 0.4, 0.0);
            }
            if (t[0] % 40 == 1) w.playSound(new Location(w, -29, G + 2, 20), Sound.BLOCK_NOTE_BLOCK_CHIME, 3f, 0.8f + (t[0] % 120) / 200f);
        }, 1L, 2L);
    }

    private static void volcano(Plugin plugin, World w) {
        Location top = new Location(w, -27.5, G + 14, -131.5);
        w.playSound(top, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (++t[0] > 40) { task.cancel(); return; }
            w.spawnParticle(Particle.LAVA, top, 25, 1.2, 0.5, 1.2, 0.2);
            w.spawnParticle(Particle.FLAME, top.clone().add(0, 2, 0), 40, 1.5, 3, 1.5, 0.08);
            w.spawnParticle(Particle.LARGE_SMOKE, top.clone().add(0, 5, 0), 15, 1.5, 2, 1.5, 0.03);
            if (t[0] % 10 == 0) w.playSound(top, Sound.BLOCK_FIRE_AMBIENT, 4f, 0.6f);
        }, 1L, 4L);
    }
}

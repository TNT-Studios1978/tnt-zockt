package ch.tntzockt.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Baut die Lobby als Schloss auf einer schwebenden Insel – mit vier Portalen
 * (Survival, Farmwelt, Grundstücke, Kreativ) im Burghof.
 */
final class LobbyCastle {

    static final int G = Generators.SURFACE; // Bodenhöhe (64)
    static final String TAG = "tnt_lobby";

    /** Portal: Mitte x, z (Ebene), Zielwelt, Name, Farbe, Rahmenmaterial. */
    record Portal(int x, int z, boolean alongX, String world, String name, NamedTextColor color, Material frame, String sub) {
        boolean contains(int bx, int by, int bz) {
            if (by < G + 1 || by > G + 5) return false;
            return alongX ? (bx >= x - 1 && bx <= x + 1 && bz == z) : (bz >= z - 1 && bz <= z + 1 && bx == x);
        }
    }

    private final Map<Long, BlockData> ops = new LinkedHashMap<>();
    private final Random rnd = new Random(42);

    static List<Portal> portals(String survival, String farm, String plots, String creative,
                                String adventure, String skyblock, String hardcore, String event, String minigames) {
        int z = -4;
        return List.of(
                new Portal(-15, z, true, survival, "Survival", NamedTextColor.GREEN, Material.EMERALD_BLOCK, "Die Hauptwelt"),
                new Portal(-5, z, true, farm, "Farmwelt", NamedTextColor.GRAY, Material.IRON_BLOCK, "Ressourcen abbauen"),
                new Portal(5, z, true, plots, "Grundstücke", NamedTextColor.AQUA, Material.PRISMARINE_BRICKS, "Deine eigene Parzelle"),
                new Portal(15, z, true, creative, "Kreativwelt", NamedTextColor.LIGHT_PURPLE, Material.PURPUR_BLOCK, "Freies Bauen"),
                new Portal(-18, 3, false, adventure, "Abenteuer", NamedTextColor.GOLD, Material.COPPER_BLOCK, "Riesige Berge erkunden"),
                new Portal(-18, 13, false, skyblock, "Skyblock", NamedTextColor.BLUE, Material.LAPIS_BLOCK, "Deine Insel im Himmel"),
                new Portal(18, 3, false, hardcore, "Hardcore", NamedTextColor.DARK_RED, Material.NETHER_BRICKS, "Ein Leben – Tod = 24h Pause"),
                new Portal(18, 13, false, event, "Events", NamedTextColor.YELLOW, Material.GOLD_BLOCK, "Stream-Events"),
                new Portal(0, KEEP_GATE_Z, true, minigames, "Minispiele", NamedTextColor.RED, Material.TNT, "TNT-Run · Spleef · Parkour · PvP"));
    }

    static Location spawn(World w) {
        return new Location(w, 0.5, G + 1, 14.5, 180f, 0f);
    }

    // ---------------------------------------------------------------- Bausteine

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private void set(int x, int y, int z, Material m) {
        ops.put(key(x, y, z), m.createBlockData());
        coords.put(key(x, y, z), new int[]{x, y, z});
    }

    private void set(int x, int y, int z, String data) {
        ops.put(key(x, y, z), Bukkit.createBlockData(data));
        coords.put(key(x, y, z), new int[]{x, y, z});
    }

    private final Map<Long, int[]> coords = new LinkedHashMap<>();

    private Material wallStone() {
        int r = rnd.nextInt(100);
        return r < 12 ? Material.MOSSY_STONE_BRICKS : r < 22 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS;
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
    }

    // ---------------------------------------------------------------- Teile

    private void island() {
        int R = 42;
        for (int x = -R - 3; x <= R + 3; x++) {
            for (int z = -R - 3; z <= R + 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                double edge = R + Math.sin(x * 0.31) * 2 + Math.cos(z * 0.27) * 2;
                if (d > edge) continue;
                int depth = (int) Math.round((edge - d) * 0.55 + 3 + rnd.nextInt(3));
                set(x, G, z, Material.GRASS_BLOCK);
                for (int y = G - 1; y >= G - depth; y--) {
                    Material m;
                    if (y >= G - 3) m = Material.DIRT;
                    else {
                        int r = rnd.nextInt(100);
                        m = r < 15 ? Material.ANDESITE : r < 22 ? Material.COBBLESTONE : r < 25 ? Material.MOSSY_COBBLESTONE : Material.STONE;
                    }
                    set(x, y, z, m);
                }
                // alte Plattform & Luft darüber freimachen
                for (int y = G + 1; y <= G + 3; y++) set(x, y, z, Material.AIR);
            }
        }
    }

    private void courtyard() {
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                boolean path = Math.abs(x) <= 2 || (z >= -6 && z <= -2) || Math.abs(z - 9) <= 1;
                Material m = path ? Material.POLISHED_ANDESITE : (((x + z) & 1) == 0 ? Material.STONE_BRICKS : Material.CHISELED_STONE_BRICKS);
                if (!path && rnd.nextInt(10) == 0) m = Material.MOSSY_STONE_BRICKS;
                set(x, G, z, m);
            }
        }
        // Weg vom Tor nach draussen
        for (int z = 21; z <= 38; z++) for (int x = -2; x <= 2; x++) set(x, G, z, Material.DIRT_PATH);
        // Brunnen
        int fz = 6;
        for (int x = -3; x <= 3; x++) for (int z = fz - 3; z <= fz + 3; z++) {
            double d = Math.sqrt(x * x + (z - fz) * (z - fz));
            if (d <= 3.4 && d >= 2.5) set(x, G + 1, z, Material.STONE_BRICK_WALL);
            if (d < 2.5) set(x, G, z, Material.WATER);
        }
        box(0, G, fz, 0, G + 2, fz, Material.CHISELED_STONE_BRICKS);
        set(0, G + 3, fz, Material.SEA_LANTERN);
        // Lichtpunkte im Boden
        for (int[] p : new int[][]{{-10, 12}, {10, 12}, {-18, 0}, {18, 0}, {-10, -12}, {10, -12}}) set(p[0], G, p[1], Material.SEA_LANTERN);
    }

    private void walls() {
        int h = 10, r = 22;
        for (int i = -r; i <= r; i++) {
            for (int t = 0; t < 2; t++) {
                int[][] spots = {{i, r + t}, {i, -r - t}, {r + t, i}, {-r - t, i}};
                for (int[] s : spots) {
                    for (int y = G + 1; y <= G + h; y++) set(s[0], y, s[1], wallStone());
                }
            }
            // Zinnen aussen
            if ((i & 1) == 0) {
                set(i, G + h + 1, r + 1, Material.STONE_BRICKS);
                set(i, G + h + 1, -r - 1, Material.STONE_BRICKS);
                set(r + 1, G + h + 1, i, Material.STONE_BRICKS);
                set(-r - 1, G + h + 1, i, Material.STONE_BRICKS);
            }
            // Laternen auf der Mauerinnenseite
            if (Math.floorMod(i, 6) == 0) {
                set(i, G + 7, r - 1, "minecraft:lantern[hanging=false]");
                set(i, G + 7, -r + 1, "minecraft:lantern[hanging=false]");
                set(r - 1, G + 7, i, "minecraft:lantern[hanging=false]");
                set(-r + 1, G + 7, i, "minecraft:lantern[hanging=false]");
            }
        }
        // Tor im Süden
        box(-2, G + 1, r, 2, G + 7, r + 1, Material.AIR);
        box(-3, G + 8, r, 3, G + 8, r + 1, Material.CHISELED_STONE_BRICKS);
        set(-3, G + 9, r + 1, "minecraft:lantern[hanging=false]");
        set(3, G + 9, r + 1, "minecraft:lantern[hanging=false]");
        // Laternen-Halter an der Innenseite brauchen Boden: kleine Konsolen
        for (int i = -r; i <= r; i++) {
            if (Math.floorMod(i, 6) != 0) continue;
            set(i, G + 6, r - 1, Material.STONE_BRICK_SLAB);
            set(i, G + 6, -r + 1, Material.STONE_BRICK_SLAB);
            set(r - 1, G + 6, i, Material.STONE_BRICK_SLAB);
            set(-r + 1, G + 6, i, Material.STONE_BRICK_SLAB);
        }
    }

    private void tower(int cx, int cz, int radius, int height, int roof) {
        int top = G + height;
        for (int x = cx - radius - 1; x <= cx + radius + 1; x++) {
            for (int z = cz - radius - 1; z <= cz + radius + 1; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d <= radius + 0.5) {
                    boolean shell = d > radius - 0.7;
                    for (int y = G + 1; y <= top; y++) {
                        if (shell) {
                            boolean window = (y - G) % 7 == 4 && (x == cx || z == cz);
                            set(x, y, z, window ? Material.GLOWSTONE : wallStone());
                        } else if ((y - G) % 7 == 0) {
                            set(x, y, z, Material.SPRUCE_PLANKS); // Stockwerke
                        } else {
                            set(x, y, z, Material.AIR);
                        }
                    }
                }
                // Kranz oben
                if (d <= radius + 1.5 && d > radius - 0.5) set(x, top + 1, z, Material.POLISHED_DEEPSLATE);
            }
        }
        // Spitzdach
        for (int h = 0; h <= roof; h++) {
            double r = (radius + 1.2) * (1.0 - (double) h / (roof + 1));
            for (int x = cx - radius - 2; x <= cx + radius + 2; x++)
                for (int z = cz - radius - 2; z <= cz + radius + 2; z++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    if (d <= r + 0.3) set(x, top + 2 + h, z, h % 3 == 2 ? Material.DEEPSLATE_BRICKS : Material.DEEPSLATE_TILES);
                }
        }
        int tip = top + 3 + roof;
        set(cx, tip, cz, Material.GOLD_BLOCK);
        // Fahnenmast mit TNT-Fahne (rot-weiss)
        for (int y = tip + 1; y <= tip + 5; y++) set(cx, y, cz, Material.DARK_OAK_FENCE);
        for (int i = 1; i <= 4; i++) {
            set(cx + i, tip + 5, cz, Material.RED_WOOL);
            set(cx + i, tip + 4, cz, i == 2 || i == 3 ? Material.WHITE_WOOL : Material.RED_WOOL);
            set(cx + i, tip + 3, cz, Material.RED_WOOL);
        }
    }

    private void keep() {
        // Haupthaus im Norden
        int x1 = -13, x2 = 13, z1 = -21, z2 = -9, h = 16;
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            boolean shell = x == x1 || x == x2 || z == z1 || z == z2;
            for (int y = G + 1; y <= G + h; y++) {
                if (shell) {
                    boolean window = (y - G) % 5 == 3 && (x % 4 == 0) && z == z2;
                    set(x, y, z, window ? Material.GLOWSTONE : wallStone());
                } else set(x, y, z, Material.AIR);
            }
        }
        // Satteldach
        for (int k = 0; k <= 7; k++) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                set(x, G + h + 1 + k, z1 + k - 1, Material.DEEPSLATE_TILES);
                set(x, G + h + 1 + k, z2 - k + 1, Material.DEEPSLATE_TILES);
                for (int z = z1 + k; z <= z2 - k; z++) if (x == x1 || x == x2) set(x, G + h + 1 + k, z, wallStone());
            }
        }
        // Eingang mit TNT-Schmuck
        box(-2, G + 1, z2, 2, G + 6, z2, Material.AIR);
        box(-3, G + 7, z2, 3, G + 7, z2, Material.CHISELED_STONE_BRICKS);
        set(-1, G + 8, z2 + 1, Material.TNT);
        set(0, G + 8, z2 + 1, Material.TNT);
        set(1, G + 8, z2 + 1, Material.TNT);
        set(-2, G + 8, z2 + 1, Material.STONE_BRICK_SLAB);
        set(2, G + 8, z2 + 1, Material.STONE_BRICK_SLAB);
        // Innenraum leuchtet
        set(0, G + 5, (z1 + z2) / 2, Material.SEA_LANTERN);
        // grosser Mittelturm hinter dem Haus
        tower(0, -15, 6, 30, 14);
    }

    /** Schriftzug TNT-ZOCKT aus Blöcken an der Schlossfront (Blick vom Spawn). */
    private void title() {
        String[][] glyphs = {
                {"###", ".#.", ".#.", ".#.", ".#."},          // T
                {"#..#", "##.#", "#.##", "#..#", "#..#"},     // N
                {"###", ".#.", ".#.", ".#.", ".#."},          // T
                {"...", "...", "###", "...", "..."},          // -
                {"###", "..#", ".#.", "#..", "###"},          // Z
                {"###", "#.#", "#.#", "#.#", "###"},          // O
                {"###", "#..", "#..", "#..", "###"},          // C
                {"#.#", "#.#", "##.", "#.#", "#.#"},          // K
                {"###", ".#.", ".#.", ".#.", ".#."}};         // T
        int width = -1;
        for (String[] g : glyphs) width += g[0].length() + 1;
        int xStart = -width / 2, zBack = -8, yTop = G + 21;
        // dunkle Tafel mit Goldrand
        for (int x = xStart - 2; x <= xStart + width + 1; x++) {
            for (int y = yTop - 6; y <= yTop + 1; y++) {
                boolean rim = x == xStart - 2 || x == xStart + width + 1 || y == yTop - 6 || y == yTop + 1;
                set(x, y, zBack, rim ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE);
            }
        }
        int x = xStart;
        for (int i = 0; i < glyphs.length; i++) {
            Material m = i < 3 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE;
            String[] g = glyphs[i];
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < g[row].length(); col++) {
                    if (g[row].charAt(col) == '#') set(x + col, yTop - row, zBack + 1, m);
                }
            }
            x += g[0].length() + 1;
        }
    }

    private void portal(Portal p) {
        int y0 = G;
        String axis = p.alongX() ? "x" : "z";
        for (int d = -2; d <= 2; d++) {
            int bx = p.alongX() ? p.x() + d : p.x();
            int bz = p.alongX() ? p.z() : p.z() + d;
            for (int y = y0; y <= y0 + 6; y++) {
                boolean inner = Math.abs(d) <= 1 && y >= y0 + 1 && y <= y0 + 5;
                if (inner) set(bx, y, bz, "minecraft:nether_portal[axis=" + axis + "]");
                else set(bx, y, bz, p.frame());
            }
            // Sockel davor/dahinter
            if (p.alongX()) {
                set(bx, y0, bz + 1, Material.POLISHED_ANDESITE);
                set(bx, y0, bz - 1, Material.POLISHED_ANDESITE);
            } else {
                set(bx + 1, y0, bz, Material.POLISHED_ANDESITE);
                set(bx - 1, y0, bz, Material.POLISHED_ANDESITE);
            }
        }
        if (p.alongX()) {
            set(p.x() - 3, y0 + 1, p.z(), "minecraft:lantern[hanging=false]");
            set(p.x() + 3, y0 + 1, p.z(), "minecraft:lantern[hanging=false]");
        } else {
            set(p.x(), y0 + 1, p.z() - 3, "minecraft:lantern[hanging=false]");
            set(p.x(), y0 + 1, p.z() + 3, "minecraft:lantern[hanging=false]");
        }
    }

    // ---------------------------------------------------------------- Bauen

    void build(World w, Plugin plugin, List<Portal> portals, Runnable done) {
        island();
        courtyard();
        walls();
        tower(-22, -22, 5, 22, 11);
        tower(22, -22, 5, 22, 11);
        tower(-22, 22, 5, 20, 10);
        tower(22, 22, 5, 20, 10);
        keep();
        title();
        for (Portal p : portals) portal(p);

        List<int[]> pos = new ArrayList<>(coords.values());
        List<BlockData> data = new ArrayList<>(ops.values());
        // Zuerst Chunks laden
        for (int cx = -4; cx <= 3; cx++) for (int cz = -4; cz <= 3; cz++) w.getChunkAt(cx, cz).load(true);
        final int[] i = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(pos.size(), i[0] + 6000);
            for (; i[0] < end; i[0]++) {
                int[] c = pos.get(i[0]);
                w.getBlockAt(c[0], c[1], c[2]).setBlockData(data.get(i[0]), false);
            }
            if (i[0] >= pos.size()) {
                task.cancel();
                finish(w, portals);
                done.run();
            }
        }, 1L, 1L);
    }

    static final int KEEP_GATE_Z = -9;

    /** Gut lesbare Schwebeschrift: gross, dunkler Hintergrund, immer voll beleuchtet. */
    private static void label(World w, Location l, Component text, float scale) {
        w.spawn(l, TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setDefaultBackground(false);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(185, 10, 10, 18));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setLineWidth(220);
            t.setViewRange(2.0f);
            t.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, 0, 0), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            t.addScoreboardTag(TAG);
            t.setPersistent(true);
        });
    }

    private void finish(World w, List<Portal> portals) {
        // Wasserfälle am Inselrand (mit Physik, damit sie fliessen)
        for (int[] p : new int[][]{{-38, 8}, {37, -10}, {12, 39}}) {
            w.getBlockAt(p[0], G, p[1]).setType(Material.WATER, true);
        }
        // Bäume ausserhalb der Mauern
        Random r = new Random(7);
        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 28 + r.nextDouble() * 9;
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            if (Math.abs(x) <= 3 && z > 20) continue; // Weg frei lassen
            if (w.getBlockAt(x, G, z).getType() != Material.GRASS_BLOCK) continue;
            w.generateTree(new Location(w, x, G + 1, z), r.nextInt(3) == 0 ? TreeType.TALL_REDWOOD : TreeType.REDWOOD);
        }
        // alte Schilder/Texte entfernen und neu setzen
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        for (Portal p : portals) {
            boolean keepGate = p.alongX() && p.z() == KEEP_GATE_Z;
            Location l = keepGate ? new Location(w, p.x() + 0.5, G + 10.6, p.z() + 2.2)
                    : new Location(w, p.x() + 0.5, G + 7.3, p.z() + 0.5);
            label(w, l, Component.text(p.name(), p.color(), TextDecoration.BOLD)
                    .append(Component.newline())
                    .append(Component.text(p.sub(), NamedTextColor.WHITE)), 2.2f);
        }
        label(w, new Location(w, 0.5, G + 11.5, 6.5), Component.text("TNT", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("-ZOCKT", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Lauf durch ein Portal oder tippe /welten", NamedTextColor.GOLD)), 2.8f);
        w.setSpawnLocation(spawn(w));
    }
}

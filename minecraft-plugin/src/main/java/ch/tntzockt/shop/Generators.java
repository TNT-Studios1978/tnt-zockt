package ch.tntzockt.shop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/** Welt-Generatoren: Leere Lobby, flache Kreativwelt und Grundstück-Raster. */
final class Generators {

    private Generators() { }

    static final int SURFACE = 64; // oberste Bodenschicht (Gras)

    /** Immer Ebene (Plains), damit kein Schnee/Regen-Mix entsteht. */
    static final class PlainsBiomes extends BiomeProvider {
        @Override public @NotNull Biome getBiome(@NotNull WorldInfo w, int x, int y, int z) { return Biome.PLAINS; }
        @Override public @NotNull List<Biome> getBiomes(@NotNull WorldInfo w) { return List.of(Biome.PLAINS); }
    }

    private abstract static class Base extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        // Paper 26.x fragt die Varianten mit Koordinaten ab – auch diese abschalten
        @Override public boolean shouldGenerateNoise(@NotNull WorldInfo w, @NotNull Random r, int x, int z) { return false; }
        @Override public boolean shouldGenerateSurface(@NotNull WorldInfo w, @NotNull Random r, int x, int z) { return false; }
        @Override public boolean shouldGenerateCaves(@NotNull WorldInfo w, @NotNull Random r, int x, int z) { return false; }
        @Override public boolean shouldGenerateDecorations(@NotNull WorldInfo w, @NotNull Random r, int x, int z) { return false; }
        @Override public boolean shouldGenerateMobs(@NotNull WorldInfo w, @NotNull Random r, int x, int z) { return false; }
        @Override public boolean shouldGenerateStructures(@NotNull WorldInfo w, @NotNull Random r, int x, int z) { return false; }
        @Override public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) { return new PlainsBiomes(); }
    }

    /** Leere Welt – die Lobby-Plattform baut das Plugin selbst. */
    static final class Void extends Base {
        @Override public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
            return new Location(world, 0.5, SURFACE + 1, 0.5);
        }
    }

    /** Flache Wiese (Kreativwelt). */
    static final class Flat extends Base {
        @Override
        public void generateNoise(@NotNull WorldInfo info, @NotNull Random random, int cx, int cz, @NotNull ChunkData data) {
            int min = info.getMinHeight();
            data.setRegion(0, min, 0, 16, min + 1, 16, Material.BEDROCK);
            data.setRegion(0, min + 1, 0, 16, SURFACE - 3, 16, Material.STONE);
            data.setRegion(0, SURFACE - 3, 0, 16, SURFACE, 16, Material.DIRT);
            data.setRegion(0, SURFACE, 0, 16, SURFACE + 1, 16, Material.GRASS_BLOCK);
        }
        @Override public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
            return new Location(world, 0.5, SURFACE + 1, 0.5);
        }
    }

    /** Grundstück-Raster: Parzellen aus Gras, dazwischen Wege mit Rand-Stufen. */
    static final class Plots extends Base {
        private final int size, road, cell;

        Plots(int size, int road) {
            this.size = size;
            this.road = road;
            this.cell = size + road;
        }

        @Override
        public void generateNoise(@NotNull WorldInfo info, @NotNull Random random, int cx, int cz, @NotNull ChunkData data) {
            int min = info.getMinHeight();
            data.setRegion(0, min, 0, 16, min + 1, 16, Material.BEDROCK);
            data.setRegion(0, min + 1, 0, 16, SURFACE - 3, 16, Material.STONE);
            data.setRegion(0, SURFACE - 3, 0, 16, SURFACE, 16, Material.DIRT);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int m = Math.floorMod(cx * 16 + lx, cell);
                    int n = Math.floorMod(cz * 16 + lz, cell);
                    boolean inPlot = m < size && n < size;
                    if (inPlot) {
                        data.setBlock(lx, SURFACE, lz, Material.GRASS_BLOCK);
                    } else {
                        boolean edgeM = m == size || m == cell - 1;
                        boolean edgeN = n == size || n == cell - 1;
                        boolean border = (edgeM && (n < size || edgeN)) || (edgeN && (m < size || edgeM));
                        data.setBlock(lx, SURFACE, lz, border ? Material.SMOOTH_STONE : Material.STONE_BRICKS);
                        if (border) data.setBlock(lx, SURFACE + 1, lz, Material.SMOOTH_STONE_SLAB);
                    }
                }
            }
        }

        @Override public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
            double mid = -road / 2.0;
            return new Location(world, mid, SURFACE + 1, mid);
        }
    }
}

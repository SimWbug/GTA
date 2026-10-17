package fr.cleangang.gta.city;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Pose la ville dans le monde, colonne par colonne, réparti sur plusieurs ticks
 * pour ne pas faire laguer le serveur. Les toits sont travaillés car c'est
 * la seule chose que les joueurs voient depuis la caméra.
 */
public final class CityBuilder {

    /** Hauteur vidée au-dessus du sol (le plus haut immeuble fait 9 + parapet). */
    public static final int CLEAR_HEIGHT = 26;

    private static final Material[] CAR_COLORS = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.WHITE_CONCRETE, Material.BLACK_CONCRETE,
            Material.YELLOW_CONCRETE, Material.GREEN_CONCRETE, Material.ORANGE_CONCRETE, Material.LIGHT_GRAY_CONCRETE
    };

    /** Le « $ » peint sur le toit de la planque (lignes du nord vers le sud). */
    private static final String[] DOLLAR = {
            "..#..",
            ".####",
            "#.#..",
            "#.#..",
            ".###.",
            "..#.#",
            "..#.#",
            "####.",
            "..#.."
    };

    private final Map<Material, BlockData> cache = new EnumMap<>(Material.class);
    private final CityTheme theme;
    private final BlockData leaves;

    private CityBuilder(CityTheme theme) {
        this.theme = theme;
        BlockData l = theme.leaves().createBlockData();
        if (l instanceof Leaves leafData) leafData.setPersistent(true);
        leaves = l;
    }

    /**
     * @param progress reçoit le pourcentage (0..100) au fil de la construction
     * @param done     appelé une fois la ville terminée
     */
    public static void build(Plugin plugin, World world, int ox, int oy, int oz, CityLayout layout, CityTheme theme,
                             int columnsPerTick, IntConsumer progress, Runnable done) {
        CityBuilder builder = new CityBuilder(theme);
        int total = CityLayout.SIZE * CityLayout.SIZE;
        new BukkitRunnable() {
            int index = 0;
            int lastPercent = -1;

            @Override
            public void run() {
                int end = Math.min(total, index + columnsPerTick);
                for (; index < end; index++) {
                    builder.column(world, ox, oy, oz, layout, index / CityLayout.SIZE, index % CityLayout.SIZE);
                }
                int percent = index * 100 / total;
                if (percent / 10 != lastPercent / 10) {
                    lastPercent = percent;
                    progress.accept(percent);
                }
                if (index >= total) {
                    cancel();
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private BlockData data(Material m) {
        return cache.computeIfAbsent(m, Material::createBlockData);
    }

    private static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= (h >>> 13);
        return Math.abs(h * 0x5bd1e995);
    }

    private void column(World w, int ox, int oy, int oz, CityLayout L, int x, int z) {
        // col[0] = oy-1 (sous-sol), col[1] = oy (sol), col[k] = oy-1+k
        BlockData[] col = new BlockData[CLEAR_HEIGHT + 2];
        BlockData air = data(Material.AIR);
        java.util.Arrays.fill(col, air);
        col[0] = data(Material.STONE);
        int h = hash(x, z);

        switch (L.kind(x, z)) {
            case WALL -> {
                col[1] = data(Material.STONE_BRICKS);
                for (int k = 2; k <= 4; k++) col[k] = data(Material.STONE_BRICKS);
                if ((x + z) % 8 == 0) col[4] = data(Material.CHISELED_STONE_BRICKS);
            }
            case ROAD -> col[1] = data(theme.road);
            case ROAD_LINE -> col[1] = data(theme.roadLine);
            case CROSSWALK -> col[1] = data(Material.WHITE_CONCRETE);
            case SIDEWALK -> col[1] = data(theme.sidewalk);
            case GRASS -> {
                col[1] = data(theme.ground);
                if (h % 100 < 12) col[2] = data(theme.decorations[h % theme.decorations.length]);
            }
            case PATH -> col[1] = data(theme.path);
            case WATER -> {
                col[0] = data(theme.waterBed);
                col[1] = data(Material.WATER);
            }
            case TREE -> {
                col[1] = data(theme.ground);
                int trunk = theme.tree == CityTheme.Tree.PALM ? 6 : 5;
                for (int k = 2; k <= trunk; k++) col[k] = data(theme.log());
            }
            case PARKING -> col[1] = data(theme.parking);
            case PARKING_LINE -> col[1] = data(Material.WHITE_CONCRETE);
            case PARKED_CAR -> {
                col[1] = data(theme.parking);
                col[2] = data(CAR_COLORS[L.aux(x, z) & 7]);
            }
            case PLAZA -> col[1] = data(theme.plaza);
            case BUILDING -> building(col, L, x, z, h);
        }

        // Feuillage des arbres (au-dessus des voitures : on peut s'y cacher)
        if (L.kind(x, z) != CityLayout.Kind.BUILDING && L.leaves(x, z) > 0) {
            boolean trunk = L.kind(x, z) == CityLayout.Kind.TREE;
            switch (theme.tree) {
                case OAK -> {
                    if (!trunk) col[5] = leaves;
                    col[6] = leaves;
                    if (L.leaves(x, z) == 2) col[7] = leaves;
                }
                case PALM -> {
                    // Palmes en étoile au sommet du tronc
                    if (trunk) col[7] = leaves;
                    else if (L.star(x, z)) col[6] = leaves;
                }
                case ACACIA -> col[6] = leaves; // canopée plate
            }
        }

        int bx = ox + x, bz = oz + z;
        for (int k = 0; k < col.length; k++) {
            Block b = w.getBlockAt(bx, oy - 1 + k, bz);
            if (!b.getBlockData().matches(col[k])) b.setBlockData(col[k], false);
        }
    }

    private void building(BlockData[] col, CityLayout L, int x, int z, int h) {
        CityLayout.Building b = L.buildingAt(x, z);
        int height = b.height();
        Material wall, roof, parapet;
        switch (b.special()) {
            case POLICE_HQ -> { wall = Material.WHITE_CONCRETE; roof = Material.LIGHT_BLUE_CONCRETE; parapet = Material.BLUE_CONCRETE; }
            case HIDEOUT -> { wall = Material.BROWN_TERRACOTTA; roof = Material.GRAY_CONCRETE; parapet = Material.BLACK_CONCRETE; }
            default -> {
                Material[] p = theme.palettes[b.palette() % theme.palettes.length];
                wall = p[0]; roof = p[1]; parapet = p[0];
            }
        }
        col[1] = data(wall);

        if (b.isEdge(x, z)) {
            boolean corner = (x == b.x1() || x == b.x2()) && (z == b.z1() || z == b.z2());
            for (int lvl = 1; lvl <= height; lvl++) {
                boolean window = !corner && lvl % 2 == 0 && lvl < height && h % 3 != 0;
                col[1 + lvl] = data(window ? Material.GLASS : wall);
            }
            Material top = parapet;
            if (b.special() == CityLayout.Special.POLICE_HQ && (x + z) % 2 == 0) top = Material.WHITE_CONCRETE;
            col[2 + height] = data(top);
            return;
        }

        // Intérieur : vide, puis le toit à la hauteur du bâtiment
        int roofIdx = 1 + height;
        Material roofMat = roof;
        Material onRoof = null;

        switch (b.special()) {
            case POLICE_HQ -> {
                int dx = x - (int) Math.round(b.centerX()), dz = z - (int) Math.round(b.centerZ());
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 6.3) {
                    roofMat = d >= 5.2 ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
                    boolean hBar = Math.abs(dx) == 2 && Math.abs(dz) <= 2;
                    boolean hMid = dz == 0 && Math.abs(dx) <= 2;
                    if (hBar || hMid) roofMat = Material.WHITE_CONCRETE;
                }
            }
            case HIDEOUT -> {
                roofMat = Math.floorMod(x, 3) == 0 ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE;
                int dx = x - (int) Math.round(b.centerX()) + 2, dz = z - (int) Math.round(b.centerZ()) + 4;
                if (dz >= 0 && dz < DOLLAR.length && dx >= 0 && dx < 5 && DOLLAR[dz].charAt(dx) == '#') {
                    roofMat = Material.LIME_CONCRETE;
                }
            }
            default -> {
                switch (L.roof(x, z)) {
                    case CityLayout.ROOF_AC -> onRoof = Material.IRON_BLOCK;
                    case CityLayout.ROOF_SKYLIGHT -> roofMat = Material.LIGHT_BLUE_STAINED_GLASS;
                    case CityLayout.ROOF_VENT -> onRoof = Material.HOPPER;
                    default -> { }
                }
            }
        }
        col[roofIdx] = data(roofMat);
        if (onRoof != null) col[roofIdx + 1] = data(onRoof);
    }
}

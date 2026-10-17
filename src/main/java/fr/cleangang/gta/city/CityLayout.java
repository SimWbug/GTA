package fr.cleangang.gta.city;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Plan 128x128 de la ville, calculé uniquement à partir d'une graine :
 * le même seed redonne toujours la même ville (on ne sauvegarde que le seed).
 *
 * Grille : 4 avenues de 7 de large dans chaque sens, 9 pâtés de maisons de ~32x32.
 *   pâté (0,0) = QG de la police, pâté (2,2) = planque des voleurs,
 *   puis un parc, un parking, deux pâtés coupés par une ruelle et des immeubles.
 */
public final class CityLayout {

    public static final int SIZE = 128;
    public static final int ROAD_W = 7;
    public static final int PALETTE_COUNT = 10;

    private static final int[] MAIN = {1, 41, 81, 120};
    private static final int[][] BLOCKS = {{8, 40}, {48, 80}, {88, 119}};

    public enum Kind {
        WALL, ROAD, ROAD_LINE, CROSSWALK, SIDEWALK, GRASS, PATH, WATER, TREE,
        PARKING, PARKING_LINE, PARKED_CAR, PLAZA, BUILDING
    }

    public enum Special { NONE, POLICE_HQ, HIDEOUT }

    public record Building(int x1, int z1, int x2, int z2, int height, int palette, Special special) {
        public boolean isEdge(int x, int z) {
            return x == x1 || x == x2 || z == z1 || z == z2;
        }
        public double centerX() { return (x1 + x2) / 2.0; }
        public double centerZ() { return (z1 + z2) / 2.0; }
    }

    public static final byte ROOF_AC = 1, ROOF_SKYLIGHT = 2, ROOF_VENT = 3;

    private final Kind[][] kind = new Kind[SIZE][SIZE];
    private final int[][] buildingAt = new int[SIZE][SIZE];
    /** 0 = rien, 1 = feuillage (rayon 2), 2 = feuillage + cime (rayon 1). */
    private final byte[][] leaves = new byte[SIZE][SIZE];
    /** Feuilles en étoile (palmiers) : lignes droites et diagonales autour du tronc. */
    private final byte[][] star = new byte[SIZE][SIZE];
    private final byte[][] roof = new byte[SIZE][SIZE];
    /** Couleur des voitures garées. */
    private final byte[][] aux = new byte[SIZE][SIZE];
    private final List<Building> buildings = new ArrayList<>();
    private final Random rng;

    private int[] policeCenter;
    private int[] hideoutCenter;
    private final List<int[]> driveCells = new ArrayList<>();
    private final List<int[]> cashCells = new ArrayList<>();
    private final List<int[]> policeSpawns;
    private final List<int[]> thiefSpawns;

    public CityLayout(long seed) {
        this.rng = new Random(seed);
        for (int[] row : buildingAt) java.util.Arrays.fill(row, -1);
        generate();
        policeSpawns = pickSpawns(policeCenter);
        thiefSpawns = pickSpawns(hideoutCenter);
    }

    // ------------------------------------------------------------------ génération

    private void generate() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1) kind[x][z] = Kind.WALL;
                else if (band(x) >= 0 || band(z) >= 0) kind[x][z] = Kind.ROAD;
            }
        }

        List<Integer> others = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7));
        Collections.shuffle(others, rng);
        int park = others.get(0), parking = others.get(1), alleyA = others.get(2), alleyB = others.get(3);

        for (int bi = 0; bi < 3; bi++) {
            for (int bj = 0; bj < 3; bj++) {
                int id = bi * 3 + bj;
                int x1 = BLOCKS[bi][0], x2 = BLOCKS[bi][1], z1 = BLOCKS[bj][0], z2 = BLOCKS[bj][1];
                if (id == 0) policeHq(x1, z1, x2, z2);
                else if (id == 8) hideout(x1, z1, x2, z2);
                else if (id == park) park(x1, z1, x2, z2);
                else if (id == parking) parking(x1, z1, x2, z2);
                else if (id == alleyA || id == alleyB) alleyBlock(x1, z1, x2, z2);
                else buildingBlock(x1, z1, x2, z2);
            }
        }

        roadMarkings();

        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                Kind k = kind[x][z];
                if (k == null) { kind[x][z] = Kind.SIDEWALK; k = Kind.SIDEWALK; }
                if (isRoad(k)) driveCells.add(new int[]{x, z});
                if (isRoad(k) || k == Kind.PARKING || k == Kind.PATH || k == Kind.PLAZA) cashCells.add(new int[]{x, z});
            }
        }
    }

    /** Début de l'avenue contenant v, ou -1. */
    private static int band(int v) {
        for (int s : MAIN) if (v >= s && v < s + ROAD_W) return s;
        return -1;
    }

    private void fill(int x1, int z1, int x2, int z2, Kind k) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) kind[x][z] = k;
    }

    private void ring(int x1, int z1, int x2, int z2, Kind k) {
        for (int x = x1; x <= x2; x++) { kind[x][z1] = k; kind[x][z2] = k; }
        for (int z = z1; z <= z2; z++) { kind[x1][z] = k; kind[x2][z] = k; }
    }

    private void buildingBlock(int x1, int z1, int x2, int z2) {
        ring(x1, z1, x2, z2, Kind.SIDEWALK);
        lots(x1 + 1, z1 + 1, x2 - 1, z2 - 1);
    }

    /** Pâté coupé en deux par une ruelle de 5 de large, reliée aux avenues. */
    private void alleyBlock(int x1, int z1, int x2, int z2) {
        if (rng.nextBoolean()) {
            int mid = (x1 + x2) / 2;
            fill(mid - 2, z1, mid + 2, z2, Kind.ROAD);
            buildingBlock(x1, z1, mid - 3, z2);
            buildingBlock(mid + 3, z1, x2, z2);
        } else {
            int mid = (z1 + z2) / 2;
            fill(x1, mid - 2, x2, mid + 2, Kind.ROAD);
            buildingBlock(x1, z1, x2, mid - 3);
            buildingBlock(x1, mid + 3, x2, z2);
        }
    }

    /** Découpe récursive d'un pâté en parcelles (6 à 13 de côté). */
    private void lots(int x1, int z1, int x2, int z2) {
        int w = x2 - x1 + 1, h = z2 - z1 + 1;
        if (w > 13 && (w >= h || h <= 13)) {
            int c = x1 + 6 + rng.nextInt(w - 11);
            lots(x1, z1, c - 1, z2);
            lots(c, z1, x2, z2);
            return;
        }
        if (h > 13) {
            int c = z1 + 6 + rng.nextInt(h - 11);
            lots(x1, z1, x2, c - 1);
            lots(x1, c, x2, z2);
            return;
        }
        if (w >= 5 && h >= 5 && rng.nextDouble() < 0.12) {
            fill(x1, z1, x2, z2, Kind.PLAZA);
            tree((x1 + x2) / 2, (z1 + z2) / 2);
            return;
        }
        addBuilding(x1, z1, x2, z2, 3 + rng.nextInt(7), rng.nextInt(PALETTE_COUNT), Special.NONE);
    }

    private void addBuilding(int x1, int z1, int x2, int z2, int height, int palette, Special special) {
        int index = buildings.size();
        buildings.add(new Building(x1, z1, x2, z2, height, palette, special));
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                kind[x][z] = Kind.BUILDING;
                buildingAt[x][z] = index;
            }
        }
        if (special != Special.NONE) return;
        // Détails de toit visibles depuis la caméra : clims, verrières, aérations
        int iw = x2 - x1 - 1, ih = z2 - z1 - 1;
        if (iw < 3 || ih < 3) return;
        int features = 1 + rng.nextInt(3);
        for (int i = 0; i < features; i++) {
            int type = 1 + rng.nextInt(3);
            int fw = type == ROOF_AC ? 2 : type == ROOF_SKYLIGHT ? 2 : 1;
            int fh = type == ROOF_SKYLIGHT ? 3 : fw;
            if (fw > iw || fh > ih) continue;
            int fx = x1 + 1 + rng.nextInt(iw - fw + 1);
            int fz = z1 + 1 + rng.nextInt(ih - fh + 1);
            for (int x = fx; x < fx + fw; x++) for (int z = fz; z < fz + fh; z++) roof[x][z] = (byte) type;
        }
    }

    private void park(int x1, int z1, int x2, int z2) {
        ring(x1, z1, x2, z2, Kind.SIDEWALK);
        fill(x1 + 1, z1 + 1, x2 - 1, z2 - 1, Kind.GRASS);
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        fill(mx - 1, z1 + 1, mx + 1, z2 - 1, Kind.PATH);
        fill(x1 + 1, mz - 1, x2 - 1, mz + 1, Kind.PATH);

        // Étang dans un des quatre quarts
        int qx = rng.nextBoolean() ? (x1 + mx) / 2 : (mx + x2) / 2;
        int qz = rng.nextBoolean() ? (z1 + mz) / 2 : (mz + z2) / 2;
        for (int x = qx - 5; x <= qx + 5; x++) {
            for (int z = qz - 4; z <= qz + 4; z++) {
                double dx = (x - qx) / 4.2, dz = (z - qz) / 3.2;
                if (dx * dx + dz * dz <= 1 && kind[x][z] == Kind.GRASS) kind[x][z] = Kind.WATER;
            }
        }

        // Arbres : leur feuillage cache les voitures vues du ciel
        for (int x = x1 + 2; x <= x2 - 2; x++) {
            for (int z = z1 + 2; z <= z2 - 2; z++) {
                if (rng.nextDouble() < 0.08 && grassAround(x, z) && noTreeNear(x, z, 3)) tree(x, z);
            }
        }
    }

    private boolean grassAround(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (kind[x + dx][z + dz] != Kind.GRASS) return false;
        }
        return true;
    }

    private boolean noTreeNear(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            int nx = x + dx, nz = z + dz;
            if (nx >= 0 && nz >= 0 && nx < SIZE && nz < SIZE && kind[nx][nz] == Kind.TREE) return false;
        }
        return true;
    }

    private void tree(int x, int z) {
        kind[x][z] = Kind.TREE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int nx = x + dx, nz = z + dz;
                if (nx < 1 || nz < 1 || nx >= SIZE - 1 || nz >= SIZE - 1) continue;
                if (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) star[nx][nz] = 1;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 1.5) leaves[nx][nz] = 2;
                else if (d <= 2.3 && leaves[nx][nz] == 0) leaves[nx][nz] = 1;
            }
        }
    }

    private void parking(int x1, int z1, int x2, int z2) {
        ring(x1, z1, x2, z2, Kind.SIDEWALK);
        fill(x1 + 1, z1 + 1, x2 - 1, z2 - 1, Kind.PARKING);
        // Rangées de places (5 de profondeur) séparées par des allées de 6
        for (int zs = z1 + 2; zs + 4 <= z2 - 2; zs += 11) {
            for (int z = zs; z <= zs + 4; z++) {
                for (int x = x1 + 2; x <= x2 - 2; x += 3) kind[x][z] = Kind.PARKING_LINE;
            }
            // Voitures garées = obstacles (2 de large, 3 de long)
            for (int xs = x1 + 3; xs + 1 <= x2 - 2; xs += 3) {
                if (rng.nextDouble() >= 0.4) continue;
                byte color = (byte) rng.nextInt(8);
                for (int x = xs; x <= xs + 1; x++) {
                    for (int z = zs + 1; z <= zs + 3; z++) {
                        kind[x][z] = Kind.PARKED_CAR;
                        aux[x][z] = color;
                    }
                }
            }
        }
    }

    private void policeHq(int x1, int z1, int x2, int z2) {
        ring(x1, z1, x2, z2, Kind.SIDEWALK);
        fill(x1 + 1, z1 + 1, x2 - 1, z2 - 1, Kind.PLAZA);
        addBuilding(x1 + 4, z1 + 4, x2 - 4, z2 - 4, 6, 0, Special.POLICE_HQ);
        policeCenter = new int[]{(x1 + x2) / 2, (z1 + z2) / 2};
    }

    private void hideout(int x1, int z1, int x2, int z2) {
        ring(x1, z1, x2, z2, Kind.SIDEWALK);
        fill(x1 + 1, z1 + 1, x2 - 1, z2 - 1, Kind.PARKING);
        addBuilding(x1 + 4, z1 + 5, x2 - 4, z2 - 5, 5, 0, Special.HIDEOUT);
        hideoutCenter = new int[]{(x1 + x2) / 2, (z1 + z2) / 2};
    }

    /** Ligne jaune centrale en pointillés + passages piétons avant chaque carrefour. */
    private void roadMarkings() {
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                if (kind[x][z] != Kind.ROAD) continue;
                int bx = band(x), bz = band(z);
                if (bx >= 0 && bz >= 0) continue; // carrefour
                if (bz >= 0) { // avenue est-ouest
                    if (nearBand(x)) { if (z % 2 == 0) kind[x][z] = Kind.CROSSWALK; }
                    else if (z == bz + 3 && Math.floorMod(x, 4) < 2) kind[x][z] = Kind.ROAD_LINE;
                } else if (bx >= 0) { // avenue nord-sud
                    if (nearBand(z)) { if (x % 2 == 0) kind[x][z] = Kind.CROSSWALK; }
                    else if (x == bx + 3 && Math.floorMod(z, 4) < 2) kind[x][z] = Kind.ROAD_LINE;
                }
            }
        }
    }

    private static boolean nearBand(int v) {
        return band(v - 1) >= 0 || band(v - 2) >= 0 || band(v + 1) >= 0 || band(v + 2) >= 0;
    }

    private static boolean isRoad(Kind k) {
        return k == Kind.ROAD || k == Kind.ROAD_LINE || k == Kind.CROSSWALK;
    }

    private List<int[]> pickSpawns(int[] center) {
        List<int[]> candidates = new ArrayList<>();
        for (int[] c : driveCells) {
            boolean clear = true;
            for (int dx = -1; dx <= 1 && clear; dx++) for (int dz = -1; dz <= 1 && clear; dz++) {
                if (!isRoad(kind[c[0] + dx][c[1] + dz])) clear = false;
            }
            if (clear) candidates.add(c);
        }
        candidates.sort(Comparator.comparingDouble(c -> sq(c[0] - center[0]) + sq(c[1] - center[1])));
        List<int[]> out = new ArrayList<>();
        for (int[] c : candidates) {
            boolean far = true;
            for (int[] o : out) if (sq(o[0] - c[0]) + sq(o[1] - c[1]) < 16) { far = false; break; }
            if (far) out.add(c);
            if (out.size() >= 16) break;
        }
        return out;
    }

    private static double sq(double v) { return v * v; }

    // ------------------------------------------------------------------ accès

    public Kind kind(int x, int z) { return kind[x][z]; }
    public Building buildingAt(int x, int z) {
        int i = buildingAt[x][z];
        return i < 0 ? null : buildings.get(i);
    }
    public byte leaves(int x, int z) { return leaves[x][z]; }
    public byte roof(int x, int z) { return roof[x][z]; }
    public boolean star(int x, int z) { return star[x][z] != 0; }
    public byte aux(int x, int z) { return aux[x][z]; }
    public List<int[]> driveCells() { return driveCells; }
    public List<int[]> cashCells() { return cashCells; }
    public List<int[]> policeSpawns() { return policeSpawns; }
    public List<int[]> thiefSpawns() { return thiefSpawns; }
    public int[] policeCenter() { return policeCenter; }
    public int[] hideoutCenter() { return hideoutCenter; }

    /** Lacet (yaw Minecraft) qui pointe vers le centre-ville, arrondi au quart de tour. */
    public static float yawTowardCenter(int x, int z) {
        double dx = SIZE / 2.0 - x, dz = SIZE / 2.0 - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return (float) (Math.round(yaw / 90.0) * 90.0);
    }
}

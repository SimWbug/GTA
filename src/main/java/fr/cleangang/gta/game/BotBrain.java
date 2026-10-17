package fr.cleangang.gta.game;

import fr.cleangang.gta.arena.Arena;
import fr.cleangang.gta.city.CityLayout;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Pilote automatique des bots de test.
 *
 * Navigation : grille 128x128 des cases « roulables avec de la marge » (la voiture fait ~1,2 bloc
 * de large), parcours en largeur depuis la cible, puis la voiture vise un point quelques cases
 * plus loin sur le chemin. Si elle reste coincée, elle recule en braquant avant de repartir.
 *
 * Objectifs :
 *  - voleur : fuit un policier trop proche, sinon libère les complices en prison, braque le convoi,
 *    ou va chercher les billets les plus proches ;
 *  - police : fonce sur le voleur libre le plus proche en anticipant sa trajectoire.
 */
final class BotBrain {

    private static final int N = CityLayout.SIZE;
    private static final int LOOKAHEAD = 5;
    private static final int REPLAN_TICKS = 10;

    /** Grille de navigation, calculée une fois par plan de ville. */
    private static final Map<CityLayout, boolean[][]> GRIDS = new WeakHashMap<>();

    private final Game game;
    private final GtaTeam team;
    private final Random rng = new Random();

    private final int[][] dist = new int[N][N];
    private int targetCellX = -1, targetCellZ = -1;
    private double goalX, goalZ;
    private int replanIn;
    private boolean fleeing;
    private boolean flooded;

    private int wanderUntil;
    private double wanderX, wanderZ;

    private double lastX = Double.NaN, lastZ;
    private int stuckCheckIn = 15;
    private int recovering;
    private int recoverSteer;

    BotBrain(Game game, GtaTeam team) {
        this.game = game;
        this.team = team;
    }

    void reset() {
        targetCellX = targetCellZ = -1;
        flooded = false;
        replanIn = 0;
        recovering = 0;
        lastX = Double.NaN;
    }

    // ------------------------------------------------------------------ décision

    Car.Controls decide(Car me, int tick) {
        Arena arena = game.arena();
        boolean[][] clear = grid(arena.layout());

        if (--replanIn <= 0) {
            replanIn = REPLAN_TICKS;
            chooseGoal(me, tick, arena, clear);
            int[] cell = snap(clear, (int) Math.floor(goalX - arena.ox()), (int) Math.floor(goalZ - arena.oz()));
            if (cell != null && (Math.abs(cell[0] - targetCellX) + Math.abs(cell[1] - targetCellZ) > 1 || !flooded)) {
                targetCellX = cell[0];
                targetCellZ = cell[1];
                flood(clear, targetCellX, targetCellZ);
                flooded = true;
            }
        }

        // Point à viser : quelques cases plus loin sur le chemin
        double wx = goalX, wz = goalZ;
        int cx = (int) Math.floor(me.x - arena.ox()), cz = (int) Math.floor(me.z - arena.oz());
        int[] at = nearestKnown(cx, cz);
        if (at != null && targetCellX >= 0) {
            int px = at[0], pz = at[1];
            for (int step = 0; step < LOOKAHEAD; step++) {
                int[] next = downhill(px, pz);
                if (next == null) break;
                px = next[0];
                pz = next[1];
            }
            if (dist[px][pz] > 0 || (px == targetCellX && pz == targetCellZ)) {
                wx = arena.ox() + px + 0.5;
                wz = arena.oz() + pz + 0.5;
            }
            if (px == targetCellX && pz == targetCellZ && Math.hypot(goalX - me.x, goalZ - me.z) < 6) {
                wx = goalX;
                wz = goalZ;
            }
        }

        double desired = Math.toDegrees(Math.atan2(-(wx - me.x), wz - me.z));
        double diff = wrap(desired - me.yaw);

        // Coincé contre un mur : marche arrière en braquant
        if (--stuckCheckIn <= 0) {
            stuckCheckIn = 15;
            if (!Double.isNaN(lastX) && Math.hypot(me.x - lastX, me.z - lastZ) < 0.5 && recovering == 0) {
                recovering = 16 + rng.nextInt(8);
                recoverSteer = diff >= 0 ? -1 : 1;
            }
            lastX = me.x;
            lastZ = me.z;
        }
        if (recovering > 0) {
            recovering--;
            return new Car.Controls(false, true, recoverSteer < 0, recoverSteer > 0, false, false);
        }

        double abs = Math.abs(diff);
        boolean forward = abs < 80 || Math.abs(me.speed) < 0.12;
        boolean brake = !forward || (abs > 45 && me.speed > 0.38);
        boolean right = diff > 4, left = diff < -4;

        double goalDist = Math.hypot(goalX - me.x, goalZ - me.z);
        boolean nitro = team == GtaTeam.POLICE
                ? goalDist > 6 && goalDist < 26 && abs < 12 && me.speed > 0.25
                : fleeing && goalDist > 10 && abs < 20;

        return new Car.Controls(forward && !brake, brake, left, right, nitro, false);
    }

    private void chooseGoal(Car me, int tick, Arena arena, boolean[][] clear) {
        fleeing = false;
        if (team == GtaTeam.THIEVES) {
            Car cop = nearest(me, GtaTeam.POLICE);
            if (cop != null && me.distanceTo(cop.x, cop.z) < 9 && !me.isProtected(tick)) {
                double dx = me.x - cop.x, dz = me.z - cop.z, d = Math.max(0.1, Math.hypot(dx, dz));
                goalX = me.x + dx / d * 24 + (rng.nextDouble() - 0.5) * 8;
                goalZ = me.z + dz / d * 24 + (rng.nextDouble() - 0.5) * 8;
                fleeing = true;
                return;
            }
            Prison prison = game.prison();
            if (prison != null && prison.size() > 0 && (me.distanceTo(prison.x, prison.z) < 50 || game.packets().isEmpty())) {
                goalX = prison.x;
                goalZ = prison.z;
                return;
            }
            double[] van = game.convoy();
            if (van != null) {
                goalX = van[0];
                goalZ = van[1];
                return;
            }
            CashPacket best = null;
            double bestD = Double.MAX_VALUE;
            for (CashPacket p : game.packets()) {
                double d = me.distanceTo(p.x, p.z);
                if (d < bestD) { bestD = d; best = p; }
            }
            if (best != null) {
                goalX = best.x;
                goalZ = best.z;
                return;
            }
        } else {
            Car thief = nearest(me, GtaTeam.THIEVES);
            if (thief != null) {
                // Anticipe la trajectoire du voleur
                double lead = Math.min(14, me.distanceTo(thief.x, thief.z) / 2);
                goalX = thief.x - Math.sin(Math.toRadians(thief.yaw)) * thief.speed * lead;
                goalZ = thief.z + Math.cos(Math.toRadians(thief.yaw)) * thief.speed * lead;
                return;
            }
        }
        // Rien à faire : balade en ville
        if (tick >= wanderUntil || Math.hypot(wanderX - me.x, wanderZ - me.z) < 4) {
            var cells = arena.layout().driveCells();
            int[] c = cells.get(rng.nextInt(cells.size()));
            wanderX = arena.ox() + c[0] + 0.5;
            wanderZ = arena.oz() + c[1] + 0.5;
            wanderUntil = tick + 400;
        }
        goalX = wanderX;
        goalZ = wanderZ;
    }

    /** Adversaire libre (en jeu, hors prison) le plus proche. */
    private Car nearest(Car me, GtaTeam of) {
        Car best = null;
        double bestD = Double.MAX_VALUE;
        for (Car c : game.carsView()) {
            if (c.team != of || !c.alive) continue;
            double d = me.distanceTo(c.x, c.z);
            if (d < bestD) { bestD = d; best = c; }
        }
        return best;
    }

    // ------------------------------------------------------------------ navigation

    private static boolean[][] grid(CityLayout layout) {
        synchronized (GRIDS) {
            return GRIDS.computeIfAbsent(layout, BotBrain::buildGrid);
        }
    }

    private static boolean drivable(CityLayout.Kind k) {
        return switch (k) {
            case ROAD, ROAD_LINE, CROSSWALK, SIDEWALK, PARKING, PARKING_LINE, PLAZA, PATH, GRASS -> true;
            default -> false;
        };
    }

    /** Case roulable dont les 8 voisines le sont aussi : la voiture y passe sans frotter. */
    private static boolean[][] buildGrid(CityLayout layout) {
        boolean[][] clear = new boolean[N][N];
        for (int x = 1; x < N - 1; x++) {
            for (int z = 1; z < N - 1; z++) {
                boolean ok = true;
                for (int dx = -1; dx <= 1 && ok; dx++) {
                    for (int dz = -1; dz <= 1 && ok; dz++) {
                        if (!drivable(layout.kind(x + dx, z + dz))) ok = false;
                    }
                }
                clear[x][z] = ok;
            }
        }
        return clear;
    }

    private void flood(boolean[][] clear, int tx, int tz) {
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        dist[tx][tz] = 0;
        queue.add(new int[]{tx, tz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= N || nz >= N || !clear[nx][nz] || dist[nx][nz] >= 0) continue;
                dist[nx][nz] = dist[c[0]][c[1]] + 1;
                queue.add(new int[]{nx, nz});
            }
        }
    }

    /** Case voisine la plus proche de la cible (8 directions, pour des trajectoires moins carrées). */
    private int[] downhill(int x, int z) {
        int best = dist[x][z];
        int[] out = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int nx = x + dx, nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= N || nz >= N) continue;
                int d = dist[nx][nz];
                if (d < 0) continue;
                // En diagonale, les deux cases adjacentes doivent être libres (pas de coin coupé)
                if (dx != 0 && dz != 0 && (dist[x + dx][z] < 0 || dist[x][z + dz] < 0)) continue;
                if (d < best) { best = d; out = new int[]{nx, nz}; }
            }
        }
        return out;
    }

    /** Case du chemin la plus proche de la voiture (elle peut frôler un trottoir hors grille). */
    private int[] nearestKnown(int x, int z) {
        for (int r = 0; r <= 3; r++) {
            int[] best = null;
            int bestD = Integer.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int nx = x + dx, nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= N || nz >= N || dist[nx][nz] < 0) continue;
                    if (dist[nx][nz] < bestD) { bestD = dist[nx][nz]; best = new int[]{nx, nz}; }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Case libre la plus proche d'un point (la cible peut être sur un trottoir étroit ou un toit). */
    private static int[] snap(boolean[][] clear, int x, int z) {
        x = Math.max(1, Math.min(N - 2, x));
        z = Math.max(1, Math.min(N - 2, z));
        for (int r = 0; r <= 12; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int nx = x + dx, nz = z + dz;
                    if (nx >= 0 && nz >= 0 && nx < N && nz < N && clear[nx][nz]) return new int[]{nx, nz};
                }
            }
        }
        return null;
    }

    private static double wrap(double a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }
}

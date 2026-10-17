package fr.cleangang.gta.game;

import fr.cleangang.gta.Settings;
import fr.cleangang.gta.arena.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Cinématique de fin : l'équipe gagnante posée sur l'avenue devant le QG de police
 * (victoire police) ou devant la planque (victoire voleurs), chaque joueur devant sa voiture.
 * La caméra de tous les joueurs fait un lent travelling vers le groupe.
 */
final class EndCinematic {

    /** Joueurs par rangée ; au-delà, une 2e rangée en quinconce derrière. */
    private static final int PER_ROW = 4;
    private static final double SPACING = 1.9;
    private static final double ROW_GAP = 5.0;

    private final Settings s;
    private final World world;
    private final GtaTeam winner;
    private final double cx, frontZ, y;
    private final int duration;

    private final List<Entity> props = new ArrayList<>();
    private final List<Mannequin> people = new ArrayList<>();
    private final List<Location> spots = new ArrayList<>();
    private final List<ItemDisplay> icons = new ArrayList<>();
    private final List<ItemDisplay> carsShown = new ArrayList<>();
    private ItemDisplay camera;

    EndCinematic(Settings s, Arena arena, GtaTeam winner, int durationTicks) {
        this.s = s;
        this.world = arena.world();
        this.winner = winner;
        this.duration = durationTicks;
        // Avenue nord-sud qui longe le QG (x 41..47) ou la planque (x 81..87)
        this.cx = arena.ox() + (winner == GtaTeam.POLICE ? 44.5 : 84.5);
        this.frontZ = arena.oz() + (winner == GtaTeam.POLICE ? 22 : 102);
        this.y = arena.driveY();
    }

    ItemDisplay camera() { return camera; }

    Location cameraStart() { return cameraAt(0); }

    /** Position de la caméra à l'instant t (0..1) : travelling avant qui descend doucement. */
    private Location cameraAt(double t) {
        double e = t * t * (3 - 2 * t); // ease in-out
        double z = frontZ - 17 + 8.5 * e;
        double h = 4.8 - 2.4 * e;
        float pitch = (float) (15 - 8 * e);
        return new Location(world, cx, y + h, z, 0f, pitch); // yaw 0 = regarde vers +Z (le groupe)
    }

    void start(List<Car> winners) {
        camera = world.spawn(cameraAt(0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.AIR));
            d.setTeleportDuration(2);
            d.setPersistent(false);
        });
        props.add(camera);

        int n = winners.size();
        for (int i = 0; i < n; i++) {
            Car car = winners.get(i);
            int row = i / PER_ROW;
            int inRow = Math.min(PER_ROW, n - row * PER_ROW);
            int k = i % PER_ROW;
            double stagger = row % 2 == 1 ? SPACING / 2 : 0;
            double x = cx + (k - (inRow - 1) / 2.0) * SPACING + stagger;
            double pz = frontZ + row * ROW_GAP;
            spawnPerson(car, new Location(world, x, y, pz, 180f, 0f), i);
            spawnCar(car, new Location(world, x, y, pz + 2.6, 180f, 0f));
        }
    }

    private void spawnPerson(Car car, Location at, int index) {
        // Mannequin = modèle de joueur complet avec son vrai skin
        Mannequin stand = world.spawn(at, Mannequin.class, m -> {
            if (car.player != null) m.setProfile(ResolvableProfile.resolvableProfile(car.player.getPlayerProfile()));
            m.setImmovable(true);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setPersistent(false);
            m.customName(Component.text(car.name, winner.color, TextDecoration.BOLD));
            m.setCustomNameVisible(true);
            m.setDescription(Component.text(winner == GtaTeam.POLICE ? "Police" : "Voleur", NamedTextColor.GRAY));
            m.setRotation(180f, 0f);
        });
        people.add(stand);
        spots.add(at.clone());
        props.add(stand);

        // Insigne ou masque au-dessus de la tête, comme en jeu
        if (s.customModels) {
            ItemDisplay icon = world.spawn(at.clone().add(0, 2.75, 0), ItemDisplay.class, d -> {
                d.setItemStack(Models.item(s, winner == GtaTeam.POLICE ? Models.Kind.BADGE : Models.Kind.MASK));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(Models.icon());
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(1);
                d.setPersistent(false);
            });
            icons.add(icon);
            props.add(icon);
        } else {
            icons.add(null);
        }
    }

    private void spawnCar(Car car, Location at) {
        ItemDisplay d = world.spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(Models.item(s, winner == GtaTeam.POLICE ? Models.Kind.POLICE : Models.Kind.THIEF));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(Models.car(s));
            e.setBillboard(Display.Billboard.FIXED);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
        });
        carsShown.add(d);
        props.add(d);
    }

    /**
     * Animation de joie d'un mannequin : petite flexion avant le saut, puis bras
     * levés à tour de rôle (coups de poing en l'air) au sommet du saut.
     */
    private static void animate(Mannequin m, int index, int t) {
        double phase = jumpPhase(index, t);
        boolean crouch = phase > 0.88;
        if (CAN_CROUCH) {
            Pose wanted = crouch ? Pose.SNEAKING : Pose.STANDING;
            if (m.getPose() != wanted) m.setPose(wanted, true);
        }
        int frame = Math.floorMod(t + index * 4, 12);
        if (frame == 2) m.swingMainHand();
        if (frame == 5) m.swingOffHand();
    }

    private static final boolean CAN_CROUCH = Mannequin.validPoses().contains(Pose.SNEAKING);

    /** Avancement (0..1) du saut en cours ; chaque personnage est décalé pour ne pas sauter tous ensemble. */
    private static double jumpPhase(int index, int t) {
        int period = 12;
        return Math.floorMod(t + index * 4, period) / (double) period;
    }

    /** @param t ticks écoulés depuis le début de la cinématique */
    void tick(int t) {
        if (camera == null || !camera.isValid()) return;
        camera.teleport(cameraAt(Math.min(1.0, t / (double) duration)));

        // Sauts de joie : arc de parabole de 0,9 bloc, bras qui suivent le mouvement
        for (int i = 0; i < people.size(); i++) {
            double ph = jumpPhase(i, t);
            double hop = 0.9 * 4 * ph * (1 - ph);
            Location base = spots.get(i);
            people.get(i).teleport(base.clone().add(0, hop, 0));
            animate(people.get(i), i, t);
            ItemDisplay icon = icons.get(i);
            if (icon != null) icon.teleport(base.clone().add(0, 2.75 + hop, 0));
        }

        double backZ = frontZ + 9;
        if (winner == GtaTeam.POLICE) {
            // Gyrophares qui clignotent
            if (t % 6 == 0) {
                boolean red = (t / 6) % 2 == 0;
                for (ItemDisplay d : carsShown) {
                    d.setGlowing(true);
                    d.setGlowColorOverride(red ? Color.fromRGB(255, 40, 40) : Color.fromRGB(40, 110, 255));
                }
            }
        } else if (t % 3 == 0) {
            // Pluie de billets sur les voleurs
            ItemStack bill = s.customModels ? Models.item(s, Models.Kind.CASH) : new ItemStack(Material.PAPER);
            world.spawnParticle(Particle.ITEM, new Location(world, cx, y + 7, frontZ + 1.5), 6,
                    PER_ROW * SPACING / 2, 0.5, 2.5, 0.02, bill);
        }

        if (t > 15 && t % 18 == 0) {
            double fx = cx + (((t / 18) % 3) - 1) * 5;
            firework(new Location(world, fx, y + 1, backZ));
        }
    }

    private void firework(Location at) {
        Color a = winner == GtaTeam.POLICE ? Color.fromRGB(40, 110, 255) : Color.fromRGB(60, 220, 60);
        Color b = winner == GtaTeam.POLICE ? Color.WHITE : Color.fromRGB(255, 200, 40);
        Firework fw = world.spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(a, b)
                    .withFade(Color.WHITE)
                    .flicker(true)
                    .build());
            meta.setPower(1);
            f.setFireworkMeta(meta);
            f.setPersistent(false);
        });
        props.add(fw);
    }

    Component title() {
        return winner == GtaTeam.POLICE
                ? Component.text("LA POLICE GAGNE", NamedTextColor.BLUE, TextDecoration.BOLD)
                : Component.text("LES VOLEURS S'EN SORTENT", NamedTextColor.RED, TextDecoration.BOLD);
    }

    void remove() {
        for (Entity e : props) if (e.isValid()) e.remove();
        props.clear();
        people.clear();
        spots.clear();
        icons.clear();
        carsShown.clear();
        camera = null;
    }
}

package fr.cleangang.gta.game;

import fr.cleangang.gta.Settings;
import fr.cleangang.gta.arena.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Random;

/**
 * Événements aléatoires pendant la partie (un à la fois) :
 *  - Convoi de fonds : un fourgon blindé traverse la ville ; un voleur qui le percute fait tomber le magot.
 *  - Coupure de courant : nuit noire, seules les voitures et les billets restent éclairés.
 *  - Double butin : billets doublés et apparitions plus rapides.
 *  - Alerte générale : la police roule plus vite.
 */
final class GameEvents {

    enum Type {
        CONVOY("CONVOI DE FONDS", "Un fourgon blindé traverse la ville !", NamedTextColor.GOLD),
        BLACKOUT("COUPURE DE COURANT", "La ville plonge dans le noir…", NamedTextColor.DARK_PURPLE),
        DOUBLE_CASH("DOUBLE BUTIN", "Les billets valent le double !", NamedTextColor.GREEN),
        ALERT("ALERTE GÉNÉRALE", "La police passe à la vitesse supérieure !", NamedTextColor.BLUE);

        final String title, subtitle;
        final NamedTextColor color;

        Type(String title, String subtitle, NamedTextColor color) {
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
        }
    }

    private static final int[] AVENUES = {1, 41, 81, 120};

    private final Game game;
    private final Settings s;
    private final Arena arena;
    private final World world;
    private final Random rng = new Random();

    private int nextAt;
    private Type active;
    private int endsAt;

    // Convoi
    private ItemDisplay van;
    private TextDisplay vanLabel;
    private double vanX, vanZ, vanDir;

    GameEvents(Game game, Settings s, Arena arena) {
        this.game = game;
        this.s = s;
        this.arena = arena;
        this.world = arena.world();
    }

    void schedule(int tick) {
        int min = Math.max(10, s.eventIntervalMin), max = Math.max(min, s.eventIntervalMax);
        nextAt = tick + (min + rng.nextInt(max - min + 1)) * 20;
    }

    Type active() { return active; }

    int secondsLeft(int tick) { return Math.max(0, (endsAt - tick) / 20); }

    double cashMultiplier() { return active == Type.DOUBLE_CASH ? 2.0 : 1.0; }

    double spawnRateMultiplier() { return active == Type.DOUBLE_CASH ? 0.5 : 1.0; }

    double speedFactor(GtaTeam team) {
        return active == Type.ALERT && team == GtaTeam.POLICE ? s.alertSpeedBoost : 1.0;
    }

    /** Position du fourgon pour la flèche des voleurs, ou null. */
    double[] convoy() {
        return van != null && van.isValid() ? new double[]{vanX, vanZ} : null;
    }

    void tick(int tick, Collection<Car> cars, Collection<Player> players) {
        if (!s.eventsEnabled) return;
        if (active == null) {
            if (tick >= nextAt) begin(pick(), tick, players);
            return;
        }
        if (active == Type.CONVOY) tickConvoy(cars, players);
        if (active != null && tick >= endsAt) finish(tick, players, false);
    }

    private Type pick() {
        Type[] all = Type.values();
        return all[rng.nextInt(all.length)];
    }

    private void begin(Type type, int tick, Collection<Player> players) {
        active = type;
        endsAt = tick + s.eventDuration * 20;
        for (Player p : players) {
            p.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(type.title, type.color, TextDecoration.BOLD),
                    Component.text(type.subtitle, NamedTextColor.WHITE),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2000), java.time.Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        }
        switch (type) {
            case CONVOY -> startConvoy();
            case BLACKOUT -> {
                for (Player p : players) p.setPlayerTime(18000, false);
            }
            case DOUBLE_CASH -> {
                for (int i = 0; i < 3; i++) game.spawnCashNow();
            }
            case ALERT -> { }
        }
    }

    private void finish(int tick, Collection<Player> players, boolean silent) {
        if (active == Type.BLACKOUT) for (Player p : players) p.resetPlayerTime();
        if (active == Type.CONVOY && van != null && !silent) {
            game.announce(Component.text("Le convoi de fonds s'est échappé.", NamedTextColor.GRAY));
        }
        removeVan();
        active = null;
        schedule(tick);
    }

    // ------------------------------------------------------------------ convoi

    private void startConvoy() {
        int band = AVENUES[1 + rng.nextInt(2)];
        boolean east = rng.nextBoolean();
        // Voie de droite selon le sens de circulation
        vanZ = arena.oz() + band + (east ? 5.5 : 1.5);
        vanX = arena.ox() + (east ? 3 : 125);
        vanDir = east ? 1 : -1;
        float yaw = east ? -90f : 90f;
        Location at = new Location(world, vanX, arena.driveY(), vanZ, yaw, 0);
        van = world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(Models.item(s, Models.Kind.VAN));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(Models.car(s));
            d.setTeleportDuration(2);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(255, 200, 40));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2f);
            d.setPersistent(false);
        });
        vanLabel = world.spawn(at.clone().add(0, 2.6, 0), TextDisplay.class, t -> {
            t.text(Component.text("$" + s.convoyValue, NamedTextColor.GOLD, TextDecoration.BOLD));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setTeleportDuration(2);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
            t.setPersistent(false);
        });
        // Le convoi dure le temps de traverser la ville
        endsAt = Integer.MAX_VALUE;
    }

    private void tickConvoy(Collection<Car> cars, Collection<Player> players) {
        if (van == null) return;
        vanX += vanDir * s.convoySpeed;
        float yaw = vanDir > 0 ? -90f : 90f;
        van.teleport(new Location(world, vanX, arena.driveY(), vanZ, yaw, 0));
        vanLabel.teleport(new Location(world, vanX, arena.driveY() + 2.6, vanZ));

        for (Car c : cars) {
            if (!c.alive || c.team != GtaTeam.THIEVES) continue;
            if (c.distanceTo(vanX, vanZ) > 2.2) continue;
            // Braquage ! Le magot tombe autour du fourgon
            Location at = new Location(world, vanX, arena.driveY(), vanZ);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.1f);
            int pieces = 4;
            for (int i = 0; i < pieces; i++) {
                double a = i * Math.PI / 2 + 0.4;
                game.dropCash(at.clone().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2), s.convoyValue / pieces);
            }
            game.announce(Component.text("💰 " + c.name + " a braqué le convoi de fonds !", NamedTextColor.GOLD));
            removeVan();
            active = null;
            schedule(game.currentTick());
            return;
        }
        double rel = vanX - arena.ox();
        if (rel < 1 || rel > 127) finish(game.currentTick(), players, false);
    }

    private void removeVan() {
        if (van != null) van.remove();
        if (vanLabel != null) vanLabel.remove();
        van = null;
        vanLabel = null;
    }

    /** Fin de partie : remet la lumière et retire le fourgon. */
    void cleanup(Collection<Player> players) {
        if (active == Type.BLACKOUT) for (Player p : players) p.resetPlayerTime();
        removeVan();
        active = null;
    }
}

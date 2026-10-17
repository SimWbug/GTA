package fr.cleangang.gta.game;

import fr.cleangang.gta.Settings;
import fr.cleangang.gta.arena.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * La voiture d'un joueur. Le joueur lui-même est en spectateur et « regarde à travers »
 * une caméra (ItemDisplay invisible) placée au-dessus de la voiture et tournée vers le sol :
 * c'est ce qui donne la vue de dessus façon GTA 1. Ses touches (ZQSD, saut, sneak) sont lues
 * avec Player#getCurrentInput() et pilotent la voiture côté serveur.
 *
 * Commandes (comme dans GTA 1, la rotation est relative à la voiture, la carte reste nord en haut) :
 *   Z / W : accélérer   S : freiner puis reculer   Q / D (A / D) : tourner
 *   Espace : nitro      Sneak : klaxon / sirène
 */
final class Car {

    private final Game game;
    /** Conducteur humain, ou null pour un bot. */
    final Player player;
    /** Pilote automatique (bots de test), ou null pour un joueur. */
    final BotBrain brain;
    final UUID id;
    final String name;
    final GtaTeam team;
    private final Settings s;
    private final Arena arena;
    private final World world;

    double x, z, yaw, speed;
    private final double y;
    boolean alive = true;

    private ItemDisplay body;
    private TextDisplay tag;
    private ItemDisplay icon;
    /** Flèche 3D de guidage, visible uniquement par le conducteur. */
    private ItemDisplay pointer;
    private boolean pointerShown;
    /** Voiture rangée pendant la cinématique : plus de physique, la vue reste sur la caméra imposée. */
    private boolean parked;
    private ItemDisplay camera;
    private double camHeight;

    private int nitroTicks, nitroCooldown, crashCooldown, hornCooldown;
    private boolean jumpWasDown, sneakWasDown;
    /** Sirène de la police (Sneak pour l'allumer / l'éteindre). */
    private boolean sirenOn = true;
    private int wreckTicks = -1;

    /** Entité que le joueur doit regarder, et à partir de quel tick l'y attacher. */
    private Entity desiredTarget;
    private int attachAtTick;

    int arrests;
    int collected;
    /** Nombre d'arrestations subies pendant la partie. */
    int timesBusted;
    /** Complices libérés de prison. */
    int rescues;
    /** En cellule au QG (attend d'être libéré). */
    boolean jailed;
    private int invulnerableUntil;

    /** Touches du conducteur pour ce tick (clavier du joueur ou décision du bot). */
    record Controls(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak) {
        static final Controls NONE = new Controls(false, false, false, false, false, false);

        static Controls of(Input in) {
            return new Controls(in.isForward(), in.isBackward(), in.isLeft(), in.isRight(), in.isJump(), in.isSneak());
        }
    }

    Car(Game game, Player player, GtaTeam team, Location spawn) {
        this(game, player, null, player.getUniqueId(), player.getName(), team, spawn);
    }

    /** Voiture pilotée par un bot. */
    Car(Game game, String botName, GtaTeam team, Location spawn) {
        this(game, null, new BotBrain(game, team), UUID.randomUUID(), botName, team, spawn);
    }

    private Car(Game game, Player player, BotBrain brain, UUID id, String name, GtaTeam team, Location spawn) {
        this.game = game;
        this.player = player;
        this.brain = brain;
        this.id = id;
        this.name = name;
        this.team = team;
        this.s = game.settings();
        this.arena = game.arena();
        this.world = spawn.getWorld();
        this.x = spawn.getX();
        this.z = spawn.getZ();
        this.y = spawn.getY();
        this.yaw = spawn.getYaw();
        this.camHeight = s.cameraHeight;
    }

    // ------------------------------------------------------------------ entités

    boolean isBot() { return player == null; }

    void spawn(int currentTick) {
        spawnVisuals();
        if (isBot()) return;
        Location camLoc = cameraLocation();
        camera = world.spawn(camLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.AIR));
            d.setTeleportDuration(2);
            d.setPersistent(false);
        });

        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(camLoc);
        lookThrough(camera, currentTick);
    }

    /** Carrosserie, icône, flèche et pseudo (recréés à chaque sortie de prison). */
    private void spawnVisuals() {
        Location loc = new Location(world, x, y, z, (float) yaw, 0);
        body = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(Models.item(s, team == GtaTeam.POLICE ? Models.Kind.POLICE : Models.Kind.THIEF));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(Models.car(s));
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2f);
            d.setPersistent(false);
        });

        // Vue du ciel, le nord est en haut de l'écran : l'icône flotte juste au-dessus
        // de la voiture (côté nord) et le pseudo juste en dessous (côté sud).
        if (s.customModels) {
            icon = world.spawn(iconLocation(), ItemDisplay.class, d -> {
                d.setItemStack(Models.item(s, team == GtaTeam.POLICE ? Models.Kind.BADGE : Models.Kind.MASK));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(Models.icon());
                // Contour lumineux pour que l'icône ressorte sur le bitume
                d.setGlowing(true);
                d.setGlowColorOverride(team == GtaTeam.POLICE ? Color.fromRGB(90, 160, 255) : Color.fromRGB(255, 70, 70));
                d.setBillboard(Display.Billboard.CENTER);
                d.setTeleportDuration(2);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(2f);
                d.setPersistent(false);
            });
        }

        if (s.customModels && !isBot()) {
            pointer = world.spawn(loc.clone().add(0, 2.2, 0), ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.AIR));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(Models.arrow());
                d.setBillboard(Display.Billboard.FIXED);
                d.setTeleportDuration(2);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                d.setGlowColorOverride(team == GtaTeam.POLICE ? Color.fromRGB(255, 60, 60) : Color.fromRGB(80, 255, 80));
                d.setVisibleByDefault(false);
                d.setPersistent(false);
            });
            player.showEntity(game.plugin(), pointer);
        }

        Component label = Component.text(name, team.color);
        if (isBot()) label = Component.text("[BOT] ", NamedTextColor.GRAY).append(label);
        if (!s.customModels) {
            // Sans resource pack : symbole devant le pseudo à la place de l'icône
            label = Component.text(team == GtaTeam.POLICE ? "★ " : "☠ ", NamedTextColor.GOLD).append(label);
        }
        Component tagText = label;
        tag = world.spawn(tagLocation(), TextDisplay.class, t -> {
            t.text(tagText);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            t.setTeleportDuration(2);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
            t.setPersistent(false);
        });
    }

    /**
     * Sortie de prison : la voiture réapparaît à la planque, intouchable quelques secondes,
     * et la vue du joueur revient sur sa propre caméra.
     */
    void respawn(Location at, int currentTick, int protectionTicks) {
        if (body != null && body.isValid()) body.remove();
        if (tag != null && tag.isValid()) tag.remove();
        if (icon != null && icon.isValid()) icon.remove();
        if (pointer != null && pointer.isValid()) pointer.remove();
        x = at.getX();
        z = at.getZ();
        yaw = at.getYaw();
        speed = 0;
        alive = true;
        jailed = false;
        wreckTicks = -1;
        pointerShown = false;
        nitroTicks = 0;
        camHeight = s.cameraHeight;
        invulnerableUntil = currentTick + protectionTicks;
        spawnVisuals();
        if (brain != null) brain.reset();
        if (isBot()) return;
        if (camera != null) camera.teleport(cameraLocation());
        player.setSpectatorTarget(null);
        player.teleport(cameraLocation());
        lookThrough(camera, currentTick);
    }

    boolean isProtected(int tick) {
        return tick < invulnerableUntil;
    }

    private Location iconLocation() {
        return new Location(world, x, y + 3.0, z - 1.6);
    }

    private Location tagLocation() {
        return new Location(world, x, y + 2.4, z + 1.8);
    }

    private Location cameraLocation() {
        // Pitch 90 = regarde droit vers le sol ; yaw 180 = le nord en haut de l'écran
        return new Location(world, x, y + camHeight, z, 180f, 90f);
    }

    /** Programme l'attache de la vue du joueur (quelques ticks plus tard, le temps que le client voie l'entité). */
    void lookThrough(Entity target, int currentTick) {
        desiredTarget = target;
        attachAtTick = currentTick + 5;
    }

    private void keepCamera(int tick) {
        if (isBot()) return;
        if (desiredTarget == null || !desiredTarget.isValid() || tick < attachAtTick) return;
        if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
        if (player.getSpectatorTarget() != desiredTarget) player.setSpectatorTarget(desiredTarget);
    }

    /** Retire la voiture de la ville (début de la cinématique de fin). */
    void park() {
        parked = true;
        speed = 0;
        if (body != null) body.remove();
        if (tag != null) tag.remove();
        if (icon != null) icon.remove();
        if (pointer != null) pointer.remove();
    }

    void remove() {
        if (player != null && player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        if (body != null) body.remove();
        if (tag != null) tag.remove();
        if (icon != null) icon.remove();
        if (pointer != null) pointer.remove();
        if (camera != null) camera.remove();
    }

    // ------------------------------------------------------------------ physique

    void tick(int tick, boolean frozen) {
        keepCamera(tick);
        if (parked) return;
        if (!alive) {
            if (wreckTicks > 0 && --wreckTicks == 0 && body != null) {
                world.spawnParticle(Particle.LARGE_SMOKE, body.getLocation().add(0, 0.6, 0), 15, 0.6, 0.3, 0.6, 0.02);
                body.remove();
            } else if (wreckTicks > 0 && tick % 4 == 0 && body != null) {
                world.spawnParticle(Particle.LARGE_SMOKE, body.getLocation().add(0, 1, 0), 2, 0.3, 0.2, 0.3, 0.01);
            }
            return;
        }

        Controls in = isBot() ? (frozen ? Controls.NONE : brain.decide(this, tick)) : Controls.of(player.getCurrentInput());
        if (nitroCooldown > 0) nitroCooldown--;
        if (crashCooldown > 0) crashCooldown--;
        if (hornCooldown > 0) hornCooldown--;

        double max = (team == GtaTeam.POLICE ? s.policeMaxSpeed : s.thiefMaxSpeed) * game.speedFactor(team);
        if (isBot()) max *= s.botSpeed;

        // Clignotement blanc pendant la protection de sortie de prison
        if (body != null && invulnerableUntil > 0) {
            if (tick < invulnerableUntil) {
                body.setGlowing((tick / 4) % 2 == 0);
                body.setGlowColorOverride(Color.WHITE);
            } else if (tick == invulnerableUntil) {
                body.setGlowing(false);
            }
        }

        if (frozen) {
            speed = 0;
        } else {
            // Nitro sur appui (front montant) de la touche saut
            if (in.jump() && !jumpWasDown && nitroCooldown == 0) {
                nitroTicks = s.nitroDuration;
                nitroCooldown = s.nitroCooldown;
                world.playSound(body.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.7f);
            }
            if (nitroTicks > 0) {
                nitroTicks--;
                max *= s.nitroMultiplier;
                speed = Math.min(max, speed + s.acceleration * 2);
                world.spawnParticle(Particle.FLAME, rearPoint(), 3, 0.15, 0.05, 0.15, 0.01);
            }

            if (in.forward()) {
                speed += speed < 0 ? s.brake : s.acceleration;
            } else if (in.backward()) {
                speed -= speed > 0 ? s.brake : s.acceleration * 0.6;
            } else {
                speed *= s.friction;
                if (Math.abs(speed) < 0.004) speed = 0;
            }
            speed = Math.max(-s.reverseMaxSpeed, Math.min(max, speed));
            if (speed > max) speed = max;

        }
        jumpWasDown = in.jump();

        // Sneak : sirène on/off pour la police, klaxon pour les voleurs
        if (in.sneak() && !sneakWasDown) {
            if (team == GtaTeam.POLICE) {
                sirenOn = !sirenOn;
                if (player != null) player.sendActionBar(Component.text(sirenOn ? "Sirène allumée" : "Sirène coupée", NamedTextColor.BLUE));
                if (!sirenOn && body != null) body.setGlowing(false);
            } else if (hornCooldown == 0) {
                hornCooldown = 12;
                GtaSounds.horn(s, world, body.getLocation());
            }
        }
        sneakWasDown = in.sneak();

        // Moteur (au ralenti même à l'arrêt) et sirène
        if (tick % 11 == 0 && player != null) GtaSounds.engine(s, player, Math.abs(speed) / s.policeMaxSpeed);
        if (team == GtaTeam.POLICE && sirenOn && !frozen && tick % 32 == 0) {
            GtaSounds.siren(s, world, body.getLocation(), game);
        }

        // Direction : la voiture ne tourne que si elle roule (inversé en marche arrière)
        double steer = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
        double grip = Math.min(1.0, Math.abs(speed) / 0.15);
        double newYaw = yaw + steer * s.turnSpeed * grip * Math.signum(speed);
        if (!fits(x, z, newYaw)) newYaw = yaw;

        double rad = Math.toRadians(newYaw);
        double dx = -Math.sin(rad) * speed, dz = Math.cos(rad) * speed;
        double before = Math.abs(speed);

        if (fits(x + dx, z + dz, newYaw)) {
            x += dx;
            z += dz;
        } else if (fits(x + dx, z, newYaw)) {
            x += dx;
            speed *= 0.82;
        } else if (fits(x, z + dz, newYaw)) {
            z += dz;
            speed *= 0.82;
        } else {
            speed *= -0.25;
            if (before > 0.2 && crashCooldown == 0) {
                crashCooldown = 10;
                world.playSound(body.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
                world.spawnParticle(Particle.CRIT, frontPoint(), 8, 0.3, 0.2, 0.3, 0.1);
            }
        }
        yaw = newYaw;

        // Recul progressif de la caméra avec la vitesse (le dézoom de GTA 1)
        double targetHeight = s.cameraHeight + s.speedZoom * Math.min(1, Math.abs(speed) / s.policeMaxSpeed);
        camHeight += (targetHeight - camHeight) * 0.06;

        updateEntities();
    }

    void updateEntities() {
        if (body != null) body.teleport(new Location(world, x, y, z, (float) yaw, 0));
        if (tag != null) tag.teleport(tagLocation());
        if (icon != null) icon.teleport(iconLocation());
        if (camera != null) camera.teleport(cameraLocation());
    }

    /** La carrosserie (rectangle tourné) tient-elle à cette position sans toucher un bloc ? */
    boolean fits(double cx, double cz, double yawDeg) {
        double rad = Math.toRadians(yawDeg);
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -fz, rz = fx;
        double hl = Models.halfLength(s) - 0.05, hw = Models.halfWidth(s) - 0.05;
        double[] f = {-hl, 0, hl};
        double[] l = {-hw, hw};
        for (double a : f) {
            for (double b : l) {
                if (blocked(cx + fx * a + rx * b, cz + fz * a + rz * b)) return false;
            }
        }
        return true;
    }

    private boolean blocked(double px, double pz) {
        if (!arena.contains(px, pz)) return true;
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
        int by = (int) Math.floor(y);
        Block level = world.getBlockAt(bx, by, bz);
        if (level.isLiquid() || level.getType().isSolid()) return true;
        return world.getBlockAt(bx, by - 1, bz).isLiquid(); // pas d'étang
    }

    // ------------------------------------------------------------------ contacts

    /** Deux cercles (avant / arrière) par voiture : bonne approximation d'un rectangle. */
    boolean touches(Car o, double tolerance) {
        double r = Models.halfWidth(s) + tolerance;
        double off = Models.halfLength(s) - Models.halfWidth(s);
        for (int i = -1; i <= 1; i += 2) {
            double ax = x - Math.sin(Math.toRadians(yaw)) * off * i, az = z + Math.cos(Math.toRadians(yaw)) * off * i;
            for (int j = -1; j <= 1; j += 2) {
                double bx = o.x - Math.sin(Math.toRadians(o.yaw)) * off * j, bz = o.z + Math.cos(Math.toRadians(o.yaw)) * off * j;
                double ddx = ax - bx, ddz = az - bz;
                if (ddx * ddx + ddz * ddz < 4 * r * r) return true;
            }
        }
        return false;
    }

    /** Écarte deux voitures de la même équipe qui se chevauchent. */
    void pushApart(Car o) {
        double ddx = x - o.x, ddz = z - o.z;
        double d = Math.sqrt(ddx * ddx + ddz * ddz);
        if (d < 1e-3) { ddx = 1; ddz = 0; d = 1; }
        double push = 0.12;
        double px = ddx / d * push, pz = ddz / d * push;
        if (fits(x + px, z + pz, yaw)) { x += px; z += pz; }
        if (o.fits(o.x - px, o.z - pz, o.yaw)) { o.x -= px; o.z -= pz; }
        speed *= 0.9;
        o.speed *= 0.9;
    }

    double distanceTo(double px, double pz) {
        double ddx = x - px, ddz = z - pz;
        return Math.sqrt(ddx * ddx + ddz * ddz);
    }

    /** Arrestation : la voiture explose et reste quelques secondes en épave. */
    void wreck() {
        alive = false;
        speed = 0;
        Location at = new Location(world, x, y + 0.5, z);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        world.spawnParticle(Particle.FLAME, at, 40, 0.8, 0.4, 0.8, 0.05);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.9f);
        if (body != null) {
            body.setItemStack(Models.item(s, Models.Kind.WRECK));
            body.setGlowing(false);
        }
        if (tag != null) tag.remove();
        if (icon != null) icon.remove();
        if (pointer != null) pointer.remove();
        wreckTicks = 100;
        timesBusted++;
    }

    // ------------------------------------------------------------------ affichage

    void setSiren(boolean red) {
        if (body == null || !alive || !sirenOn) return;
        body.setGlowing(true);
        body.setGlowColorOverride(red ? Color.fromRGB(255, 40, 40) : Color.fromRGB(40, 110, 255));
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /**
     * Flèche vers une cible. L'écran a toujours le nord en haut, donc la flèche
     * suit les directions du monde : ↑ = nord, → = est, etc.
     */
    Component pointer(double tx, double tz, String what, NamedTextColor color) {
        double dx = tx - x, dz = tz - z;
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        Component text = Component.text(what + " " + dist + "m", color);
        if (pointer != null) return text; // la flèche 3D fait déjà le travail
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int idx = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return Component.text(ARROWS[idx] + " ", color, net.kyori.adventure.text.format.TextDecoration.BOLD).append(text);
    }

    /**
     * Place la flèche 3D autour de la voiture, côté cible, pointe vers la cible.
     * Elle « respire » légèrement et disparaît quand la cible est tout près.
     */
    void aim(double[] target, int tick) {
        if (pointer == null || !pointer.isValid()) return;
        double dx = target == null ? 0 : target[0] - x, dz = target == null ? 0 : target[1] - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        boolean show = alive && target != null && dist > 4;
        if (show != pointerShown) {
            pointerShown = show;
            pointer.setItemStack(show
                    ? Models.item(s, team == GtaTeam.POLICE ? Models.Kind.ARROW_THIEF : Models.Kind.ARROW_CASH)
                    : new ItemStack(Material.AIR));
        }
        if (!show) return;
        double ux = dx / dist, uz = dz / dist;
        double radius = 2.9 + 0.25 * Math.sin(tick / 3.0);
        float arrowYaw = (float) Math.toDegrees(Math.atan2(-ux, uz));
        pointer.teleport(new Location(world, x + ux * radius, y + 2.2, z + uz * radius, arrowYaw, 0));
    }

    Component hud(Component pointer) {
        Component base = hud();
        return pointer == null ? base : pointer.append(Component.text("  |  ", NamedTextColor.DARK_GRAY)).append(base);
    }

    Component hud() {
        int kmh = (int) Math.round(Math.abs(speed) * 20 * 3.6 * 2.5);
        Component nitro = nitroTicks > 0
                ? Component.text("NITRO !", NamedTextColor.GOLD)
                : nitroCooldown == 0
                ? Component.text("Nitro prête [Espace]", NamedTextColor.GREEN)
                : Component.text("Nitro " + (nitroCooldown / 20 + 1) + "s", NamedTextColor.GRAY);
        return Component.text(kmh + " km/h", NamedTextColor.WHITE)
                .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(nitro);
    }

    Location location() { return new Location(world, x, y, z); }

    private Location frontPoint() {
        double hl = Models.halfLength(s);
        return new Location(world, x - Math.sin(Math.toRadians(yaw)) * hl, y + 0.5, z + Math.cos(Math.toRadians(yaw)) * hl);
    }

    private Location rearPoint() {
        double hl = Models.halfLength(s);
        return new Location(world, x + Math.sin(Math.toRadians(yaw)) * hl, y + 0.4, z - Math.cos(Math.toRadians(yaw)) * hl);
    }
}

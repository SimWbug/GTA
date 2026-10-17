package fr.cleangang.gta.game;

import fr.cleangang.gta.Settings;
import fr.cleangang.gta.arena.Arena;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cellule devant le QG de police. Les voleurs arrêtés y attendent (leur mannequin est
 * visible dans la cellule) en regardant la zone d'en haut. Un voleur libre qui roule
 * dans le cercle libère tout le monde.
 */
final class Prison {

    static final double RADIUS = 2.6;

    private final World world;
    final double x, z;
    private final double y;
    private final double camHeight;

    private ItemDisplay camera;
    private TextDisplay label;
    private final Map<Car, Mannequin> inmates = new LinkedHashMap<>();

    Prison(Settings s, Arena arena) {
        this.world = arena.world();
        // Parvis sud du QG (case 24, 38 du plan), accessible depuis l'avenue
        this.x = arena.ox() + 24.5;
        this.z = arena.oz() + 38.5;
        this.y = arena.driveY();
        this.camHeight = s.cameraHeight + 4;
    }

    void spawn() {
        camera = world.spawn(new Location(world, x, y + camHeight, z + 3, 180f, 90f), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.AIR));
            d.setPersistent(false);
        });
        label = world.spawn(new Location(world, x, y + 3.2, z), TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(120, 60, 0, 0));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(1.8f, 1.8f, 1.8f), new Quaternionf()));
            t.setPersistent(false);
        });
        updateLabel();
    }

    ItemDisplay camera() { return camera; }

    Location cameraLocation() { return camera.getLocation(); }

    int size() { return inmates.size(); }

    boolean contains(Car c) { return inmates.containsKey(c); }

    void add(Car c) {
        if (inmates.containsKey(c)) return;
        // Les prisonniers sont rangés en cercle dans la cellule
        int i = inmates.size();
        double angle = i * 2.4;
        double r = i == 0 ? 0 : 1.1;
        Location at = new Location(world, x + Math.cos(angle) * r, y, z + Math.sin(angle) * r, 180f, 0f);
        Mannequin m = world.spawn(at, Mannequin.class, e -> {
            if (c.player != null) e.setProfile(ResolvableProfile.resolvableProfile(c.player.getPlayerProfile()));
            e.setImmovable(true);
            e.setGravity(false);
            e.setInvulnerable(true);
            e.setPersistent(false);
            e.customName(Component.text(c.name, NamedTextColor.RED));
            e.setCustomNameVisible(true);
            e.setDescription(Component.text("En cellule", NamedTextColor.GRAY));
        });
        inmates.put(c, m);
        updateLabel();
    }

    /** Vide la cellule et renvoie les voitures libérées. */
    java.util.List<Car> releaseAll() {
        java.util.List<Car> out = new java.util.ArrayList<>(inmates.keySet());
        for (Mannequin m : inmates.values()) m.remove();
        inmates.clear();
        updateLabel();
        return out;
    }

    void forget(Car c) {
        Mannequin m = inmates.remove(c);
        if (m != null) m.remove();
        updateLabel();
    }

    boolean inside(Car c) {
        return c.distanceTo(x, z) <= RADIUS;
    }

    void tick(int tick) {
        if (tick % 4 != 0) return;
        // Barreaux lumineux : cercle de particules qui tourne
        Color color = inmates.isEmpty() ? Color.fromRGB(120, 120, 120) : Color.fromRGB(255, 60, 60);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 + tick * 0.02;
            for (double h = 0.2; h <= 2.0; h += 0.6) {
                world.spawnParticle(Particle.DUST, new Location(world, x + Math.cos(a) * RADIUS, y + h, z + Math.sin(a) * RADIUS),
                        1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.3f));
            }
        }
    }

    private void updateLabel() {
        if (label == null) return;
        label.text(Component.text("PRISON", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text(inmates.isEmpty() ? "" : " (" + inmates.size() + ")", NamedTextColor.WHITE)));
    }

    void remove() {
        for (Mannequin m : inmates.values()) m.remove();
        inmates.clear();
        if (label != null) label.remove();
        if (camera != null) camera.remove();
    }
}

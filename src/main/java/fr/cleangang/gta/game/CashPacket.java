package fr.cleangang.gta.game;

import fr.cleangang.gta.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

/** Un paquet de billets posé dans la ville, qui tourne sur lui-même et brille en vert. */
final class CashPacket {

    final int value;
    final double x, z;
    private final double y;
    private final World world;
    private final ItemDisplay body;
    private final TextDisplay label;
    private float spin;
    private int age;

    CashPacket(Settings s, Location at, int value) {
        this.value = value;
        this.world = at.getWorld();
        this.x = at.getX();
        this.y = at.getY();
        this.z = at.getZ();
        this.spin = (float) (Math.random() * 360);

        body = world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(Models.item(s, Models.Kind.CASH));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(Models.cash(s));
            d.setTeleportDuration(1);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(80, 255, 80));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2f);
            d.setPersistent(false);
        });
        label = world.spawn(at.clone().add(0, 1.8, 0), TextDisplay.class, t -> {
            t.text(Component.text("$" + value, NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setPersistent(false);
        });
        world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.5, 0), 12, 0.5, 0.3, 0.5, 0);
    }

    void tick() {
        age++;
        spin = (spin + 6) % 360;
        double bob = Math.sin(age / 6.0) * 0.12;
        body.teleport(new Location(world, x, y + 0.1 + bob, z, spin, 0));
        if (age % 10 == 0) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, new Location(world, x, y + 0.6, z), 2, 0.3, 0.2, 0.3, 0);
        }
    }

    void remove() {
        body.remove();
        label.remove();
    }
}

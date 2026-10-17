package fr.cleangang.gta.arena;

import com.destroystokyo.paper.ParticleBuilder;
import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.city.CityBuilder;
import fr.cleangang.gta.city.CityLayout;
import fr.cleangang.gta.city.CityTheme;
import fr.cleangang.gta.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bâton de placement : tant qu'il est en main, le contour de la future ville (128x128,
 * centrée sur le bloc visé) s'affiche en particules avec ses avenues, le QG de police (bleu)
 * et la planque (rouge). Clic droit = pose, second clic droit sur le même bloc = confirmation.
 */
public final class CityWand implements Listener {

    private static final int RANGE = 160;
    private static final long CONFIRM_MS = 10_000;
    private static final int[] AVENUES = {4, 44, 84, 123};

    private final GtaPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private int frame;

    private record Pending(String world, int x, int y, int z, long until) {
        boolean same(Block b) {
            return b.getWorld().getName().equals(world) && b.getX() == x && b.getY() == y && b.getZ() == z;
        }
    }

    public CityWand(GtaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "city_wand");
    }

    public ItemStack create(String name, long seed, CityTheme theme) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        meta.displayName(Component.text("Bâton de ville : " + name + " (" + theme.label + ")", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Vise le sol : le contour s'affiche", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Clic droit pour poser la ville", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, name + ";" + theme.id + ";" + seed);
        stick.setItemMeta(meta);
        return stick;
    }

    /** "nom;seed" si l'objet est un bâton de ville, sinon null. */
    private String data(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** Coin nord-ouest de la ville quand elle est centrée sur ce bloc. */
    private static int originX(Block b) { return b.getX() - CityLayout.SIZE / 2; }
    private static int originZ(Block b) { return b.getZ() - CityLayout.SIZE / 2; }

    private static boolean heightOk(Block b) {
        World w = b.getWorld();
        return b.getY() + CityBuilder.CLEAR_HEIGHT < w.getMaxHeight() && b.getY() - 1 >= w.getMinHeight();
    }

    // ------------------------------------------------------------------ pose

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer();
        String data = data(p.getInventory().getItemInMainHand());
        if (data == null) return;
        e.setCancelled(true);

        if (!p.hasPermission("gta.admin")) {
            msg(p, "Tu n'as pas la permission.", NamedTextColor.RED);
            return;
        }
        Block target = p.getTargetBlockExact(RANGE);
        if (target == null) {
            msg(p, "Vise un bloc (moins de " + RANGE + " blocs).", NamedTextColor.RED);
            return;
        }
        if (!heightOk(target)) {
            msg(p, "Impossible ici : la ville a besoin de " + CityBuilder.CLEAR_HEIGHT + " blocs de hauteur libre.", NamedTextColor.RED);
            return;
        }

        String[] parts = data.split(";");
        String name = parts[0];
        CityTheme theme = parts.length >= 3 ? CityTheme.parse(parts[1]) : CityTheme.LIBERTY;
        long seed = Long.parseLong(parts[parts.length - 1]);
        if (plugin.arenas().get(name) != null || plugin.creator().isBuilding(name)) {
            msg(p, "La ville « " + name + " » existe déjà.", NamedTextColor.RED);
            return;
        }

        Pending prev = pending.get(p.getUniqueId());
        if (prev == null || !prev.same(target) || System.currentTimeMillis() > prev.until()) {
            pending.put(p.getUniqueId(), new Pending(target.getWorld().getName(), target.getX(), target.getY(),
                    target.getZ(), System.currentTimeMillis() + CONFIRM_MS));
            msg(p, "Ville centrée sur " + target.getX() + " " + target.getY() + " " + target.getZ()
                    + ". Tout ce qui se trouve dans le contour sera remplacé.", NamedTextColor.YELLOW);
            msg(p, "Clic droit à nouveau sur ce même bloc pour confirmer (10 s).", NamedTextColor.GOLD);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            return;
        }

        pending.remove(p.getUniqueId());
        ItemStack hand = p.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
        Arena arena = new Arena(name, target.getWorld().getName(), originX(target), target.getY(), originZ(target), seed, theme, null);
        plugin.creator().build(p, arena, true);
    }

    // ------------------------------------------------------------------ aperçu

    public void startPreview() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::preview, 5L, 5L);
    }

    private void preview() {
        frame++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (data(p.getInventory().getItemInMainHand()) == null) continue;
            Block target = p.getTargetBlockExact(RANGE);
            if (target == null) {
                p.sendActionBar(Component.text("Vise le sol pour placer la ville", NamedTextColor.GRAY));
                continue;
            }
            boolean ok = heightOk(target);
            Pending pend = pending.get(p.getUniqueId());
            boolean armed = pend != null && pend.same(target) && System.currentTimeMillis() <= pend.until();
            Color border = !ok ? Color.fromRGB(255, 40, 40) : armed ? Color.fromRGB(80, 255, 80) : Color.fromRGB(255, 200, 40);
            draw(p, target, border);
            p.sendActionBar(!ok
                    ? Component.text("Pas assez de hauteur ici", NamedTextColor.RED)
                    : armed
                    ? Component.text("Clic droit pour CONFIRMER", NamedTextColor.GREEN, TextDecoration.BOLD)
                    : Component.text("Centre : " + target.getX() + " " + target.getY() + " " + target.getZ() + " · clic droit pour poser", NamedTextColor.YELLOW));
        }
    }

    private void draw(Player p, Block center, Color borderColor) {
        World w = center.getWorld();
        int ox = originX(center), oz = originZ(center);
        double y = center.getY() + 1.3;
        int size = CityLayout.SIZE;
        int shift = frame % 2;

        // Contour
        for (int i = shift; i <= size; i += 2) {
            dust(p, new Location(w, ox + i, y, oz), borderColor, 2f);
            dust(p, new Location(w, ox + i, y, oz + size), borderColor, 2f);
            dust(p, new Location(w, ox, y, oz + i), borderColor, 2f);
            dust(p, new Location(w, ox + size, y, oz + i), borderColor, 2f);
        }
        // Piliers aux quatre coins
        for (int h = 0; h < 12; h += 2) {
            for (int[] c : new int[][]{{0, 0}, {size, 0}, {0, size}, {size, size}}) {
                dust(p, new Location(w, ox + c[0], y + h, oz + c[1]), borderColor, 2.5f);
            }
        }
        // Avenues
        Color road = Color.fromRGB(200, 200, 200);
        for (int a : AVENUES) {
            for (int i = 2 + shift * 2; i < size; i += 4) {
                dust(p, new Location(w, ox + a + 0.5, y, oz + i), road, 1.2f);
                dust(p, new Location(w, ox + i, y, oz + a + 0.5), road, 1.2f);
            }
        }
        // QG de police (nord-ouest) et planque des voleurs (sud-est)
        square(p, w, ox + 12, oz + 12, ox + 36, oz + 36, y, Color.fromRGB(60, 120, 255));
        square(p, w, ox + 92, oz + 93, ox + 115, oz + 114, y, Color.fromRGB(255, 60, 60));
        // Centre visé
        for (int h = 0; h < 6; h++) dust(p, center.getLocation().add(0.5, 1.2 + h * 0.5, 0.5), Color.WHITE, 1.5f);
    }

    private static void square(Player p, World w, int x1, int z1, int x2, int z2, double y, Color c) {
        for (int x = x1; x <= x2; x += 2) {
            dust(p, new Location(w, x, y, z1), c, 1.5f);
            dust(p, new Location(w, x, y, z2), c, 1.5f);
        }
        for (int z = z1; z <= z2; z += 2) {
            dust(p, new Location(w, x1, y, z), c, 1.5f);
            dust(p, new Location(w, x2, y, z), c, 1.5f);
        }
    }

    private static void dust(Player p, Location l, Color c, float size) {
        new ParticleBuilder(Particle.DUST)
                .location(l)
                .receivers(p)
                .count(1)
                .offset(0, 0, 0)
                .data(new Particle.DustOptions(c, size))
                .force(true)
                .spawn();
    }

    private static void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(Game.PREFIX.append(Component.text(text, color)));
    }
}

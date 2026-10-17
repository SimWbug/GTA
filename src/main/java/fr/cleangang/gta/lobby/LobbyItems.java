package fr.cleangang.gta.lobby;

import fr.cleangang.gta.GtaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Objets de barre d'action du mode : objet du hub, vote de la ville, quitter. */
public final class LobbyItems {

    public enum Type { HUB, VOTE, LEAVE }

    private static NamespacedKey key;

    private LobbyItems() {}

    public static void init(GtaPlugin plugin) {
        key = new NamespacedKey(plugin, "gta_item");
    }

    public static ItemStack create(GtaPlugin plugin, Type type) {
        ItemStack item;
        Component name;
        List<Component> lore;
        switch (type) {
            case HUB -> {
                // Avec le pack : la voiture de police en 3D dans la main
                item = new ItemStack(plugin.settings().customModels ? Material.PAPER : Material.MINECART);
                name = Component.text("Grand Theft Auto", NamedTextColor.GOLD, TextDecoration.BOLD);
                lore = List.of(line("Police contre voleurs, vue du ciel", NamedTextColor.GRAY),
                        line("Clic droit : choisir une ville", NamedTextColor.YELLOW));
            }
            case VOTE -> {
                item = new ItemStack(Material.MAP);
                name = Component.text("Voter pour la prochaine ville", NamedTextColor.GREEN, TextDecoration.BOLD);
                lore = List.of(line("Clic droit pour ouvrir le vote", NamedTextColor.YELLOW));
            }
            default -> {
                item = new ItemStack(Material.RED_BED);
                name = Component.text("Quitter la partie", NamedTextColor.RED, TextDecoration.BOLD);
                lore = List.of(line("Clic droit pour retourner au hub", NamedTextColor.GRAY));
            }
        }
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        if (type == Type.HUB && plugin.settings().customModels) {
            meta.setItemModel(new NamespacedKey("gta", "police_car"));
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Type de l'objet GTA, ou null si ce n'en est pas un. */
    public static Type type(ItemStack item) {
        if (item == null || key == null || !item.hasItemMeta()) return null;
        String v = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (v == null) return null;
        try {
            return Type.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}

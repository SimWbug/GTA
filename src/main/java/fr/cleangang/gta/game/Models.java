package fr.cleangang.gta.game;

import fr.cleangang.gta.Settings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Objets affichés par les ItemDisplay. Avec le resource pack, on utilise les modèles
 * gta:police_car, gta:thief_car, gta:wreck_car et gta:cash. Sans pack, des blocs de
 * béton étirés en forme de voiture font office de secours.
 */
final class Models {

    enum Kind {
        POLICE("police_car", Material.LIGHT_BLUE_CONCRETE),
        THIEF("thief_car", Material.RED_CONCRETE),
        WRECK("wreck_car", Material.BLACK_CONCRETE),
        CASH("cash", Material.EMERALD_BLOCK),
        BADGE("badge", Material.AIR),
        MASK("mask", Material.AIR),
        ARROW_CASH("arrow_cash", Material.AIR),
        ARROW_THIEF("arrow_thief", Material.AIR),
        VAN("armored_van", Material.WHITE_CONCRETE);

        final String id;
        final Material fallback;

        Kind(String id, Material fallback) {
            this.id = id;
            this.fallback = fallback;
        }
    }

    private Models() {}

    static ItemStack item(Settings s, Kind kind) {
        if (!s.customModels) return new ItemStack(kind.fallback);
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("gta", kind.id));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Transformation d'une voiture : le modèle est posé sur la route, avant vers +Z. */
    static Transformation car(Settings s) {
        float k = (float) s.carScale;
        if (s.customModels) {
            return new Transformation(new Vector3f(0, 0.5f * k, 0), new Quaternionf(),
                    new Vector3f(k, k, k), new Quaternionf());
        }
        Vector3f scale = new Vector3f(0.85f * k, 0.45f * k, 1.6f * k);
        return new Transformation(new Vector3f(0, scale.y / 2f, 0), new Quaternionf(), scale, new Quaternionf());
    }

    static Transformation cash(Settings s) {
        float k = s.customModels ? 1.3f : 0.6f;
        return new Transformation(new Vector3f(0, k / 2f, 0), new Quaternionf(),
                new Vector3f(k, k, k), new Quaternionf());
    }

    /** Icône d'équipe (insigne / masque) : sprite plat tourné vers la caméra. */
    static Transformation icon() {
        return new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf());
    }

    /** Flèche de guidage (couchée, pointe vers +Z, orientée par le yaw de l'entité). */
    static Transformation arrow() {
        return new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf());
    }

    /** Demi-largeur et demi-longueur de la carrosserie en blocs (pour les collisions). */
    static double halfWidth(Settings s) { return 7.0 / 16.0 * s.carScale; }

    static double halfLength(Settings s) { return 13.0 / 16.0 * s.carScale; }
}

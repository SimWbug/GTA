package fr.cleangang.gta.arena;

import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.city.CityBuilder;
import fr.cleangang.gta.city.CityLayout;
import fr.cleangang.gta.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/** Lance la construction d'une ville (commande /gta create, /gta regen ou bâton de placement). */
public final class ArenaCreator {

    private final GtaPlugin plugin;
    private final Set<String> building = new HashSet<>();

    public ArenaCreator(GtaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBuilding(String name) {
        return building.contains(name.toLowerCase());
    }

    private static void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Game.PREFIX.append(Component.text(text, color)));
    }

    /**
     * @param register true pour une nouvelle arène (enregistrée à la fin), false pour une reconstruction
     */
    public void build(CommandSender sender, Arena arena, boolean register) {
        String key = arena.name().toLowerCase();
        if (!building.add(key)) {
            msg(sender, "Cette ville est déjà en construction.", NamedTextColor.RED);
            return;
        }
        if (arena.world() == null) {
            building.remove(key);
            msg(sender, "Monde introuvable.", NamedTextColor.RED);
            return;
        }
        int maxY = arena.oy() + CityBuilder.CLEAR_HEIGHT;
        if (maxY >= arena.world().getMaxHeight() || arena.oy() - 1 < arena.world().getMinHeight()) {
            building.remove(key);
            msg(sender, "Hauteur invalide pour construire la ville ici.", NamedTextColor.RED);
            return;
        }
        msg(sender, "Construction de « " + arena.name() + " » — " + arena.theme().label
                + " (128x128, seed " + arena.seed() + ")…", NamedTextColor.GOLD);
        CityLayout layout = arena.layout();
        CityBuilder.build(plugin, arena.world(), arena.ox(), arena.oy(), arena.oz(), layout, arena.theme(),
                plugin.settings().columnsPerTick,
                percent -> msg(sender, "Ville : " + percent + "%", NamedTextColor.GRAY),
                () -> {
                    building.remove(key);
                    if (register) plugin.arenas().add(arena);
                    msg(sender, "Ville « " + arena.name() + " » prête ! /gta join " + arena.name(), NamedTextColor.GREEN);
                    if (sender instanceof Player p && p.isOnline()) p.teleport(arena.lobby());
                });
    }
}

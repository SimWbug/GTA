package fr.cleangang.gta.lobby;

import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.arena.Arena;
import fr.cleangang.gta.city.CityTheme;
import fr.cleangang.gta.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu des villes, en deux modes :
 *  JOIN : ouvert par l'objet du hub, clic = rejoindre la ville ;
 *  VOTE : après une partie, clic = voter pour la prochaine ville.
 */
public final class CityMenu implements Listener {

    public enum Mode { JOIN, VOTE }

    private static final class Holder implements InventoryHolder {
        final Mode mode;
        final List<Arena> slots = new ArrayList<>();
        Inventory inventory;

        Holder(Mode mode) { this.mode = mode; }

        @Override public Inventory getInventory() { return inventory; }
    }

    private final GtaPlugin plugin;

    public CityMenu(GtaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void open(GtaPlugin plugin, Player p, Mode mode) {
        List<Arena> arenas = new ArrayList<>(plugin.arenas().all());
        if (arenas.isEmpty()) {
            p.sendMessage(Game.PREFIX.append(Component.text("Aucune ville n'est encore construite.", NamedTextColor.GRAY)));
            return;
        }
        Holder h = new Holder(mode);
        int size = Math.min(54, ((arenas.size() + 8) / 9) * 9 + 9);
        Inventory inv = Bukkit.createInventory(h, size, Component.text(
                mode == Mode.VOTE ? "Vote — prochaine ville" : "GTA — Choisis ta ville", NamedTextColor.DARK_GRAY));
        h.inventory = inv;
        fill(plugin, h, p);
        p.openInventory(inv);
    }

    private static void fill(GtaPlugin plugin, Holder h, Player viewer) {
        Inventory inv = h.inventory;
        inv.clear();
        h.slots.clear();
        List<Arena> arenas = new ArrayList<>(plugin.arenas().all());
        Game voting = h.mode == Mode.VOTE ? plugin.arenas().gameOf(viewer) : null;
        int slot = 0;
        for (Arena a : arenas) {
            if (slot >= inv.getSize()) break;
            Game g = plugin.arenas().game(a);
            List<Component> lore = new ArrayList<>();
            lore.add(text(a.theme().label, NamedTextColor.GRAY));
            if (h.mode == Mode.VOTE && voting != null) {
                int votes = voting.votesFor(a);
                boolean mine = a.equals(voting.voteOf(viewer));
                lore.add(text("Votes : " + votes, NamedTextColor.YELLOW));
                lore.add(text(mine ? "✔ Ton vote" : "Clic : voter", mine ? NamedTextColor.GREEN : NamedTextColor.GOLD));
            } else {
                lore.add(text(stateLabel(g), stateColor(g)));
                lore.add(text("Joueurs : " + g.size() + "/" + plugin.settings().maxPlayers, NamedTextColor.WHITE));
                lore.add(text("Clic : rejoindre", NamedTextColor.GOLD));
            }
            inv.setItem(slot, item(icon(a.theme()), text(a.name(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD), lore));
            h.slots.add(a);
            slot++;
        }
    }

    private static String stateLabel(Game g) {
        return switch (g.state()) {
            case WAITING -> "En attente";
            case COUNTDOWN -> "Départ imminent";
            case RUNNING, ENDING -> "Partie en cours";
            case VOTING -> "Vote en cours";
        };
    }

    private static NamedTextColor stateColor(Game g) {
        return switch (g.state()) {
            case WAITING, COUNTDOWN -> NamedTextColor.GREEN;
            default -> NamedTextColor.RED;
        };
    }

    private static Material icon(CityTheme t) {
        return switch (t) {
            case LIBERTY -> Material.BRICKS;
            case VICE -> Material.PINK_CONCRETE;
            case SAN_ANDREAS -> Material.RED_SANDSTONE;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= h.slots.size()) return;
        Arena a = h.slots.get(slot);

        if (h.mode == Mode.VOTE) {
            Game g = plugin.arenas().gameOf(p);
            if (g == null || !g.vote(p, a)) {
                p.closeInventory();
                return;
            }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
            fill(plugin, h, p);
        } else {
            p.closeInventory();
            if (plugin.arenas().gameOf(p) != null) {
                p.sendMessage(Game.PREFIX.append(Component.text("Tu es déjà dans une partie.", NamedTextColor.RED)));
                return;
            }
            plugin.arenas().game(a).join(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private static Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(m);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}

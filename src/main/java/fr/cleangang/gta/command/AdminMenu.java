package fr.cleangang.gta.command;

import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Menu des réglages (/gta admin). Clic gauche = augmenter, clic droit = diminuer,
 * Maj + clic = grand pas. Chaque changement est écrit dans config.yml et s'applique
 * à la partie suivante.
 */
public final class AdminMenu implements Listener {

    private record Option(int slot, Material icon, String path, String label, int min, int max,
                          int step, int bigStep, IntFunction<String> format) {}

    private static final List<Option> OPTIONS = List.of(
            new Option(10, Material.BLUE_CONCRETE, "game.players-per-police", "Équipes : 1 policier pour…",
                    2, 6, 1, 1, v -> v + " joueurs"),
            new Option(11, Material.OAK_DOOR, "game.max-players", "Joueurs maximum",
                    4, 16, 1, 4, v -> v + " joueurs"),
            new Option(12, Material.OAK_PRESSURE_PLATE, "game.min-players", "Joueurs minimum pour lancer",
                    2, 16, 1, 2, v -> v + " joueurs"),
            new Option(13, Material.CLOCK, "game.duration", "Durée de la partie",
                    60, 1200, 30, 120, AdminMenu::time),
            new Option(14, Material.EMERALD, "money.target-base", "Butin de base",
                    0, 20000, 250, 1000, v -> "$" + v),
            new Option(15, Material.GOLD_INGOT, "money.target-per-thief", "Butin en plus par voleur",
                    0, 5000, 250, 1000, v -> "$" + v),
            new Option(16, Material.LEAD, "game.police-release-delay", "Avance des voleurs",
                    0, 30, 1, 5, v -> v + " s")
    );

    private static final int CLOSE_SLOT = 22;
    private static final int TEST_POLICE_SLOT = 19, TEST_THIEF_SLOT = 20, TEST_AUTO_SLOT = 21;
    private static final int INFO_SLOT = 4;

    /** Marque l'inventaire comme étant ce menu. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final GtaPlugin plugin;

    public AdminMenu(GtaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void open(GtaPlugin plugin, Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("GTA — Réglages", NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        fill(plugin, inv);
        p.openInventory(inv);
    }

    private static void fill(GtaPlugin plugin, Inventory inv) {
        Settings s = plugin.settings();
        FileConfiguration c = plugin.getConfig();

        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        for (Option o : OPTIONS) {
            int value = c.getInt(o.path());
            List<Component> lore = new ArrayList<>();
            lore.add(line("Valeur : ", NamedTextColor.GRAY).append(text(o.format().apply(value), NamedTextColor.YELLOW)));
            lore.add(Component.empty());
            if (o.path().equals("game.players-per-police")) {
                lore.add(line("Répartition (police / voleurs) :", NamedTextColor.GRAY));
                for (int n : new int[]{2, 4, 6, 9, 12}) {
                    if (n > s.maxPlayers) break;
                    int cops = Math.max(1, Math.min(n - 1, n / value));
                    lore.add(line("  " + n + " joueurs → ", NamedTextColor.DARK_GRAY)
                            .append(text(cops + " flic(s)", NamedTextColor.BLUE))
                            .append(text(" / ", NamedTextColor.DARK_GRAY))
                            .append(text((n - cops) + " voleur(s)", NamedTextColor.RED)));
                }
                lore.add(Component.empty());
            }
            if (o.path().startsWith("money.")) {
                lore.add(line("Objectif à 4 voleurs : ", NamedTextColor.GRAY)
                        .append(text("$" + (s.targetBase + 4 * s.targetPerThief), NamedTextColor.GREEN)));
                lore.add(Component.empty());
            }
            lore.add(line("Clic gauche : +" + o.format().apply(o.step()), NamedTextColor.GREEN));
            lore.add(line("Clic droit : −" + o.format().apply(o.step()), NamedTextColor.RED));
            if (o.bigStep() != o.step()) {
                lore.add(line("Maj + clic : ±" + o.format().apply(o.bigStep()), NamedTextColor.GOLD));
            }
            inv.setItem(o.slot(), item(o.icon(), text(o.label(), NamedTextColor.GOLD), lore));
        }

        inv.setItem(INFO_SLOT, item(Material.MAP, text("Grand Theft Auto", NamedTextColor.GOLD), List.of(
                line("Les changements s'appliquent", NamedTextColor.GRAY),
                line("à la prochaine partie.", NamedTextColor.GRAY))));
        inv.setItem(CLOSE_SLOT, item(Material.BARRIER, text("Fermer", NamedTextColor.RED), List.of()));
        inv.setItem(TEST_POLICE_SLOT, item(Material.LIGHT_BLUE_CONCRETE, text("Tester en policier", NamedTextColor.BLUE),
                List.of(line("Partie immédiate contre 3 bots voleurs", NamedTextColor.GRAY))));
        inv.setItem(TEST_THIEF_SLOT, item(Material.RED_CONCRETE, text("Tester en voleur", NamedTextColor.RED),
                List.of(line("Partie immédiate contre 2 bots policiers", NamedTextColor.GRAY),
                        line("(+ 1 bot voleur complice)", NamedTextColor.DARK_GRAY))));
        inv.setItem(TEST_AUTO_SLOT, item(Material.COMMAND_BLOCK, text("Test complet (11 bots)", NamedTextColor.GOLD),
                List.of(line("Partie à 12 avec équipes automatiques", NamedTextColor.GRAY))));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() == CLOSE_SLOT) {
            p.closeInventory();
            return;
        }
        if (!p.hasPermission("gta.admin")) return;
        String test = switch (e.getRawSlot()) {
            case TEST_POLICE_SLOT -> "gta test 3 police";
            case TEST_THIEF_SLOT -> "gta test 3 voleur";
            case TEST_AUTO_SLOT -> "gta test 11";
            default -> null;
        };
        if (test != null) {
            p.closeInventory();
            p.performCommand(test);
            return;
        }

        for (Option o : OPTIONS) {
            if (o.slot() != e.getRawSlot()) continue;
            ClickType click = e.getClick();
            int delta = click.isShiftClick() ? o.bigStep() : o.step();
            if (click.isRightClick()) delta = -delta;
            else if (!click.isLeftClick()) return;

            FileConfiguration c = plugin.getConfig();
            int value = clamp(c.getInt(o.path()) + delta, o.min(), o.max());
            // Garder min ≤ max
            if (o.path().equals("game.min-players")) value = Math.min(value, c.getInt("game.max-players"));
            if (o.path().equals("game.max-players")) value = Math.max(value, c.getInt("game.min-players"));
            c.set(o.path(), value);
            plugin.applyConfig();

            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, delta > 0 ? 1.4f : 0.9f);
            fill(plugin, e.getInventory());
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ utilitaires

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String time(int seconds) {
        return seconds % 60 == 0 ? (seconds / 60) + " min" : (seconds / 60) + " min " + (seconds % 60) + " s";
    }

    private static Component text(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component line(String s, NamedTextColor color) {
        return text(s, color);
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

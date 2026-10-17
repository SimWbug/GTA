package fr.cleangang.gta.lobby;

import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Objet GTA dans la barre d'action du hub (case configurable), et usage des objets
 * du lobby GTA (vote de la ville, quitter). Ces objets ne peuvent pas être jetés ni déplacés.
 */
public final class HubListener implements Listener {

    private final GtaPlugin plugin;

    public HubListener(GtaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Donne l'objet du hub si le joueur est dans un monde du hub et pas en partie. */
    public void giveHubItem(Player p) {
        if (!plugin.settings().hubEnabled) return;
        if (plugin.arenas().gameOf(p) != null) return;
        if (!plugin.settings().hubWorlds.contains(p.getWorld().getName())) return;
        ItemStack current = p.getInventory().getItem(plugin.settings().hubSlot);
        if (LobbyItems.type(current) == LobbyItems.Type.HUB) return;
        p.getInventory().setItem(plugin.settings().hubSlot, LobbyItems.create(plugin, LobbyItems.Type.HUB));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Un tick plus tard, pour passer après les autres plugins du hub qui remplissent la barre
        Bukkit.getScheduler().runTaskLater(plugin, () -> giveHubItem(e.getPlayer()), 2L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> giveHubItem(e.getPlayer()), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> giveHubItem(e.getPlayer()), 2L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        LobbyItems.Type type = LobbyItems.type(p.getInventory().getItemInMainHand());
        if (type == null) return;
        e.setCancelled(true);
        switch (type) {
            case HUB -> CityMenu.open(plugin, p, CityMenu.Mode.JOIN);
            case VOTE -> {
                Game g = plugin.arenas().gameOf(p);
                if (g != null && g.state() == Game.State.VOTING) CityMenu.open(plugin, p, CityMenu.Mode.VOTE);
            }
            case LEAVE -> {
                Game g = plugin.arenas().gameOf(p);
                if (g != null) g.leave(p, false);
                Bukkit.getScheduler().runTaskLater(plugin, () -> giveHubItem(p), 2L);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (LobbyItems.type(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (LobbyItems.type(e.getMainHandItem()) != null || LobbyItems.type(e.getOffHandItem()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryClickEvent e) {
        if (LobbyItems.type(e.getCurrentItem()) != null || LobbyItems.type(e.getCursor()) != null) e.setCancelled(true);
    }
}

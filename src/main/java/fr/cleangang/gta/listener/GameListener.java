package fr.cleangang.gta.listener;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class GameListener implements Listener {

    private final GtaPlugin plugin;

    public GameListener(GtaPlugin plugin) {
        this.plugin = plugin;
    }

    private Game game(Player p) {
        return plugin.arenas().gameOf(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Game g = game(e.getPlayer());
        if (g != null) g.leave(e.getPlayer(), true);
    }

    /** Le sneak sert de klaxon : il ne doit pas faire sortir le joueur de sa caméra. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent e) {
        Game g = game(e.getPlayer());
        if (g != null && (g.state() == Game.State.RUNNING || g.state() == Game.State.ENDING)) e.setCancelled(true);
    }

    /** Pas de téléportation via le menu spectateur (touches 1-9). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && game(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && game(p) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && game(p) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (game(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (game(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (game(e.getPlayer()) != null) e.setCancelled(true);
    }
}

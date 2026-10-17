package fr.cleangang.gta.game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;

/** État du joueur avant la partie, restauré quand il quitte. */
public final class PlayerSnapshot {

    private final Location location;
    private final GameMode gameMode;
    private final ItemStack[] contents;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final boolean allowFlight;
    private final boolean flying;
    private final Collection<PotionEffect> effects;
    private final Scoreboard scoreboard;

    private PlayerSnapshot(Player p) {
        location = p.getLocation().clone();
        gameMode = p.getGameMode();
        contents = p.getInventory().getContents().clone();
        for (int i = 0; i < contents.length; i++) if (contents[i] != null) contents[i] = contents[i].clone();
        health = p.getHealth();
        food = p.getFoodLevel();
        saturation = p.getSaturation();
        level = p.getLevel();
        exp = p.getExp();
        allowFlight = p.getAllowFlight();
        flying = p.isFlying();
        effects = new ArrayList<>(p.getActivePotionEffects());
        scoreboard = p.getScoreboard();
    }

    public static PlayerSnapshot capture(Player p) {
        return new PlayerSnapshot(p);
    }

    public Location location() { return location; }

    public void restore(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.setGameMode(gameMode);
        p.teleport(location);
        p.getInventory().setContents(contents);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        p.setHealth(Math.max(1, Math.min(cap, health)));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
        p.setAllowFlight(allowFlight);
        if (allowFlight) p.setFlying(flying);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.addPotionEffects(effects);
        p.setScoreboard(scoreboard != null ? scoreboard : Bukkit.getScoreboardManager().getMainScoreboard());
        p.setFireTicks(0);
        p.resetPlayerTime();
    }
}

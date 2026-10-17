package fr.cleangang.gta;

import fr.cleangang.gta.arena.ArenaCreator;
import fr.cleangang.gta.arena.ArenaManager;
import fr.cleangang.gta.arena.CityWand;
import fr.cleangang.gta.command.GtaCommand;
import fr.cleangang.gta.listener.GameListener;
import fr.cleangang.gta.stats.StatsManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GtaPlugin extends JavaPlugin {

    private Settings settings;
    private ArenaManager arenas;
    private ArenaCreator creator;
    private CityWand wand;
    private StatsManager stats;
    private fr.cleangang.gta.lobby.HubListener hub;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        arenas = new ArenaManager(this);
        arenas.load();
        creator = new ArenaCreator(this);
        stats = new StatsManager(this);
        stats.load();
        wand = new CityWand(this);
        fr.cleangang.gta.lobby.LobbyItems.init(this);

        GtaCommand command = new GtaCommand(this);
        PluginCommand gta = getCommand("gta");
        if (gta != null) {
            gta.setExecutor(command);
            gta.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(wand, this);
        getServer().getPluginManager().registerEvents(new fr.cleangang.gta.command.AdminMenu(this), this);
        getServer().getPluginManager().registerEvents(new fr.cleangang.gta.lobby.CityMenu(this), this);
        hub = new fr.cleangang.gta.lobby.HubListener(this);
        getServer().getPluginManager().registerEvents(hub, this);
        for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) hub.giveHubItem(p);
        wand.startPreview();
        getLogger().info("GtaMC prêt — bienvenue à Liberty City.");
    }

    @Override
    public void onDisable() {
        if (arenas != null) arenas.shutdownAll();
        if (stats != null) stats.save();
    }

    /** Applique les valeurs modifiées en mémoire (menu admin) et les écrit dans config.yml. */
    public void applyConfig() {
        saveConfig();
        settings = new Settings(getConfig());
    }

    public void reloadSettings() {
        reloadConfig();
        settings = new Settings(getConfig());
    }

    public Settings settings() { return settings; }

    public ArenaManager arenas() { return arenas; }

    public ArenaCreator creator() { return creator; }

    public CityWand wand() { return wand; }

    public StatsManager stats() { return stats; }

    public fr.cleangang.gta.lobby.HubListener hub() { return hub; }
}

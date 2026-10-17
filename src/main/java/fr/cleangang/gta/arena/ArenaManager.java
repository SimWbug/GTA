package fr.cleangang.gta.arena;

import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.game.Game;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ArenaManager {

    private final GtaPlugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<String, Game> games = new HashMap<>();
    private final Map<UUID, Game> playerGames = new HashMap<>();

    public ArenaManager(GtaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("arenas");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Location lobby = s.getLocation("lobby");
            arenas.put(key.toLowerCase(), new Arena(key, s.getString("world"), s.getInt("x"), s.getInt("y"),
                    s.getInt("z"), s.getLong("seed"), fr.cleangang.gta.city.CityTheme.parse(s.getString("theme")), lobby));
        }
        plugin.getLogger().info(arenas.size() + " arène(s) GTA chargée(s).");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            String p = "arenas." + a.name() + ".";
            yml.set(p + "world", a.worldName());
            yml.set(p + "x", a.ox());
            yml.set(p + "y", a.oy());
            yml.set(p + "z", a.oz());
            yml.set(p + "seed", a.seed());
            yml.set(p + "theme", a.theme().id);
            if (a.customLobby() != null) yml.set(p + "lobby", a.customLobby());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer arenas.yml : " + e.getMessage());
        }
    }

    public Arena get(String name) { return arenas.get(name.toLowerCase()); }
    public Collection<Arena> all() { return arenas.values(); }

    public void add(Arena arena) {
        arenas.put(arena.name().toLowerCase(), arena);
        save();
    }

    public void remove(Arena arena) {
        Game g = games.remove(arena.name().toLowerCase());
        if (g != null) g.shutdown();
        arenas.remove(arena.name().toLowerCase());
        save();
    }

    public Game game(Arena arena) {
        return games.computeIfAbsent(arena.name().toLowerCase(), k -> new Game(plugin, arena));
    }

    public Game gameOf(Player p) { return playerGames.get(p.getUniqueId()); }

    public void bind(Player p, Game g) { playerGames.put(p.getUniqueId(), g); }

    public void unbind(Player p) { playerGames.remove(p.getUniqueId()); }

    public void shutdownAll() {
        for (Game g : games.values()) g.shutdown();
        games.clear();
        playerGames.clear();
    }
}

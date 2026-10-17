package fr.cleangang.gta.stats;

import fr.cleangang.gta.GtaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Statistiques de carrière des joueurs, dans plugins/GtaMC/stats.yml. */
public final class StatsManager {

    public static final class Entry {
        public String name = "?";
        public int played, wins, policeGames, thiefGames, arrests, busted, money, bestLoot;
    }

    /** Classements disponibles pour /gta top. */
    public enum Ranking {
        WINS("victoires", "Victoires"),
        ARRESTS("arrestations", "Arrestations"),
        MONEY("butin", "Butin total"),
        BEST("record", "Record de butin"),
        PLAYED("parties", "Parties jouées");

        public final String id, label;

        Ranking(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public int value(Entry e) {
            return switch (this) {
                case WINS -> e.wins;
                case ARRESTS -> e.arrests;
                case MONEY -> e.money;
                case BEST -> e.bestLoot;
                case PLAYED -> e.played;
            };
        }

        public static Ranking parse(String s) {
            if (s == null) return WINS;
            String k = s.toLowerCase(Locale.ROOT);
            for (Ranking r : values()) if (r.id.equals(k) || r.name().equalsIgnoreCase(k)) return r;
            return null;
        }
    }

    private final GtaPlugin plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public StatsManager(GtaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public void load() {
        entries.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Entry e = new Entry();
            e.name = s.getString("name", "?");
            e.played = s.getInt("played");
            e.wins = s.getInt("wins");
            e.policeGames = s.getInt("police-games");
            e.thiefGames = s.getInt("thief-games");
            e.arrests = s.getInt("arrests");
            e.busted = s.getInt("busted");
            e.money = s.getInt("money");
            e.bestLoot = s.getInt("best-loot");
            try {
                entries.put(UUID.fromString(key), e);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            String p = "players." + me.getKey() + ".";
            Entry e = me.getValue();
            yml.set(p + "name", e.name);
            yml.set(p + "played", e.played);
            yml.set(p + "wins", e.wins);
            yml.set(p + "police-games", e.policeGames);
            yml.set(p + "thief-games", e.thiefGames);
            yml.set(p + "arrests", e.arrests);
            yml.set(p + "busted", e.busted);
            yml.set(p + "money", e.money);
            yml.set(p + "best-loot", e.bestLoot);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible d'enregistrer stats.yml : " + ex.getMessage());
        }
    }

    /** Enregistre le résultat d'un joueur à la fin d'une partie. */
    public void record(UUID id, String name, boolean police, boolean won, int arrests, int busted, int collected) {
        Entry e = entries.computeIfAbsent(id, k -> new Entry());
        e.name = name;
        e.played++;
        if (won) e.wins++;
        if (police) e.policeGames++;
        else e.thiefGames++;
        e.arrests += arrests;
        e.busted += busted;
        e.money += collected;
        e.bestLoot = Math.max(e.bestLoot, collected);
    }

    public Entry get(UUID id) { return entries.get(id); }

    public Entry byName(String name) {
        for (Entry e : entries.values()) if (e.name.equalsIgnoreCase(name)) return e;
        return null;
    }

    public List<Entry> top(Ranking r, int limit) {
        List<Entry> list = new ArrayList<>(entries.values());
        list.removeIf(e -> r.value(e) <= 0);
        list.sort(Comparator.comparingInt((Entry e) -> r.value(e)).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }
}

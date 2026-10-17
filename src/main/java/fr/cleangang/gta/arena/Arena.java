package fr.cleangang.gta.arena;

import fr.cleangang.gta.city.CityLayout;
import fr.cleangang.gta.city.CityTheme;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Une arène = une ville 128x128. On ne garde que l'origine (coin nord-ouest, au
 * niveau de la route) et la graine : le plan est recalculé à la demande.
 */
public final class Arena {

    private final String name;
    private final String worldName;
    private final int ox, oy, oz;
    private final long seed;
    private final CityTheme theme;
    private Location lobby;
    private CityLayout layout;

    public Arena(String name, String worldName, int ox, int oy, int oz, long seed, CityTheme theme, Location lobby) {
        this.theme = theme == null ? CityTheme.LIBERTY : theme;
        this.name = name;
        this.worldName = worldName;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.seed = seed;
        this.lobby = lobby;
    }

    public String name() { return name; }
    public String worldName() { return worldName; }
    public int ox() { return ox; }
    public int oy() { return oy; }
    public int oz() { return oz; }
    public long seed() { return seed; }
    public CityTheme theme() { return theme; }

    public World world() { return Bukkit.getWorld(worldName); }

    public CityLayout layout() {
        if (layout == null) layout = new CityLayout(seed);
        return layout;
    }

    /** Hauteur à laquelle roulent les voitures (juste au-dessus de la route). */
    public double driveY() { return oy + 1; }

    /** Centre de la case (x, z) du plan, au niveau des voitures. */
    public Location cell(int x, int z) {
        return new Location(world(), ox + x + 0.5, driveY(), oz + z + 0.5);
    }

    public Location center() {
        return new Location(world(), ox + CityLayout.SIZE / 2.0, driveY(), oz + CityLayout.SIZE / 2.0);
    }

    public boolean contains(double x, double z) {
        return x >= ox + 1 && z >= oz + 1 && x < ox + CityLayout.SIZE - 1 && z < oz + CityLayout.SIZE - 1;
    }

    /** Point d'attente : sur l'avenue juste devant le QG de police si rien n'est défini. */
    public Location lobby() {
        if (lobby != null && lobby.getWorld() != null) return lobby;
        Location l = cell(44, 24);
        l.setYaw(90);
        return l;
    }

    public Location customLobby() { return lobby; }

    public void setLobby(Location lobby) { this.lobby = lobby; }
}

package fr.cleangang.gta.game;

import fr.cleangang.gta.Settings;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Sons du mode. Avec le resource pack : sons gta:* (moteur, sirène, klaxon, billets, busted).
 * Sans pack (use-custom-models: false) : équivalents vanilla.
 */
final class GtaSounds {

    private GtaSounds() {}

    /** Ronronnement du moteur, joué au conducteur seulement ; la hauteur suit la vitesse (0..1). */
    static void engine(Settings s, Player driver, double speedRatio) {
        if (!s.customModels) return;
        float pitch = (float) (0.55 + 1.3 * Math.min(1.0, speedRatio));
        driver.playSound(driver.getLocation(), "gta:car.engine", 0.35f, pitch);
    }

    /** Sirène de police, audible par tous autour de la voiture. */
    static void siren(Settings s, World w, Location at, Game game) {
        if (s.customModels) {
            w.playSound(at, "gta:car.siren", 3f, 1f);
        } else {
            w.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 1.4f);
            game.later(8, () -> w.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 1.0f));
        }
    }

    static void horn(Settings s, World w, Location at) {
        if (s.customModels) w.playSound(at, "gta:car.horn", 2.5f, 1f);
        else w.playSound(at, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.5f, 1.2f);
    }

    static void cash(Settings s, Player p, boolean collector) {
        if (s.customModels) p.playSound(p.getLocation(), "gta:cash.pickup", collector ? 1f : 0.45f, collector ? 1f : 0.9f);
        else p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, collector ? 1f : 0.6f, collector ? 1.2f : 0.9f);
    }

    static void busted(Settings s, Player p) {
        if (s.customModels) p.playSound(p.getLocation(), "gta:player.busted", 1f, 1f);
        else p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
    }
}

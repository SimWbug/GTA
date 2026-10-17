package fr.cleangang.gta;

import org.bukkit.configuration.file.FileConfiguration;

/** Valeurs de config.yml, relues à chaque /gta reload. */
public final class Settings {

    public final int minPlayers, maxPlayers, lobbyCountdown, duration, policeReleaseDelay, endDelay;
    public final int playersPerPolice;
    public final boolean cinematic;
    public final int cinematicDuration;
    public final boolean voteEnabled;
    public final int voteDuration;
    public final int prisonProtection;
    public final boolean eventsEnabled;
    public final int eventIntervalMin, eventIntervalMax, eventDuration, convoyValue;
    public final double convoySpeed, alertSpeedBoost;
    public final boolean hubEnabled;
    public final java.util.List<String> hubWorlds;
    public final int hubSlot;
    public final double botSpeed;

    public final int targetBase, targetPerThief, packetMin, packetMax, initialPackets, maxPackets, spawnInterval;
    public final double pickupRadius;

    public final double carScale, policeMaxSpeed, thiefMaxSpeed, reverseMaxSpeed, acceleration, brake, friction, turnSpeed;
    public final double nitroMultiplier, killTolerance;
    public final int nitroDuration, nitroCooldown;

    public final double cameraHeight, speedZoom, overviewHeight;

    public final boolean customModels;
    public final boolean sendPack;
    public final String packUrl, packSha1, packPrompt;

    public final int columnsPerTick;

    /** Nombre de policiers pour n joueurs : 1 pour chaque tranche complète, au moins 1, au moins 1 voleur. */
    public int policeFor(int players) {
        return Math.max(1, Math.min(players - 1, players / playersPerPolice));
    }

    public Settings(FileConfiguration c) {
        minPlayers = Math.max(2, c.getInt("game.min-players", 2));
        maxPlayers = Math.max(minPlayers, c.getInt("game.max-players", 12));
        lobbyCountdown = c.getInt("game.lobby-countdown", 20);
        duration = c.getInt("game.duration", 300);
        playersPerPolice = Math.max(2, c.getInt("game.players-per-police", 3));
        policeReleaseDelay = c.getInt("game.police-release-delay", 5);
        endDelay = c.getInt("game.end-delay", 6);
        cinematic = c.getBoolean("game.end-cinematic", true);
        cinematicDuration = Math.max(3, c.getInt("game.cinematic-duration", 9));
        voteEnabled = c.getBoolean("vote.enabled", true);
        voteDuration = Math.max(5, c.getInt("vote.duration", 15));
        prisonProtection = Math.max(0, c.getInt("prison.protection", 3));
        eventsEnabled = c.getBoolean("events.enabled", true);
        eventIntervalMin = c.getInt("events.interval-min", 60);
        eventIntervalMax = c.getInt("events.interval-max", 100);
        eventDuration = Math.max(5, c.getInt("events.duration", 20));
        convoyValue = c.getInt("events.convoy-value", 1000);
        convoySpeed = c.getDouble("events.convoy-speed", 0.28);
        alertSpeedBoost = c.getDouble("events.alert-speed-boost", 1.15);
        hubEnabled = c.getBoolean("hub.enabled", true);
        hubWorlds = c.getStringList("hub.worlds");
        hubSlot = Math.max(0, Math.min(8, c.getInt("hub.slot", 5)));
        botSpeed = Math.max(0.3, Math.min(1.5, c.getDouble("bots.speed", 0.9)));

        targetBase = c.getInt("money.target-base", 2000);
        targetPerThief = c.getInt("money.target-per-thief", 1000);
        packetMin = c.getInt("money.packet-min", 100);
        packetMax = Math.max(packetMin, c.getInt("money.packet-max", 500));
        initialPackets = c.getInt("money.initial-packets", 5);
        maxPackets = c.getInt("money.max-packets", 8);
        spawnInterval = Math.max(1, c.getInt("money.spawn-interval", 5));
        pickupRadius = c.getDouble("money.pickup-radius", 1.6);

        carScale = c.getDouble("car.scale", 1.4);
        policeMaxSpeed = c.getDouble("car.police-max-speed", 0.60);
        thiefMaxSpeed = c.getDouble("car.thief-max-speed", 0.56);
        reverseMaxSpeed = c.getDouble("car.reverse-max-speed", 0.22);
        acceleration = c.getDouble("car.acceleration", 0.022);
        brake = c.getDouble("car.brake", 0.05);
        friction = c.getDouble("car.friction", 0.965);
        turnSpeed = c.getDouble("car.turn-speed", 6.5);
        nitroMultiplier = c.getDouble("car.nitro-multiplier", 1.45);
        nitroDuration = c.getInt("car.nitro-duration", 40);
        nitroCooldown = c.getInt("car.nitro-cooldown", 200);
        killTolerance = c.getDouble("car.kill-tolerance", 0.05);

        cameraHeight = c.getDouble("camera.height", 20);
        speedZoom = c.getDouble("camera.speed-zoom", 18);
        overviewHeight = c.getDouble("camera.overview-height", 92);

        customModels = c.getBoolean("use-custom-models", true);
        sendPack = c.getBoolean("resource-pack.send-on-join", false);
        packUrl = c.getString("resource-pack.url", "");
        packSha1 = c.getString("resource-pack.sha1", "");
        packPrompt = c.getString("resource-pack.prompt", "Pack de textures GTA");

        columnsPerTick = Math.max(16, c.getInt("generation.columns-per-tick", 160));
    }
}

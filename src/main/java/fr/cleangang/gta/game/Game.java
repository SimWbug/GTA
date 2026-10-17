package fr.cleangang.gta.game;

import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.Settings;
import fr.cleangang.gta.arena.Arena;
import fr.cleangang.gta.city.CityLayout;
import fr.cleangang.gta.lobby.CityMenu;
import fr.cleangang.gta.lobby.LobbyItems;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Une partie sur une arène. Cycle : WAITING → COUNTDOWN → RUNNING → ENDING → WAITING.
 *
 * Voleurs : ramasser des paquets de billets jusqu'au montant cible (butin d'équipe).
 * Police  : rentrer dans les voleurs (un seul contact suffit) ; gagne si le temps s'écoule
 *           ou si tous les voleurs sont arrêtés.
 */
public final class Game {

    public enum State { WAITING, COUNTDOWN, RUNNING, ENDING, VOTING }

    public static final Component PREFIX = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("GTA", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY));

    private final GtaPlugin plugin;
    private final Arena arena;
    private final Random random = new Random();

    private State state = State.WAITING;
    private final Map<UUID, PlayerSnapshot> snapshots = new LinkedHashMap<>();
    private final Map<UUID, Car> cars = new LinkedHashMap<>();
    private final List<CashPacket> packets = new ArrayList<>();

    private BukkitTask task;
    private int tick;
    private int countdown;
    private int timeLeft;
    private int money;
    private int target;
    private int freezeUntil;
    private int policeReleaseAt;
    private int nextCashAt;
    private int endTicks;
    private int endElapsed;
    private GtaTeam winner;
    private EndCinematic cinematic;
    /** Durée du plan « VICTOIRE » en jeu avant la cinématique. */
    private static final int CINEMATIC_DELAY = 50;

    private BossBar bar;
    private Scoreboard board;
    private Objective objective;
    private int shownLines;
    private ItemDisplay overview;
    private Prison prison;
    private GameEvents events;
    /** Vote de la prochaine ville : joueur → arène. */
    private final Map<UUID, Arena> votes = new HashMap<>();
    /** Bots de test ajoutés par un admin (nom + équipe imposée ou null). */
    private final List<BotEntry> bots = new ArrayList<>();
    /** Équipe imposée à un joueur pour le test (/gta test … police|voleur). */
    private final Map<UUID, GtaTeam> forcedTeam = new HashMap<>();

    private record BotEntry(String name, GtaTeam team) {}

    private static final String[] BOT_NAMES = {
            "Tony", "Marco", "Lola", "Rico", "Vinnie", "Nina", "Bruno", "Dina",
            "Sal", "Joey", "Mia", "Leo", "Paco", "Gina", "Frankie", "Lucky"};
    private int voteEndsAt;

    public Game(GtaPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
    }

    Settings settings() { return plugin.settings(); }
    GtaPlugin plugin() { return plugin; }
    public Arena arena() { return arena; }
    public State state() { return state; }
    /** Participants : joueurs + bots. */
    public int size() { return snapshots.size() + bots.size(); }

    public int botCount() { return bots.size(); }

    java.util.Collection<Car> carsView() { return cars.values(); }

    List<CashPacket> packets() { return packets; }

    Prison prison() { return prison; }

    double[] convoy() { return events == null ? null : events.convoy(); }

    void later(int ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }

    private List<Player> players() {
        List<Player> out = new ArrayList<>();
        for (UUID id : snapshots.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private void broadcast(Component msg) {
        for (Player p : players()) p.sendMessage(PREFIX.append(msg));
    }

    private void sound(Sound sound, float pitch) {
        for (Player p : players()) p.playSound(p.getLocation(), sound, 1f, pitch);
    }

    private static void title(Player p, Component main, Component sub, int stayMs) {
        if (p == null) return; // bot
        p.showTitle(Title.title(main, sub, Title.Times.times(Duration.ofMillis(150),
                Duration.ofMillis(stayMs), Duration.ofMillis(300))));
    }

    // ================================================================== joueurs

    public boolean join(Player p) {
        Settings s = settings();
        if (state == State.RUNNING || state == State.ENDING) {
            p.sendMessage(PREFIX.append(Component.text("Une partie est en cours, réessaie dans un instant.", NamedTextColor.RED)));
            return false;
        }
        if (size() >= s.maxPlayers) {
            p.sendMessage(PREFIX.append(Component.text("La partie est pleine.", NamedTextColor.RED)));
            return false;
        }
        if (arena.world() == null) {
            p.sendMessage(PREFIX.append(Component.text("Le monde de cette arène n'est pas chargé.", NamedTextColor.RED)));
            return false;
        }

        snapshots.put(p.getUniqueId(), PlayerSnapshot.capture(p));
        plugin.arenas().bind(p, this);
        enterLobby(p);
        sendPack(p);

        broadcast(Component.text(p.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" a rejoint la ville (" + size() + "/" + s.maxPlayers + ")", NamedTextColor.GRAY)));
        p.sendMessage(PREFIX.append(Component.text("Bienvenue à " + arena.theme().label + ". /gta leave pour quitter.", NamedTextColor.GRAY)));

        ensureTask();
        if (state == State.WAITING && size() >= s.minPlayers) startCountdown(s.lobbyCountdown);
        else if (state == State.COUNTDOWN && size() >= s.maxPlayers && countdown > 5) countdown = 5;
        updateBoard();
        return true;
    }

    /** Accueil au point d'attente : inventaire vidé, mode aventure, objets du lobby. */
    private void enterLobby(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setGameMode(GameMode.ADVENTURE);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.resetPlayerTime();
        p.teleport(arena.lobby());
        giveLobbyItems(p);
        ensureBoard();
        p.setScoreboard(board);
    }

    private void giveLobbyItems(Player p) {
        p.getInventory().clear();
        if (state == State.VOTING) p.getInventory().setItem(0, LobbyItems.create(plugin, LobbyItems.Type.VOTE));
        p.getInventory().setItem(8, LobbyItems.create(plugin, LobbyItems.Type.LEAVE));
    }

    /** Reprend un joueur venu d'une autre ville après un vote (garde son état d'origine). */
    void adopt(Player p, PlayerSnapshot snap) {
        snapshots.put(p.getUniqueId(), snap);
        plugin.arenas().bind(p, this);
        enterLobby(p);
        ensureTask();
    }

    public void leave(Player p, boolean quitting) {
        PlayerSnapshot snap = snapshots.remove(p.getUniqueId());
        if (snap == null) return;
        plugin.arenas().unbind(p);

        Car car = cars.remove(p.getUniqueId());
        if (car != null) {
            if (prison != null) prison.forget(car);
            car.remove();
        }
        votes.remove(p.getUniqueId());
        forcedTeam.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
        snap.restore(p);

        if (!quitting) p.sendMessage(PREFIX.append(Component.text("Tu as quitté la partie.", NamedTextColor.GRAY)));
        broadcast(Component.text(p.getName(), NamedTextColor.YELLOW).append(Component.text(" a quitté la ville.", NamedTextColor.GRAY)));

        if (snapshots.isEmpty()) {
            reset();
            return;
        }
        if (state == State.VOTING && votes.size() >= snapshots.size()) voteEndsAt = tick;
        if (state == State.COUNTDOWN && size() < settings().minPlayers) {
            state = State.WAITING;
            broadcast(Component.text("Pas assez de joueurs, départ annulé.", NamedTextColor.RED));
        }
        if (state == State.RUNNING) checkWin();
        updateBoard();
    }

    public boolean has(Player p) { return snapshots.containsKey(p.getUniqueId()); }

    private void sendPack(Player p) {
        Settings s = settings();
        if (!s.sendPack || s.packUrl == null || s.packUrl.isBlank()) return;
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(
                    UUID.nameUUIDFromBytes(s.packUrl.getBytes()), URI.create(s.packUrl), s.packSha1);
            p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .prompt(Component.text(s.packPrompt))
                    .required(false)
                    .replace(false)
                    .build());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("URL de resource pack invalide : " + s.packUrl);
        }
    }

    // ================================================================== boucle

    private void ensureTask() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::loop, 1L, 1L);
    }

    private void loop() {
        tick++;
        switch (state) {
            case WAITING -> {
                if (tick % 20 == 0) {
                    int missing = settings().minPlayers - size();
                    for (Player p : players()) {
                        p.sendActionBar(Component.text("En attente de " + missing + " joueur(s)…", NamedTextColor.GRAY));
                    }
                }
            }
            case COUNTDOWN -> {
                if (tick % 20 != 0) return;
                countdown--;
                if (countdown <= 0) {
                    start();
                    return;
                }
                if (countdown <= 5 || countdown % 10 == 0) {
                    for (Player p : players()) {
                        p.sendActionBar(Component.text("Départ dans " + countdown + "s", NamedTextColor.GOLD));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
                    }
                }
                updateBoard();
            }
            case RUNNING -> running();
            case ENDING -> {
                endElapsed++;
                for (Car c : cars.values()) {
                    c.tick(tick, true);
                    c.aim(null, tick);
                }
                if (endElapsed == CINEMATIC_DELAY && settings().cinematic) startCinematic();
                if (cinematic != null) cinematic.tick(endElapsed - CINEMATIC_DELAY);
                if (--endTicks <= 0) endRound();
            }
            case VOTING -> {
                int left = Math.max(0, (voteEndsAt - tick + 19) / 20);
                if (tick % 20 == 0) {
                    for (Player p : players()) {
                        p.sendActionBar(Component.text("Vote de la prochaine ville : " + left + "s", NamedTextColor.GREEN));
                    }
                    updateBoard();
                }
                if (tick >= voteEndsAt) closeVote();
            }
        }
    }

    private void startCountdown(int seconds) {
        state = State.COUNTDOWN;
        countdown = Math.max(3, seconds);
        broadcast(Component.text("La partie commence dans " + countdown + " secondes !", NamedTextColor.GOLD));
    }

    public void forceStart() {
        if (state == State.WAITING || state == State.COUNTDOWN) {
            if (size() < 2) {
                broadcast(Component.text("Il faut au moins 2 joueurs (1 policier, 1 voleur).", NamedTextColor.RED));
                return;
            }
            state = State.COUNTDOWN;
            countdown = 3;
        }
    }

    // ================================================================== démarrage

    private void start() {
        Settings s = settings();
        List<Player> ps = players();
        int n = ps.size() + bots.size();
        if (n < 2 || ps.isEmpty()) {
            state = State.WAITING;
            return;
        }
        int nPolice = s.policeFor(n);
        int nThieves = n - nPolice;

        // Répartition : d'abord les équipes imposées (tests admin), puis le reste au hasard
        List<Object> police = new ArrayList<>(), thieves = new ArrayList<>(), free = new ArrayList<>();
        for (Player p : ps) sort(p, forcedTeam.get(p.getUniqueId()), police, thieves, free, nPolice, nThieves);
        for (BotEntry b : bots) sort(b, b.team(), police, thieves, free, nPolice, nThieves);
        Collections.shuffle(free, random);
        for (Object o : free) (police.size() < nPolice ? police : thieves).add(o);

        CityLayout layout = arena.layout();
        World world = arena.world();
        money = 0;
        target = s.targetBase + s.targetPerThief * nThieves;
        freezeUntil = tick + 60;
        policeReleaseAt = freezeUntil + s.policeReleaseDelay * 20;
        nextCashAt = freezeUntil + s.spawnInterval * 20;
        timeLeft = s.duration * 20;

        prison = new Prison(s, arena);
        prison.spawn();
        events = new GameEvents(this, s, arena);
        events.schedule(freezeUntil);

        int pi = 0, ti = 0;
        List<Object> order = new ArrayList<>(police);
        order.addAll(thieves);
        for (int i = 0; i < order.size(); i++) {
            Object who = order.get(i);
            GtaTeam team = i < police.size() ? GtaTeam.POLICE : GtaTeam.THIEVES;
            List<int[]> spawns = team == GtaTeam.POLICE ? layout.policeSpawns() : layout.thiefSpawns();
            int[] cell = spawns.get((team == GtaTeam.POLICE ? pi++ : ti++) % spawns.size());
            Location loc = arena.cell(cell[0], cell[1]);
            loc.setYaw(CityLayout.yawTowardCenter(cell[0], cell[1]));
            Car car;
            if (who instanceof Player p) {
                p.getInventory().clear();
                car = new Car(this, p, team, loc);
            } else {
                car = new Car(this, ((BotEntry) who).name(), team, loc);
            }
            cars.put(car.id, car);
            car.spawn(tick);
        }
        nPolice = police.size();
        nThieves = thieves.size();

        bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        for (Player p : ps) p.showBossBar(bar);
        updateBar();

        for (int i = 0; i < s.initialPackets; i++) spawnCash();
        state = State.RUNNING;

        for (Car c : cars.values()) {
            if (c.team == GtaTeam.POLICE) {
                title(c.player, Component.text("POLICIER", NamedTextColor.BLUE, TextDecoration.BOLD),
                        Component.text("Fonce dans les voleurs pour les arrêter !", NamedTextColor.WHITE), 2500);
            } else {
                title(c.player, Component.text("VOLEUR", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text("Ramassez $" + target + " de billets !", NamedTextColor.GREEN), 2500);
            }
        }
        broadcast(Component.text(nPolice + " policier(s) contre " + nThieves + " voleur(s). Butin à atteindre : $" + target, NamedTextColor.GOLD));
        broadcast(Component.text("Voleurs arrêtés = en prison devant le QG. Roulez dans la cellule pour libérer vos complices !", NamedTextColor.RED));
        broadcast(Component.text("Z/W accélérer · S freiner/reculer · Q/D tourner · Espace nitro · Sneak klaxon (voleurs) / sirène (police)", NamedTextColor.GRAY));
        if (s.policeReleaseDelay > 0) {
            broadcast(Component.text("Les voleurs ont " + s.policeReleaseDelay + "s d'avance !", NamedTextColor.RED));
        }
        updateBoard();
    }

    private static void sort(Object who, GtaTeam wanted, List<Object> police, List<Object> thieves,
                             List<Object> free, int nPolice, int nThieves) {
        if (wanted == GtaTeam.POLICE && police.size() < nPolice) police.add(who);
        else if (wanted == GtaTeam.THIEVES && thieves.size() < nThieves) thieves.add(who);
        else free.add(who);
    }

    // ================================================================== bots de test (admin)

    /** Ajoute des bots au lobby. team = null pour laisser la répartition automatique. */
    public int addBots(int count, GtaTeam team) {
        if (state != State.WAITING && state != State.COUNTDOWN) return 0;
        int added = 0;
        while (added < count && size() < settings().maxPlayers) {
            String name = "Bot " + BOT_NAMES[(bots.size() + added) % BOT_NAMES.length];
            bots.add(new BotEntry(name, team));
            added++;
        }
        if (added > 0) {
            broadcast(Component.text("+" + added + " bot(s)" + (team == null ? "" : " (" + team.label + ")")
                    + " — " + size() + "/" + settings().maxPlayers, NamedTextColor.GRAY));
            if (state == State.WAITING && size() >= settings().minPlayers && !snapshots.isEmpty()) {
                startCountdown(settings().lobbyCountdown);
            }
            updateBoard();
        }
        return added;
    }

    public int removeBots(int count) {
        if (state != State.WAITING && state != State.COUNTDOWN) return 0;
        int removed = 0;
        while (removed < count && !bots.isEmpty()) {
            bots.remove(bots.size() - 1);
            removed++;
        }
        if (state == State.COUNTDOWN && size() < settings().minPlayers) state = State.WAITING;
        updateBoard();
        return removed;
    }

    /** Équipe imposée à un joueur pour la prochaine manche (null = automatique). */
    public void forceTeam(Player p, GtaTeam team) {
        if (team == null) forcedTeam.remove(p.getUniqueId());
        else forcedTeam.put(p.getUniqueId(), team);
    }

    /** Raccourci de test : départ quasi immédiat. */
    public void quickStart() {
        if ((state == State.WAITING || state == State.COUNTDOWN) && size() >= 2 && !snapshots.isEmpty()) {
            state = State.COUNTDOWN;
            countdown = 3;
        }
    }

    // ================================================================== partie en cours

    private void running() {
        Settings s = settings();

        int left = freezeUntil - tick;
        if (left > 0 && left % 20 == 0) {
            for (Player p : players()) {
                title(p, Component.text(String.valueOf(left / 20), NamedTextColor.YELLOW, TextDecoration.BOLD), Component.empty(), 700);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            }
        } else if (left == 0) {
            for (Player p : players()) {
                title(p, Component.text("GO !", NamedTextColor.GREEN, TextDecoration.BOLD), Component.empty(), 700);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            }
        }
        if (tick == policeReleaseAt && s.policeReleaseDelay > 0) {
            broadcast(Component.text("🚨 La police est lâchée !", NamedTextColor.BLUE, TextDecoration.BOLD));
            sound(Sound.BLOCK_BELL_USE, 1.2f);
        }

        boolean frozen = tick < freezeUntil;
        boolean policeHeld = tick < policeReleaseAt;
        for (Car c : cars.values()) c.tick(tick, frozen || (c.team == GtaTeam.POLICE && policeHeld));
        for (Car c : cars.values()) c.aim(c.alive ? targetFor(c) : null, tick);

        resolveContacts(policeHeld);
        if (state != State.RUNNING) return;

        for (CashPacket packet : packets) packet.tick();
        pickups();
        if (state != State.RUNNING) return;

        prison.tick(tick);
        jailbreak();
        if (!frozen) events.tick(tick, cars.values(), players());

        if (tick >= nextCashAt) {
            nextCashAt = tick + (int) Math.max(20, s.spawnInterval * 20 * events.spawnRateMultiplier());
            if (packets.size() < s.maxPackets) spawnCash();
        }

        if (tick % 6 == 0) {
            boolean red = (tick / 6) % 2 == 0;
            for (Car c : cars.values()) if (c.team == GtaTeam.POLICE) c.setSiren(red);
        }

        if (!frozen) {
            timeLeft--;
            int sec = timeLeft / 20;
            if (timeLeft % 20 == 0 && (sec == 60 || sec == 30 || (sec <= 10 && sec > 0))) {
                broadcast(Component.text("⏱ " + sec + " secondes restantes !", NamedTextColor.YELLOW));
                sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f);
            }
            if (timeLeft <= 0) {
                end(GtaTeam.POLICE, "Le temps est écoulé, le butin est incomplet !");
                return;
            }
        }

        if (tick % 4 == 0) {
            for (Car c : cars.values()) {
                if (c.player == null || !c.player.isOnline()) continue;
                c.player.sendActionBar(c.alive ? c.hud(pointerFor(c))
                        : c.jailed ? Component.text("En prison : un complice doit rouler dans la cellule pour te libérer !", NamedTextColor.RED)
                        : Component.text("BUSTED !", NamedTextColor.RED));
            }
        }
        if (tick % 10 == 0) {
            updateBar();
            updateBoard();
        }
    }

    /** Voleurs : paquet de billets le plus proche. Police : voleur en fuite le plus proche. {x, z} ou null. */
    private double[] targetFor(Car c) {
        double best = Double.MAX_VALUE;
        double[] t = null;
        if (c.team == GtaTeam.THIEVES) {
            double[] van = events == null ? null : events.convoy();
            if (van != null) return van; // le convoi passe avant tout
            for (CashPacket p : packets) {
                double d = c.distanceTo(p.x, p.z);
                if (d < best) { best = d; t = new double[]{p.x, p.z}; }
            }
        } else {
            for (Car o : cars.values()) {
                if (o.team != GtaTeam.THIEVES || !o.alive) continue;
                double d = c.distanceTo(o.x, o.z);
                if (d < best) { best = d; t = new double[]{o.x, o.z}; }
            }
        }
        return t;
    }

    private Component pointerFor(Car c) {
        double[] t = targetFor(c);
        if (t == null) return null;
        if (c.team == GtaTeam.THIEVES && events != null && events.convoy() != null) {
            return c.pointer(t[0], t[1], "Convoi", NamedTextColor.GOLD);
        }
        return c.team == GtaTeam.THIEVES
                ? c.pointer(t[0], t[1], "Billets", NamedTextColor.GREEN)
                : c.pointer(t[0], t[1], "Voleur", NamedTextColor.RED);
    }

    private void resolveContacts(boolean policeHeld) {
        double tol = settings().killTolerance;
        List<Car> list = new ArrayList<>();
        for (Car c : cars.values()) if (c.alive) list.add(c);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Car a = list.get(i), b = list.get(j);
                if (!a.alive || !b.alive) continue;
                if (a.team != b.team && !policeHeld) {
                    Car police = a.team == GtaTeam.POLICE ? a : b;
                    Car thief = police == a ? b : a;
                    if (thief.isProtected(tick)) {
                        if (a.touches(b, 0)) a.pushApart(b);
                    } else if (a.touches(b, tol)) {
                        arrest(thief, police);
                        if (state != State.RUNNING) return;
                    }
                } else if (a.touches(b, 0)) {
                    a.pushApart(b);
                }
            }
        }
    }

    private void arrest(Car thief, Car police) {
        thief.wreck();
        police.arrests++;
        broadcast(Component.text("🚓 ", NamedTextColor.BLUE)
                .append(Component.text(police.name, NamedTextColor.BLUE))
                .append(Component.text(" a arrêté ", NamedTextColor.GRAY))
                .append(Component.text(thief.name, NamedTextColor.RED))
                .append(Component.text(" !", NamedTextColor.GRAY)));
        title(thief.player, Component.text("BUSTED !", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("par " + police.name, NamedTextColor.GRAY), 2000);
        if (police.player != null) police.player.playSound(police.player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        if (thief.player != null) GtaSounds.busted(settings(), thief.player);

        // Après un instant sur l'épave, direction la cellule devant le QG
        later(35, () -> {
            if (state != State.RUNNING || prison == null || cars.get(thief.id) != thief || thief.alive) return;
            thief.jailed = true;
            prison.add(thief);
            if (thief.player == null) return;
            thief.player.setSpectatorTarget(null);
            thief.player.teleport(prison.cameraLocation());
            thief.lookThrough(prison.camera(), tick);
            thief.player.sendMessage(PREFIX.append(Component.text(
                    "Tu es en prison devant le QG. Un complice peut te libérer en roulant dans la cellule.", NamedTextColor.RED)));
        });
        checkWin();
    }

    private void pickups() {
        double r = settings().pickupRadius;
        for (Car c : cars.values()) {
            if (!c.alive || c.team != GtaTeam.THIEVES) continue;
            for (int i = packets.size() - 1; i >= 0; i--) {
                CashPacket packet = packets.get(i);
                if (c.distanceTo(packet.x, packet.z) > r) continue;
                packets.remove(i);
                packet.remove();
                money += packet.value;
                c.collected += packet.value;
                if (c.player != null) {
                    GtaSounds.cash(settings(), c.player, true);
                    c.player.sendMessage(PREFIX.append(Component.text("+$" + packet.value, NamedTextColor.GREEN, TextDecoration.BOLD)));
                }
                for (Car other : cars.values()) {
                    if (other.team == GtaTeam.THIEVES && other != c && other.player != null) {
                        GtaSounds.cash(settings(), other.player, false);
                    }
                }
                updateBar();
                if (money >= target) {
                    end(GtaTeam.THIEVES, "Le butin de $" + target + " est réuni !");
                    return;
                }
            }
        }
    }

    private void spawnCash() {
        Settings s = settings();
        List<int[]> cells = arena.layout().cashCells();
        World world = arena.world();
        int y = (int) Math.floor(arena.driveY());
        for (int attempt = 0; attempt < 40; attempt++) {
            int[] c = cells.get(random.nextInt(cells.size()));
            Location loc = arena.cell(c[0], c[1]);
            Block at = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            if (at.getType().isSolid() || at.isLiquid()) continue;
            if (world.getBlockAt(loc.getBlockX(), y - 1, loc.getBlockZ()).isLiquid()) continue;
            boolean free = true;
            for (Car car : cars.values()) {
                if (car.alive && car.distanceTo(loc.getX(), loc.getZ()) < 8) { free = false; break; }
            }
            for (CashPacket p : packets) {
                double dx = p.x - loc.getX(), dz = p.z - loc.getZ();
                if (dx * dx + dz * dz < 64) { free = false; break; }
            }
            if (!free) continue;
            int steps = (s.packetMax - s.packetMin) / 50;
            int value = s.packetMin + 50 * (steps > 0 ? random.nextInt(steps + 1) : 0);
            if (events != null) value = (int) Math.round(value * events.cashMultiplier());
            packets.add(new CashPacket(s, loc, value));
            return;
        }
    }

    // ------------------------------------------------------------------ prison

    /** Un voleur libre qui roule dans la cellule libère tous ses complices. */
    private void jailbreak() {
        if (prison == null || prison.size() == 0) return;
        Car hero = null;
        for (Car c : cars.values()) {
            if (c.alive && c.team == GtaTeam.THIEVES && prison.inside(c)) { hero = c; break; }
        }
        if (hero == null) return;
        List<Car> freed = prison.releaseAll();
        List<int[]> spawns = arena.layout().thiefSpawns();
        int i = random.nextInt(spawns.size());
        for (Car c : freed) {
            if (cars.get(c.id) != c) continue;
            int[] cell = spawns.get(i++ % spawns.size());
            Location at = arena.cell(cell[0], cell[1]);
            at.setYaw(CityLayout.yawTowardCenter(cell[0], cell[1]));
            c.respawn(at, tick, settings().prisonProtection * 20);
            title(c.player, Component.text("LIBÉRÉ !", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("merci " + hero.name, NamedTextColor.GRAY), 1500);
        }
        hero.rescues += freed.size();
        broadcast(Component.text("🔓 " + hero.name + " a libéré " + freed.size() + " complice(s) !", NamedTextColor.GREEN));
        sound(Sound.BLOCK_IRON_DOOR_OPEN, 0.8f);
        Location at = new Location(arena.world(), prison.x, arena.driveY() + 1, prison.z);
        arena.world().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, at, 30, 1.5, 0.8, 1.5, 0);
    }

    // ------------------------------------------------------------------ appelés par les événements

    int currentTick() { return tick; }

    double speedFactor(GtaTeam team) {
        return events == null || state != State.RUNNING ? 1.0 : events.speedFactor(team);
    }

    void announce(Component msg) { broadcast(msg); }

    void spawnCashNow() { spawnCash(); }

    void dropCash(Location at, int value) {
        packets.add(new CashPacket(settings(), at, value));
    }

    private void checkWin() {
        if (state != State.RUNNING) return;
        int thieves = 0, police = 0;
        for (Car c : cars.values()) {
            if (c.team == GtaTeam.THIEVES && c.alive) thieves++;
            if (c.team == GtaTeam.POLICE) police++;
        }
        if (thieves == 0) end(GtaTeam.POLICE, "Tous les voleurs sont sous les verrous !");
        else if (police == 0) end(GtaTeam.THIEVES, "Il n'y a plus aucun policier en ville !");
    }

    // ================================================================== fin

    private void end(GtaTeam winner, String reason) {
        state = State.ENDING;
        this.winner = winner;
        endElapsed = 0;
        boolean hasWinners = cars.values().stream().anyMatch(c -> c.team == winner);
        endTicks = settings().cinematic && hasWinners
                ? CINEMATIC_DELAY + settings().cinematicDuration * 20
                : settings().endDelay * 20;
        for (CashPacket p : packets) p.remove();
        packets.clear();
        if (events != null) events.cleanup(players());
        if (prison != null) {
            prison.remove();
            prison = null;
        }

        Component reasonC = Component.text(reason, NamedTextColor.GRAY);
        for (Car c : cars.values()) {
            boolean won = c.team == winner;
            title(c.player,
                    won ? Component.text("VICTOIRE", NamedTextColor.GOLD, TextDecoration.BOLD)
                        : Component.text("DÉFAITE", NamedTextColor.RED, TextDecoration.BOLD),
                    reasonC, 4000);
            if (c.player != null) c.player.playSound(c.player.getLocation(),
                    won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        for (Car c : cars.values()) {
            if (c.isBot()) continue; // les bots ne comptent pas dans les statistiques
            plugin.stats().record(c.id, c.name, c.team == GtaTeam.POLICE,
                    c.team == winner, c.arrests, c.timesBusted, c.collected);
        }
        plugin.stats().save();

        Car bestCop = null, bestThief = null;
        for (Car c : cars.values()) {
            if (c.team == GtaTeam.POLICE && (bestCop == null || c.arrests > bestCop.arrests)) bestCop = c;
            if (c.team == GtaTeam.THIEVES && (bestThief == null || c.collected > bestThief.collected)) bestThief = c;
        }
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        broadcast(Component.text("Victoire : ", NamedTextColor.GRAY)
                .append(Component.text(winner.label, winner.color, TextDecoration.BOLD)));
        broadcast(Component.text("Butin : $" + money + " / $" + target, NamedTextColor.GREEN));
        if (bestCop != null && bestCop.arrests > 0) {
            broadcast(Component.text("Meilleur flic : " + bestCop.name + " (" + bestCop.arrests + " arrestation(s))", NamedTextColor.BLUE));
        }
        if (bestThief != null && bestThief.collected > 0) {
            broadcast(Component.text("Meilleur voleur : " + bestThief.name + " ($" + bestThief.collected + ")", NamedTextColor.RED));
        }
        broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    /** Plan final : l'équipe gagnante devant ses voitures, vue par tous les joueurs. */
    private void startCinematic() {
        List<Car> winners = new ArrayList<>();
        for (Car c : cars.values()) if (c.team == winner) winners.add(c);
        if (winners.isEmpty()) return;

        for (Car c : cars.values()) c.park();
        if (bar != null) for (Player p : players()) p.hideBossBar(bar);

        cinematic = new EndCinematic(settings(), arena, winner, settings().cinematicDuration * 20);
        cinematic.start(winners);
        Location start = cinematic.cameraStart();
        Car mvp = null;
        for (Car c : winners) {
            if (mvp == null || (winner == GtaTeam.POLICE ? c.arrests > mvp.arrests : c.collected > mvp.collected)) mvp = c;
        }
        Component sub = mvp == null ? Component.empty()
                : Component.text("MVP : " + mvp.name + (winner == GtaTeam.POLICE
                ? " (" + mvp.arrests + " arrestation(s))" : " ($" + mvp.collected + ")"), NamedTextColor.GOLD);
        for (Car c : cars.values()) {
            if (c.player == null) continue;
            c.player.setSpectatorTarget(null);
            c.player.teleport(start);
            c.lookThrough(cinematic.camera(), tick);
            title(c.player, cinematic.title(), sub, 3500);
        }
    }

    // ================================================================== retour au lobby + vote

    /** Fin de manche : on nettoie la ville et tout le monde revient au point d'attente. */
    private void endRound() {
        clearRound();
        List<Player> ps = players();
        if (ps.isEmpty()) {
            reset();
            return;
        }
        boolean vote = settings().voteEnabled && plugin.arenas().all().size() > 1;
        state = vote ? State.VOTING : State.WAITING;
        votes.clear();
        for (Player p : ps) enterLobby(p);
        if (vote) {
            voteEndsAt = tick + settings().voteDuration * 20;
            broadcast(Component.text("Votez pour la prochaine ville ! (objet carte ou /gta vote)", NamedTextColor.GREEN));
            for (Player p : ps) CityMenu.open(plugin, p, CityMenu.Mode.VOTE);
        } else if (size() >= settings().minPlayers) {
            startCountdown(settings().lobbyCountdown);
        }
        updateBoard();
    }

    public int votesFor(Arena a) {
        int n = 0;
        for (Arena v : votes.values()) if (v.equals(a)) n++;
        return n;
    }

    public Arena voteOf(Player p) { return votes.get(p.getUniqueId()); }

    public boolean vote(Player p, Arena a) {
        if (state != State.VOTING || !snapshots.containsKey(p.getUniqueId())) return false;
        votes.put(p.getUniqueId(), a);
        broadcast(Component.text(p.getName() + " vote pour ", NamedTextColor.GRAY)
                .append(Component.text(a.name(), NamedTextColor.GOLD)));
        if (votes.size() >= snapshots.size()) voteEndsAt = Math.min(voteEndsAt, tick + 40);
        return true;
    }

    private void closeVote() {
        // Ville la plus votée ; égalité ou aucun vote → tirage au sort parmi les meilleures
        Map<Arena, Integer> count = new HashMap<>();
        for (Arena a : votes.values()) count.merge(a, 1, Integer::sum);
        List<Arena> best = new ArrayList<>();
        int top = 0;
        for (Map.Entry<Arena, Integer> e : count.entrySet()) {
            if (e.getValue() > top) { top = e.getValue(); best.clear(); }
            if (e.getValue() == top) best.add(e.getKey());
        }
        Arena chosen = best.isEmpty() ? arena : best.get(random.nextInt(best.size()));
        Game next = plugin.arenas().game(chosen);
        boolean nextFree = next == this || next.state() == State.WAITING || next.state() == State.COUNTDOWN;
        if (!nextFree || next.size() + size() > settings().maxPlayers) chosen = arena;

        broadcast(Component.text("Prochaine ville : ", NamedTextColor.GRAY)
                .append(Component.text(chosen.name() + " (" + chosen.theme().label + ")", NamedTextColor.GOLD, TextDecoration.BOLD)));
        votes.clear();
        for (Player p : players()) p.closeInventory();

        if (chosen == arena) {
            state = State.WAITING;
            for (Player p : players()) giveLobbyItems(p);
            if (size() >= settings().minPlayers) startCountdown(settings().lobbyCountdown);
            updateBoard();
            return;
        }
        // Déménagement de tout le groupe vers l'autre ville
        Game target = plugin.arenas().game(chosen);
        for (UUID id : new ArrayList<>(snapshots.keySet())) {
            PlayerSnapshot snap = snapshots.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.arenas().unbind(p);
            target.adopt(p, snap);
        }
        target.adoptBots(bots);
        bots.clear();
        target.afterTransfer();
        reset();
    }

    private void adoptBots(List<BotEntry> others) {
        for (BotEntry b : others) if (size() < settings().maxPlayers) bots.add(b);
    }

    /** Appelé quand un groupe arrive d'une autre ville. */
    void afterTransfer() {
        broadcast(Component.text("Bienvenue à " + arena.theme().label + " !", NamedTextColor.GOLD));
        if (state == State.WAITING && size() >= settings().minPlayers) startCountdown(settings().lobbyCountdown);
        updateBoard();
    }

    /** Arrêt forcé par un admin. */
    public void stop() {
        broadcast(Component.text("Partie arrêtée par un administrateur.", NamedTextColor.RED));
        reset();
    }

    public void shutdown() {
        reset();
    }

    /** Retire tout ce qui appartient à la manche (voitures, billets, prison, cinématique…). */
    private void clearRound() {
        for (Car c : cars.values()) c.remove();
        cars.clear();
        for (CashPacket p : packets) p.remove();
        packets.clear();
        if (overview != null) {
            overview.remove();
            overview = null;
        }
        if (cinematic != null) {
            cinematic.remove();
            cinematic = null;
        }
        if (prison != null) {
            prison.remove();
            prison = null;
        }
        if (events != null) {
            events.cleanup(players());
            events = null;
        }
        if (bar != null) {
            for (Player p : players()) p.hideBossBar(bar);
            bar = null;
        }
        money = 0;
        timeLeft = 0;
    }

    /** Nettoie tout et renvoie chaque joueur là où il était avant de rejoindre. */
    private void reset() {
        state = State.WAITING;
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearRound();
        votes.clear();
        bots.clear();
        forcedTeam.clear();
        for (UUID id : new ArrayList<>(snapshots.keySet())) {
            Player p = Bukkit.getPlayer(id);
            PlayerSnapshot snap = snapshots.remove(id);
            if (p == null) continue;
            plugin.arenas().unbind(p);
            if (bar != null) p.hideBossBar(bar);
            snap.restore(p);
        }
        snapshots.clear();
        bar = null;
        board = null;
        objective = null;
        shownLines = 0;
    }

    // ================================================================== HUD

    private void updateBar() {
        if (bar == null) return;
        float progress = target <= 0 ? 0f : Math.min(1f, money / (float) target);
        bar.progress(progress);
        bar.name(Component.text("💰 Butin des voleurs : ", NamedTextColor.WHITE)
                .append(Component.text("$" + money, NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" / $" + target, NamedTextColor.GRAY)));
    }

    private void ensureBoard() {
        if (board != null) return;
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("gta", Criteria.DUMMY,
                Component.text("GRAND THEFT AUTO", NamedTextColor.GOLD, TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
    }

    private void updateBoard() {
        if (board == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(arena.theme().label, NamedTextColor.YELLOW));
        lines.add(Component.empty());
        if (state == State.RUNNING || state == State.ENDING) {
            int sec = Math.max(0, timeLeft / 20);
            int thieves = 0, alive = 0, police = 0;
            for (Car c : cars.values()) {
                if (c.team == GtaTeam.THIEVES) { thieves++; if (c.alive) alive++; }
                else police++;
            }
            lines.add(Component.text("⏱ Temps : ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%d:%02d", sec / 60, sec % 60), NamedTextColor.YELLOW)));
            lines.add(Component.text("💰 Butin : ", NamedTextColor.GRAY)
                    .append(Component.text("$" + money, NamedTextColor.GREEN))
                    .append(Component.text("/$" + target, NamedTextColor.DARK_GREEN)));
            lines.add(Component.empty().append(Component.text(" ")));
            lines.add(Component.text("Voleurs en fuite : ", NamedTextColor.RED)
                    .append(Component.text(alive + "/" + thieves, NamedTextColor.WHITE)));
            if (prison != null && prison.size() > 0) {
                lines.add(Component.text("En prison : ", NamedTextColor.DARK_RED)
                        .append(Component.text(String.valueOf(prison.size()), NamedTextColor.WHITE)));
            }
            lines.add(Component.text("Policiers : ", NamedTextColor.BLUE)
                    .append(Component.text(String.valueOf(police), NamedTextColor.WHITE)));
            if (events != null && events.active() != null) {
                lines.add(Component.empty().append(Component.text("  ")));
                lines.add(Component.text("⚡ " + events.active().title, events.active().color));
            }
        } else if (state == State.VOTING) {
            lines.add(Component.text("Vote en cours : " + Math.max(0, (voteEndsAt - tick) / 20) + "s", NamedTextColor.GREEN));
            for (Arena a : plugin.arenas().all()) {
                lines.add(Component.text(a.name() + " : ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(votesFor(a)), NamedTextColor.YELLOW)));
                if (lines.size() >= 14) break;
            }
        } else {
            lines.add(Component.text("Joueurs : ", NamedTextColor.GRAY)
                    .append(Component.text(size() + "/" + settings().maxPlayers, NamedTextColor.WHITE)));
            if (!bots.isEmpty()) {
                lines.add(Component.text("  dont bots : ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(String.valueOf(bots.size()), NamedTextColor.GRAY)));
            }
            int n = size();
            if (n >= 2) {
                int cops = settings().policeFor(n);
                lines.add(Component.text("Police : ", NamedTextColor.BLUE).append(Component.text(String.valueOf(cops), NamedTextColor.WHITE))
                        .append(Component.text("  Voleurs : ", NamedTextColor.RED)).append(Component.text(String.valueOf(n - cops), NamedTextColor.WHITE)));
            }
            lines.add(state == State.COUNTDOWN
                    ? Component.text("Départ dans " + countdown + "s", NamedTextColor.GOLD)
                    : Component.text("En attente…", NamedTextColor.YELLOW));
        }

        for (int i = 0; i < lines.size(); i++) {
            var score = objective.getScore("line" + i);
            score.setScore(lines.size() - i);
            score.customName(lines.get(i));
        }
        for (int i = lines.size(); i < shownLines; i++) board.resetScores("line" + i);
        shownLines = lines.size();
    }
}

package fr.cleangang.gta.command;

import fr.cleangang.gta.GtaPlugin;
import fr.cleangang.gta.arena.Arena;
import fr.cleangang.gta.city.CityTheme;
import fr.cleangang.gta.stats.StatsManager;
import fr.cleangang.gta.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class GtaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("join", "leave", "list", "vote", "menu", "stats", "top");
    private static final List<String> ADMIN_SUBS = List.of("admin", "test", "bot", "hubitem", "create", "wand", "regen", "delete", "setlobby", "tp", "start", "stop", "reload");

    private final GtaPlugin plugin;

    public GtaCommand(GtaPlugin plugin) {
        this.plugin = plugin;
    }

    private static void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Game.PREFIX.append(Component.text(text, color)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission("gta.admin")) {
            msg(sender, "Tu n'as pas la permission.", NamedTextColor.RED);
            return true;
        }

        switch (sub) {
            case "join" -> join(sender, args);
            case "leave" -> {
                if (!(sender instanceof Player p)) return true;
                Game g = plugin.arenas().gameOf(p);
                if (g == null) msg(p, "Tu n'es dans aucune partie.", NamedTextColor.RED);
                else {
                    g.leave(p, false);
                    plugin.hub().giveHubItem(p);
                }
            }
            case "list" -> {
                if (plugin.arenas().all().isEmpty()) {
                    msg(sender, "Aucune ville. Un admin peut en créer une avec /gta create <nom>.", NamedTextColor.GRAY);
                }
                for (Arena a : plugin.arenas().all()) {
                    Game g = plugin.arenas().game(a);
                    msg(sender, a.name() + " — " + g.state().name().toLowerCase() + " (" + g.size() + " joueurs)", NamedTextColor.YELLOW);
                }
            }
            case "admin" -> {
                if (sender instanceof Player p) AdminMenu.open(plugin, p);
            }
            case "vote" -> {
                if (!(sender instanceof Player p)) return true;
                Game g = plugin.arenas().gameOf(p);
                if (g == null || g.state() != Game.State.VOTING) msg(p, "Aucun vote en cours.", NamedTextColor.RED);
                else fr.cleangang.gta.lobby.CityMenu.open(plugin, p, fr.cleangang.gta.lobby.CityMenu.Mode.VOTE);
            }
            case "menu" -> {
                if (sender instanceof Player p) fr.cleangang.gta.lobby.CityMenu.open(plugin, p, fr.cleangang.gta.lobby.CityMenu.Mode.JOIN);
            }
            case "hubitem" -> {
                if (sender instanceof Player p) p.getInventory().addItem(fr.cleangang.gta.lobby.LobbyItems.create(plugin, fr.cleangang.gta.lobby.LobbyItems.Type.HUB));
            }
            case "test" -> test(sender, args);
            case "bot" -> bot(sender, args);
            case "stats" -> stats(sender, args);
            case "top" -> top(sender, args);
            case "create" -> create(sender, args);
            case "wand" -> wand(sender, args);
            case "regen" -> regen(sender, args);
            case "delete" -> {
                Arena a = arena(sender, args, 1);
                if (a == null) return true;
                plugin.arenas().remove(a);
                msg(sender, "Arène " + a.name() + " supprimée (les blocs restent en place).", NamedTextColor.GREEN);
            }
            case "setlobby" -> {
                if (!(sender instanceof Player p)) return true;
                Arena a = arena(sender, args, 1);
                if (a == null) return true;
                a.setLobby(p.getLocation());
                plugin.arenas().save();
                msg(p, "Point d'attente de " + a.name() + " défini ici.", NamedTextColor.GREEN);
            }
            case "tp" -> {
                if (!(sender instanceof Player p)) return true;
                Arena a = arena(sender, args, 1);
                if (a != null) p.teleport(a.lobby());
            }
            case "start" -> {
                Arena a = arena(sender, args, 1);
                if (a == null) return true;
                plugin.arenas().game(a).forceStart();
                msg(sender, "Départ forcé.", NamedTextColor.GREEN);
            }
            case "stop" -> {
                Arena a = arena(sender, args, 1);
                if (a == null) return true;
                plugin.arenas().game(a).stop();
                msg(sender, "Partie arrêtée.", NamedTextColor.GREEN);
            }
            case "reload" -> {
                plugin.reloadSettings();
                msg(sender, "Configuration rechargée.", NamedTextColor.GREEN);
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        msg(s, "/gta join [ville] — rejoindre une partie", NamedTextColor.YELLOW);
        msg(s, "/gta leave — quitter", NamedTextColor.YELLOW);
        msg(s, "/gta list — villes disponibles · /gta menu — menu des villes · /gta vote — voter", NamedTextColor.YELLOW);
        msg(s, "/gta stats [joueur] — statistiques · /gta top [victoires|arrestations|butin|record|parties]", NamedTextColor.YELLOW);
        if (s.hasPermission("gta.admin")) {
            msg(s, "/gta test [nb bots] [police|voleur] [ville] — partie de test immédiate avec des bots", NamedTextColor.GOLD);
            msg(s, "/gta bot add <nb> [police|voleur] · /gta bot remove [nb] · /gta bot clear — gérer les bots du lobby", NamedTextColor.GOLD);
            msg(s, "/gta admin — menu des réglages (équipes, joueurs, durée, butin)", NamedTextColor.GOLD);
            msg(s, "/gta wand <nom> [liberty|vice|sanandreas] — bâton : vise un bloc, clic droit pour poser la ville", NamedTextColor.GOLD);
            msg(s, "/gta create <nom> [thème] [seed] — génère une ville 128x128 à tes pieds (vers l'est et le sud)", NamedTextColor.GOLD);
            msg(s, "/gta regen <nom> — reconstruit la ville à l'identique", NamedTextColor.GOLD);
            msg(s, "/gta delete|setlobby|tp|start|stop <nom>, /gta reload", NamedTextColor.GOLD);
        }
    }

    /** Arène nommée en argument, ou la seule existante si aucune n'est précisée. */
    private Arena arena(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Arena a = plugin.arenas().get(args[index]);
            if (a == null) msg(sender, "Ville inconnue : " + args[index], NamedTextColor.RED);
            return a;
        }
        if (plugin.arenas().all().size() == 1) return plugin.arenas().all().iterator().next();
        msg(sender, "Précise le nom de la ville (/gta list).", NamedTextColor.RED);
        return null;
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (plugin.arenas().gameOf(p) != null) {
            msg(p, "Tu es déjà dans une partie (/gta leave).", NamedTextColor.RED);
            return;
        }
        Arena a = arena(sender, args, 1);
        if (a == null) return;
        if (plugin.creator().isBuilding(a.name())) {
            msg(p, "Cette ville est en construction, patiente un peu.", NamedTextColor.RED);
            return;
        }
        plugin.arenas().game(a).join(p);
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Commande réservée aux joueurs.", NamedTextColor.RED);
            return;
        }
        if (args.length < 2) {
            msg(p, "/gta create <nom> [liberty|vice|sanandreas] [seed]", NamedTextColor.RED);
            return;
        }
        String name = args[1];
        if (plugin.arenas().get(name) != null) {
            msg(p, "Une ville porte déjà ce nom (/gta regen " + name + " pour la reconstruire).", NamedTextColor.RED);
            return;
        }
        CityTheme theme = args.length > 2 ? CityTheme.parse(args[2]) : null;
        long seed = parseSeed(args, theme != null ? 3 : 2);
        Location l = p.getLocation();
        // Le bloc sous les pieds devient le niveau de la route ; la ville s'étend vers +X et +Z
        Arena arena = new Arena(name, l.getWorld().getName(), l.getBlockX(), l.getBlockY() - 1, l.getBlockZ(), seed, theme, null);
        build(p, arena, true);
    }

    private static fr.cleangang.gta.game.GtaTeam parseTeam(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "police", "flic", "policier", "cop" -> fr.cleangang.gta.game.GtaTeam.POLICE;
            case "voleur", "voleurs", "thief", "thieves" -> fr.cleangang.gta.game.GtaTeam.THIEVES;
            default -> null;
        };
    }

    private static int parseCount(String s, int def) {
        try {
            return Math.max(1, Math.min(15, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * /gta test [nb] [police|voleur] [ville] : l'admin rejoint la ville, choisit son équipe,
     * le lobby est complété par des bots et la partie démarre dans 3 secondes.
     */
    private void test(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        int count = 3;
        fr.cleangang.gta.game.GtaTeam team = null;
        Arena chosen = null;
        for (int i = 1; i < args.length; i++) {
            fr.cleangang.gta.game.GtaTeam t = parseTeam(args[i]);
            if (t != null) { team = t; continue; }
            if (args[i].matches("\\d+")) { count = parseCount(args[i], 3); continue; }
            chosen = plugin.arenas().get(args[i]);
            if (chosen == null) {
                msg(p, "Ville inconnue : " + args[i], NamedTextColor.RED);
                return;
            }
        }
        if (chosen == null) {
            Game current = plugin.arenas().gameOf(p);
            if (current != null) chosen = current.arena();
            else if (plugin.arenas().all().size() == 1) chosen = plugin.arenas().all().iterator().next();
            else {
                msg(p, "Précise la ville : /gta test [nb] [police|voleur] <ville>", NamedTextColor.RED);
                return;
            }
        }
        Game g = plugin.arenas().game(chosen);
        Game mine = plugin.arenas().gameOf(p);
        if (mine != null && mine != g) {
            msg(p, "Quitte d'abord ta partie actuelle (/gta leave).", NamedTextColor.RED);
            return;
        }
        if (g.state() != Game.State.WAITING && g.state() != Game.State.COUNTDOWN) {
            msg(p, "Une partie est déjà en cours dans cette ville (/gta stop " + chosen.name() + ").", NamedTextColor.RED);
            return;
        }
        if (mine == null && !g.join(p)) return;
        g.forceTeam(p, team);
        // L'équipe choisie par l'admin est servie en priorité, les bots complètent le reste
        g.addBots(count, null);
        g.quickStart();
        msg(p, "Partie de test : " + g.botCount() + " bot(s)" + (team == null ? "" : ", tu es " + team.member.toLowerCase())
                + ". Départ dans 3 s.", NamedTextColor.GOLD);
    }

    private void bot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        Game g = plugin.arenas().gameOf(p);
        if (g == null) {
            msg(p, "Rejoins d'abord une ville (/gta join) — les bots s'ajoutent à ton lobby.", NamedTextColor.RED);
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase() : "add";
        switch (action) {
            case "add" -> {
                int n = args.length >= 3 ? parseCount(args[2], 1) : 1;
                fr.cleangang.gta.game.GtaTeam team = args.length >= 4 ? parseTeam(args[3]) : null;
                int added = g.addBots(n, team);
                if (added == 0) msg(p, "Impossible d'ajouter des bots (partie en cours ou lobby plein).", NamedTextColor.RED);
            }
            case "remove" -> {
                int n = args.length >= 3 ? parseCount(args[2], 1) : 1;
                msg(p, g.removeBots(n) + " bot(s) retiré(s).", NamedTextColor.GRAY);
            }
            case "clear" -> msg(p, g.removeBots(99) + " bot(s) retiré(s).", NamedTextColor.GRAY);
            default -> msg(p, "/gta bot add <nb> [police|voleur] · remove [nb] · clear", NamedTextColor.RED);
        }
    }

    private void stats(CommandSender sender, String[] args) {
        StatsManager.Entry e;
        if (args.length >= 2) e = plugin.stats().byName(args[1]);
        else if (sender instanceof Player p) e = plugin.stats().get(p.getUniqueId());
        else e = null;
        if (e == null) {
            msg(sender, "Aucune statistique pour l'instant.", NamedTextColor.GRAY);
            return;
        }
        int rate = e.played == 0 ? 0 : Math.round(100f * e.wins / e.played);
        sender.sendMessage(Game.PREFIX.append(Component.text("Statistiques de " + e.name, NamedTextColor.GOLD)));
        line(sender, "Parties", e.played + " (" + e.policeGames + " en police, " + e.thiefGames + " en voleur)");
        line(sender, "Victoires", e.wins + " (" + rate + " %)");
        line(sender, "Arrestations", String.valueOf(e.arrests));
        line(sender, "Fois arrêté", String.valueOf(e.busted));
        line(sender, "Butin total", "$" + e.money);
        line(sender, "Record en une partie", "$" + e.bestLoot);
    }

    private static void line(CommandSender s, String label, String value) {
        s.sendMessage(Component.text("  " + label + " : ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE)));
    }

    private void top(CommandSender sender, String[] args) {
        StatsManager.Ranking r = StatsManager.Ranking.parse(args.length >= 2 ? args[1] : null);
        if (r == null) {
            msg(sender, "Classements : victoires, arrestations, butin, record, parties.", NamedTextColor.RED);
            return;
        }
        List<StatsManager.Entry> top = plugin.stats().top(r, 10);
        sender.sendMessage(Game.PREFIX.append(Component.text("Top 10 — " + r.label, NamedTextColor.GOLD)));
        if (top.isEmpty()) msg(sender, "Personne pour l'instant.", NamedTextColor.GRAY);
        for (int i = 0; i < top.size(); i++) {
            StatsManager.Entry e = top.get(i);
            NamedTextColor medal = i == 0 ? NamedTextColor.GOLD : i == 1 ? NamedTextColor.WHITE : i == 2 ? NamedTextColor.RED : NamedTextColor.GRAY;
            String value = (r == StatsManager.Ranking.MONEY || r == StatsManager.Ranking.BEST ? "$" : "") + r.value(e);
            sender.sendMessage(Component.text("  " + (i + 1) + ". ", medal)
                    .append(Component.text(e.name, NamedTextColor.WHITE))
                    .append(Component.text(" — " + value, NamedTextColor.YELLOW)));
        }
    }

    private void wand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (args.length < 2) {
            msg(p, "/gta wand <nom> [liberty|vice|sanandreas] [seed]", NamedTextColor.RED);
            return;
        }
        if (plugin.arenas().get(args[1]) != null) {
            msg(p, "Une ville porte déjà ce nom.", NamedTextColor.RED);
            return;
        }
        CityTheme theme = args.length > 2 ? CityTheme.parse(args[2]) : null;
        if (theme == null) theme = CityTheme.LIBERTY;
        boolean themed = args.length > 2 && CityTheme.parse(args[2]) != null;
        p.getInventory().addItem(plugin.wand().create(args[1], parseSeed(args, themed ? 3 : 2), theme));
        msg(p, "Bâton de ville reçu : vise le sol pour voir le contour, clic droit pour poser.", NamedTextColor.GOLD);
    }

    private void regen(CommandSender sender, String[] args) {
        Arena a = arena(sender, args, 1);
        if (a == null) return;
        Game g = plugin.arenas().game(a);
        if (g.size() > 0) g.stop();
        build(sender, a, false);
    }

    private void build(CommandSender sender, Arena arena, boolean register) {
        plugin.creator().build(sender, arena, register);
    }

    /** Seed tapée en argument (nombre ou texte), sinon aléatoire. */
    public static long parseSeed(String[] args, int index) {
        if (args.length <= index) return ThreadLocalRandom.current().nextLong();
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException e) {
            return args[index].hashCode();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (sender.hasPermission("gta.admin")) subs.addAll(ADMIN_SUBS);
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("bot")) {
            for (String o : List.of("add", "remove", "clear")) if (o.startsWith(args[1].toLowerCase())) out.add(o);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("bot")) {
            for (String o : List.of("police", "voleur")) if (o.startsWith(args[3].toLowerCase())) out.add(o);
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("test")) {
            String last = args[args.length - 1].toLowerCase();
            for (String o : List.of("police", "voleur")) if (o.startsWith(last)) out.add(o);
            for (Arena ar : plugin.arenas().all()) if (ar.name().toLowerCase().startsWith(last)) out.add(ar.name());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            for (StatsManager.Ranking r : StatsManager.Ranking.values()) if (r.id.startsWith(args[1].toLowerCase())) out.add(r.id);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            for (Player pl : org.bukkit.Bukkit.getOnlinePlayers()) if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(pl.getName());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("wand"))) {
            for (CityTheme t : CityTheme.values()) if (t.id.startsWith(args[2].toLowerCase())) out.add(t.id);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("wand")
                && !args[0].equalsIgnoreCase("leave") && !args[0].equalsIgnoreCase("admin")
                && !args[0].equalsIgnoreCase("top") && !args[0].equalsIgnoreCase("stats")
                && !args[0].equalsIgnoreCase("bot") && !args[0].equalsIgnoreCase("test")) {
            for (Arena a : plugin.arenas().all()) {
                if (a.name().toLowerCase().startsWith(args[1].toLowerCase())) out.add(a.name());
            }
        }
        return out;
    }
}

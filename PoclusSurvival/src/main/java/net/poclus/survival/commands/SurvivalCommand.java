package net.poclus.survival.commands;

import net.poclus.survival.PoclusSurvival;
import net.poclus.survival.managers.GameManager;
import net.poclus.survival.models.GameState;
import net.poclus.survival.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class SurvivalCommand implements CommandExecutor, TabCompleter {

    private final PoclusSurvival plugin;

    public SurvivalCommand(PoclusSurvival plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return color(plugin.getConfig().getString("messages.prefix", "&8[&6Poclus&eSMP&8] "));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        GameManager gm = plugin.getGameManager();

        switch (args[0].toLowerCase()) {

            case "join" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                if (!player.hasPermission("poclus.survival.play")) {
                    player.sendMessage(prefix() + color("&cYou don't have permission to join."));
                    return true;
                }
                if (gm.getState() != GameState.COUNTDOWN) {
                    player.sendMessage(prefix() + color("&cThere is no game accepting players right now."));
                    return true;
                }
                boolean joined = gm.joinGame(player);
                if (!joined) {
                    player.sendMessage(prefix() + color("&cCould not join: game is full or you're already in!"));
                }
            }

            case "leave" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                boolean left = gm.leaveGame(player);
                if (!left) {
                    player.sendMessage(prefix() + color("&cYou are not in a game."));
                } else {
                    player.sendMessage(prefix() + color("&aYou have left the game."));
                }
            }

            case "status" -> {
                sender.sendMessage(color("&8--- &6Poclus LMS Status &8---"));
                sender.sendMessage(color("&fState: &e" + gm.getState().name()));
                sender.sendMessage(color("&fPlayers: &e" + gm.getParticipantCount()));
            }

            case "leaderboard", "lb" -> {
                sender.sendMessage(color("&8--- &6&lPoclus LMS Leaderboard &8---"));
                sender.sendMessage(color("&e&lTop Wins:"));
                List<PlayerData> topWins = plugin.getLeaderboardManager().getTopByWins(5);
                for (int i = 0; i < topWins.size(); i++) {
                    PlayerData d = topWins.get(i);
                    String medal = i == 0 ? "§6#1" : i == 1 ? "§7#2" : i == 2 ? "§c#3" : "§f#" + (i + 1);
                    sender.sendMessage(color(" " + medal + " &f" + d.getName()
                            + " &7- &aWins: &f" + d.getWins()
                            + " &7| &eKills: &f" + d.getKills()
                            + " &7| &7Games: &f" + d.getGamesPlayed()));
                }
            }

            // ── ADMIN COMMANDS ──────────────────────────────────────────────

            case "start" -> {
                if (!sender.hasPermission("poclus.survival.admin")) { noPerms(sender); return true; }
                if (gm.isGameRunning()) {
                    sender.sendMessage(prefix() + color("&cA game is already running!"));
                    return true;
                }
                gm.startCountdown();
                sender.sendMessage(prefix() + color("&aGame countdown started!"));
            }

            case "stop" -> {
                if (!sender.hasPermission("poclus.survival.admin")) { noPerms(sender); return true; }
                if (!gm.isGameRunning()) {
                    sender.sendMessage(prefix() + color("&cNo game is running."));
                    return true;
                }
                gm.forceStop();
                sender.sendMessage(prefix() + color("&aGame stopped."));
            }

            case "setlobby" -> {
                if (!sender.hasPermission("poclus.survival.admin")) { noPerms(sender); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                var loc = player.getLocation();
                plugin.getConfig().set("lobby.world", loc.getWorld().getName());
                plugin.getConfig().set("lobby.x", loc.getX());
                plugin.getConfig().set("lobby.y", loc.getY());
                plugin.getConfig().set("lobby.z", loc.getZ());
                plugin.getConfig().set("lobby.yaw", loc.getYaw());
                plugin.getConfig().set("lobby.pitch", loc.getPitch());
                plugin.saveConfig();
                sender.sendMessage(prefix() + color("&aLobby spawn set to your location!"));
            }

            case "setarena" -> {
                if (!sender.hasPermission("poclus.survival.admin")) { noPerms(sender); return true; }
                if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
                var loc = player.getLocation();
                plugin.getConfig().set("arena.world", loc.getWorld().getName());
                plugin.getConfig().set("arena.x", loc.getX());
                plugin.getConfig().set("arena.y", loc.getY());
                plugin.getConfig().set("arena.z", loc.getZ());
                plugin.saveConfig();
                sender.sendMessage(prefix() + color("&aArena spawn set to your location!"));
            }

            case "reload" -> {
                if (!sender.hasPermission("poclus.survival.admin")) { noPerms(sender); return true; }
                plugin.reloadConfig();
                plugin.getSchedulerManager().startAutoScheduler();
                sender.sendMessage(prefix() + color("&aConfig reloaded!"));
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8--- &6&lPoclus Survival &8---"));
        sender.sendMessage(color("&f/survival join &7- Join the current game"));
        sender.sendMessage(color("&f/survival leave &7- Leave the current game"));
        sender.sendMessage(color("&f/survival status &7- View game status"));
        sender.sendMessage(color("&f/survival leaderboard &7- View top players"));
        if (sender.hasPermission("poclus.survival.admin")) {
            sender.sendMessage(color("&c/survival start &7- Force start a game"));
            sender.sendMessage(color("&c/survival stop &7- Force stop the game"));
            sender.sendMessage(color("&c/survival setlobby &7- Set lobby spawn"));
            sender.sendMessage(color("&c/survival setarena &7- Set arena center"));
            sender.sendMessage(color("&c/survival reload &7- Reload config"));
        }
    }

    private void noPerms(CommandSender sender) {
        sender.sendMessage(prefix() + color("&cYou don't have permission to do that."));
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> all = Arrays.asList("join", "leave", "status", "leaderboard");
            if (sender.hasPermission("poclus.survival.admin")) {
                all = Arrays.asList("join", "leave", "status", "leaderboard",
                        "start", "stop", "setlobby", "setarena", "reload");
            }
            return all.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}

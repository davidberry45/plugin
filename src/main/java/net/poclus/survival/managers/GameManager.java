package net.poclus.survival.managers;

import net.poclus.survival.PoclusSurvival;
import net.poclus.survival.models.GameState;
import net.poclus.survival.models.PlayerData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {

    private final PoclusSurvival plugin;
    private GameState state = GameState.IDLE;

    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, Location> savedLocations = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();

    private BukkitTask currentTask;
    private BossBar bossBar;
    private World gameWorld;
    private int countdown;

    // Kill tracking for current game
    private final Map<UUID, Integer> sessionKills = new HashMap<>();

    public GameManager(PoclusSurvival plugin) {
        this.plugin = plugin;
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public boolean isGameRunning() {
        return state != GameState.IDLE && state != GameState.ENDED;
    }

    public GameState getState() { return state; }

    public boolean isParticipant(Player p) { return participants.contains(p.getUniqueId()); }
    public boolean isSpectator(Player p) { return spectators.contains(p.getUniqueId()); }

    public int getParticipantCount() { return participants.size(); }

    public boolean joinGame(Player player) {
        if (state != GameState.COUNTDOWN) return false;
        if (participants.contains(player.getUniqueId())) return false;
        int max = plugin.getConfig().getInt("game.max-players", 20);
        if (participants.size() >= max) return false;

        savedLocations.put(player.getUniqueId(), player.getLocation().clone());
        savedInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());

        participants.add(player.getUniqueId());
        player.getInventory().clear();

        String msg = color(plugin.getConfig().getString("messages.player-joined", "")
                .replace("%player%", player.getName())
                .replace("%current%", String.valueOf(participants.size()))
                .replace("%max%", String.valueOf(max)));

        broadcastToAll(plugin.getConfig().getString("messages.prefix", "") + msg);
        return true;
    }

    public boolean leaveGame(Player player) {
        if (!participants.contains(player.getUniqueId())) return false;
        eliminatePlayer(player, null, false);
        return true;
    }

    /** Start a countdown sequence then launch the game */
    public void startCountdown() {
        if (state != GameState.IDLE) return;
        state = GameState.COUNTDOWN;
        countdown = plugin.getConfig().getInt("game.countdown", 30);

        String startMsg = color(plugin.getConfig().getString("messages.prefix", "")
                + plugin.getConfig().getString("messages.game-starting", "")
                .replace("%seconds%", String.valueOf(countdown)));
        Bukkit.broadcast(Component.text(startMsg));

        setupBossBar("§e§lLMS §7- §aJoin with §f/survival join", BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

        currentTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdown <= 0) {
                    cancel();
                    int min = plugin.getConfig().getInt("game.min-players", 2);
                    if (participants.size() < min) {
                        broadcastToAll(color(plugin.getConfig().getString("messages.prefix", "")
                                + plugin.getConfig().getString("messages.not-enough-players", "")));
                        resetGame();
                        return;
                    }
                    launchGame();
                    return;
                }
                if (countdown <= 10 || countdown % 10 == 0) {
                    Bukkit.broadcast(Component.text(color("§8[§6PoclusSMP§8] §aGame starting in §e" + countdown + " §aseconds! §f/survival join")));
                }
                updateBossBar("§e§lLMS §7- §aStarting in §f" + countdown + "s",
                        (float) countdown / plugin.getConfig().getInt("game.countdown", 30));
                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void forceStop() {
        if (state == GameState.IDLE) return;
        broadcastToAll(color("§8[§6PoclusSMP§8] §cThe game has been force-stopped by an admin."));
        for (UUID uuid : new HashSet<>(participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) restorePlayer(p);
        }
        resetGame();
    }

    // ── Internal game flow ──────────────────────────────────────────────────

    private void launchGame() {
        state = GameState.GRACE_PERIOD;
        gameWorld = resolveGameWorld();
        sessionKills.clear();

        // Teleport all players to arena
        Location arenaSpawn = getArenaSpawn();
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) { participants.remove(uuid); continue; }
            savedLocations.put(uuid, p.getLocation().clone());
            savedInventories.put(uuid, p.getInventory().getContents().clone());
            p.getInventory().clear();
            p.teleport(arenaSpawn);
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
        }

        // Set world border
        if (gameWorld != null) {
            WorldBorder border = gameWorld.getWorldBorder();
            border.setCenter(arenaSpawn);
            double startSize = plugin.getConfig().getInt("game.border-start-size", 200) * 2.0;
            border.setSize(startSize);
        }

        int gracePeriod = plugin.getConfig().getInt("game.grace-period", 60);
        String msg = color(plugin.getConfig().getString("messages.prefix", "")
                + plugin.getConfig().getString("messages.game-started", "")
                .replace("%grace%", String.valueOf(gracePeriod)));
        broadcastToAll(msg);

        updateBossBar("§e§lLMS §7- §aGrace Period: §f" + gracePeriod + "s", 1.0f);

        // Grace period countdown then shrink
        currentTask = new BukkitRunnable() {
            int graceLeft = gracePeriod;
            @Override
            public void run() {
                if (graceLeft <= 0) {
                    cancel();
                    startShrinking();
                    return;
                }
                updateBossBar("§e§lLMS §7- §aGrace: §f" + graceLeft + "s §7| §fAlive: §e" + participants.size(),
                        (float) graceLeft / gracePeriod);
                if (graceLeft == 10) {
                    broadcastToParticipants(color("§c§lBorder shrinks in 10 seconds!"));
                }
                graceLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startShrinking() {
        state = GameState.SHRINKING;
        broadcastToAll(color(plugin.getConfig().getString("messages.prefix", "")
                + plugin.getConfig().getString("messages.border-shrinking", "")));

        int shrinkDuration = plugin.getConfig().getInt("game.shrink-duration", 300);
        double endSize = plugin.getConfig().getInt("game.border-end-size", 10) * 2.0;

        if (gameWorld != null) {
            gameWorld.getWorldBorder().setSize(endSize, shrinkDuration);
        }

        currentTask = new BukkitRunnable() {
            int timeLeft = shrinkDuration;
            @Override
            public void run() {
                if (participants.size() <= 1) {
                    cancel();
                    endGame();
                    return;
                }
                if (timeLeft <= 0) {
                    cancel();
                    endGame();
                    return;
                }
                double progress = (double) timeLeft / shrinkDuration;
                updateBossBar("§c§lBORDER SHRINKING §7| §fAlive: §e" + participants.size()
                        + " §7| §fTime: §c" + timeLeft + "s", (float) progress);
                timeLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void endGame() {
        state = GameState.ENDED;
        removeBossBar();

        List<UUID> remaining = new ArrayList<>(participants);

        // Award places
        for (int i = 0; i < remaining.size() && i < 3; i++) {
            Player p = Bukkit.getPlayer(remaining.get(i));
            if (p == null) continue;
            PlayerData data = plugin.getLeaderboardManager().getOrCreate(p.getUniqueId(), p.getName());
            data.addGame();
            if (i == 0) {
                data.addWin();
                giveRewards(p, "rewards.winner");
                String winMsg = color(plugin.getConfig().getString("messages.prefix", "")
                        + plugin.getConfig().getString("messages.player-won", "")
                        .replace("%player%", p.getName()));
                Bukkit.broadcast(Component.text(winMsg));
            } else if (i == 1) {
                giveRewards(p, "rewards.runner-up");
            } else {
                giveRewards(p, "rewards.top3");
            }
        }

        // Save kill stats
        for (Map.Entry<UUID, Integer> entry : sessionKills.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : "Unknown";
            PlayerData data = plugin.getLeaderboardManager().getOrCreate(entry.getKey(), name);
            for (int k = 0; k < entry.getValue(); k++) data.addKill();
        }

        // Track remaining players who didn't place top
        for (int i = 3; i < remaining.size(); i++) {
            Player p = Bukkit.getPlayer(remaining.get(i));
            if (p != null) {
                plugin.getLeaderboardManager().getOrCreate(p.getUniqueId(), p.getName()).addGame();
            }
        }

        plugin.getLeaderboardManager().save();

        // Restore everyone after 5 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(participants)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) restorePlayer(p);
                }
                for (UUID uuid : new HashSet<>(spectators)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.setGameMode(GameMode.SURVIVAL);
                        restorePlayer(p);
                    }
                }
                resetGame();
            }
        }.runTaskLater(plugin, 100L);
    }

    public void eliminatePlayer(Player player, Player killer, boolean announce) {
        participants.remove(player.getUniqueId());
        sessionKills.merge(player.getUniqueId(), 0, Integer::sum);

        if (killer != null) {
            sessionKills.merge(killer.getUniqueId(), 1, Integer::sum);
        }

        PlayerData data = plugin.getLeaderboardManager()
                .getOrCreate(player.getUniqueId(), player.getName());
        data.addGame();

        if (announce && state == GameState.SHRINKING || state == GameState.GRACE_PERIOD) {
            String msg = color(plugin.getConfig().getString("messages.prefix", "")
                    + plugin.getConfig().getString("messages.player-eliminated", "")
                    .replace("%player%", player.getName())
                    .replace("%remaining%", String.valueOf(participants.size())));
            broadcastToAll(msg);
        }

        // Move to spectator
        spectators.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);

        if (participants.size() <= 1 && isGameRunning()) {
            if (currentTask != null) currentTask.cancel();
            endGame();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void restorePlayer(Player player) {
        Location loc = savedLocations.getOrDefault(player.getUniqueId(), getLobbySpawn());
        ItemStack[] inv = savedInventories.get(player.getUniqueId());
        player.teleport(loc);
        player.setGameMode(GameMode.SURVIVAL);
        if (inv != null) player.getInventory().setContents(inv);
        player.setHealth(20);
        player.setFoodLevel(20);
    }

    private void resetGame() {
        if (currentTask != null) { currentTask.cancel(); currentTask = null; }
        participants.clear();
        spectators.clear();
        savedLocations.clear();
        savedInventories.clear();
        sessionKills.clear();
        removeBossBar();
        if (gameWorld != null) {
            WorldBorder border = gameWorld.getWorldBorder();
            border.setSize(plugin.getConfig().getInt("game.border-start-size", 200) * 2.0);
            gameWorld = null;
        }
        state = GameState.IDLE;
    }

    private void giveRewards(Player player, String configPath) {
        List<String> commands = plugin.getConfig().getStringList(configPath);
        for (String cmd : commands) {
            String resolved = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private void broadcastToAll(String msg) {
        Bukkit.broadcast(Component.text(msg));
    }

    private void broadcastToParticipants(String msg) {
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }

    private void setupBossBar(String title, BossBar.Color color, BossBar.Overlay overlay) {
        removeBossBar();
        bossBar = BossBar.bossBar(Component.text(title), 1.0f, color, overlay);
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showBossBar(bossBar);
        }
    }

    private void updateBossBar(String title, float progress) {
        if (bossBar == null) return;
        bossBar.name(Component.text(title));
        bossBar.progress(Math.max(0f, Math.min(1f, progress)));
    }

    private void removeBossBar() {
        if (bossBar == null) return;
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(bossBar);
        }
        for (UUID uuid : spectators) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(bossBar);
        }
        bossBar = null;
    }

    private Location getLobbySpawn() {
        String worldName = plugin.getConfig().getString("lobby.world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w,
                plugin.getConfig().getDouble("lobby.x", 0),
                plugin.getConfig().getDouble("lobby.y", 64),
                plugin.getConfig().getDouble("lobby.z", 0),
                (float) plugin.getConfig().getDouble("lobby.yaw", 0),
                (float) plugin.getConfig().getDouble("lobby.pitch", 0));
    }

    private Location getArenaSpawn() {
        String worldName = plugin.getConfig().getString("arena.world", "survival_arena");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w,
                plugin.getConfig().getDouble("arena.x", 0),
                plugin.getConfig().getDouble("arena.y", 64),
                plugin.getConfig().getDouble("arena.z", 0));
    }

    private World resolveGameWorld() {
        String worldName = plugin.getConfig().getString("arena.world", "survival_arena");
        World w = Bukkit.getWorld(worldName);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    public BossBar getBossBar() { return bossBar; }
    public Set<UUID> getParticipants() { return participants; }
}

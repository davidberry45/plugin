package net.poclus.survival;

import net.poclus.survival.commands.SurvivalCommand;
import net.poclus.survival.listeners.GameListener;
import net.poclus.survival.listeners.SpectatorListener;
import net.poclus.survival.managers.GameManager;
import net.poclus.survival.managers.LeaderboardManager;
import net.poclus.survival.managers.SchedulerManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PoclusSurvival extends JavaPlugin {

    private static PoclusSurvival instance;
    private GameManager gameManager;
    private LeaderboardManager leaderboardManager;
    private SchedulerManager schedulerManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        leaderboardManager = new LeaderboardManager(this);
        gameManager = new GameManager(this);
        schedulerManager = new SchedulerManager(this);

        getCommand("survival").setExecutor(new SurvivalCommand(this));
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new SpectatorListener(this), this);

        schedulerManager.startAutoScheduler();

        getLogger().info("PoclusSurvival enabled! Good luck on Poclus SMP!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameRunning()) {
            gameManager.forceStop();
        }
        if (leaderboardManager != null) {
            leaderboardManager.save();
        }
        getLogger().info("PoclusSurvival disabled.");
    }

    public static PoclusSurvival getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public SchedulerManager getSchedulerManager() { return schedulerManager; }
}

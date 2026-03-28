package net.poclus.survival.managers;

import net.poclus.survival.PoclusSurvival;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class SchedulerManager {

    private final PoclusSurvival plugin;
    private BukkitTask autoTask;

    public SchedulerManager(PoclusSurvival plugin) {
        this.plugin = plugin;
    }

    public void startAutoScheduler() {
        stopAutoScheduler();
        int intervalMinutes = plugin.getConfig().getInt("game.auto-start-interval", 60);
        long intervalTicks = intervalMinutes * 60L * 20L;

        autoTask = new BukkitRunnable() {
            @Override
            public void run() {
                GameManager gm = plugin.getGameManager();
                if (!gm.isGameRunning()) {
                    gm.startCountdown();
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);

        plugin.getLogger().info("Auto-scheduler set: every " + intervalMinutes + " minutes.");
    }

    public void stopAutoScheduler() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }
}

package net.poclus.survival.listeners;

import net.poclus.survival.PoclusSurvival;
import net.poclus.survival.managers.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class GameListener implements Listener {

    private final PoclusSurvival plugin;

    public GameListener(PoclusSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();

        if (!gm.isParticipant(player)) return;

        Player killer = player.getKiller();
        event.setDeathMessage(null);
        event.setKeepInventory(true);

        gm.eliminatePlayer(player, killer, true);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();

        if (gm.isSpectator(player)) {
            // Already set to SPECTATOR gamemode by eliminatePlayer
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();

        if (gm.isParticipant(player)) {
            gm.eliminatePlayer(player, null, true);
        }
    }
}

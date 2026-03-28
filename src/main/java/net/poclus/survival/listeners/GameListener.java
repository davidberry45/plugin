package net.poclus.survival.listeners;

import net.poclus.survival.PoclusSurvival;
import net.poclus.survival.managers.GameManager;
import net.poclus.survival.models.GameState;
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
        Player player = event.getEntity();
        GameManager gm = plugin.getGameManager();

        if (!gm.isParticipant(player)) return;

        Player killer = player.getKiller();
        event.setDeathMessage(null); // suppress default message

        gm.eliminatePlayer(player, killer, true);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();

        // If they were a participant who just died, they're now a spectator
        // Teleport respawn to arena so they can spectate
        if (gm.isSpectator(player)) {
            // They'll respawn and be in SPECTATOR gamemode already
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

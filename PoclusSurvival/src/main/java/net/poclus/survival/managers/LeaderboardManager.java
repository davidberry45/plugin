package net.poclus.survival.managers;

import net.poclus.survival.PoclusSurvival;
import net.poclus.survival.models.PlayerData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardManager {

    private final PoclusSurvival plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    public LeaderboardManager(PoclusSurvival plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (dataConfig.contains("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                String name = dataConfig.getString("players." + key + ".name", "Unknown");
                int wins = dataConfig.getInt("players." + key + ".wins", 0);
                int games = dataConfig.getInt("players." + key + ".gamesPlayed", 0);
                int kills = dataConfig.getInt("players." + key + ".kills", 0);
                playerDataMap.put(uuid, new PlayerData(uuid, name, wins, games, kills));
            }
        }
    }

    public void save() {
        for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
            String path = "players." + entry.getKey();
            PlayerData d = entry.getValue();
            dataConfig.set(path + ".name", d.getName());
            dataConfig.set(path + ".wins", d.getWins());
            dataConfig.set(path + ".gamesPlayed", d.getGamesPlayed());
            dataConfig.set(path + ".kills", d.getKills());
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public PlayerData getOrCreate(UUID uuid, String name) {
        return playerDataMap.computeIfAbsent(uuid, k -> new PlayerData(k, name));
    }

    public List<PlayerData> getTopByWins(int limit) {
        return playerDataMap.values().stream()
                .sorted(Comparator.comparingInt(PlayerData::getWins).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<PlayerData> getTopByKills(int limit) {
        return playerDataMap.values().stream()
                .sorted(Comparator.comparingInt(PlayerData::getKills).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}

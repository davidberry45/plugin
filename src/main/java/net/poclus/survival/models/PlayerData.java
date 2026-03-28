package net.poclus.survival.models;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final String name;
    private int wins;
    private int gamesPlayed;
    private int kills;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.wins = 0;
        this.gamesPlayed = 0;
        this.kills = 0;
    }

    public PlayerData(UUID uuid, String name, int wins, int gamesPlayed, int kills) {
        this.uuid = uuid;
        this.name = name;
        this.wins = wins;
        this.gamesPlayed = gamesPlayed;
        this.kills = kills;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public int getWins() { return wins; }
    public int getGamesPlayed() { return gamesPlayed; }
    public int getKills() { return kills; }

    public void addWin() { wins++; }
    public void addGame() { gamesPlayed++; }
    public void addKill() { kills++; }
}

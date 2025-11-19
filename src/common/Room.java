package common;

public class Room {
    private int id;
    private String name;
    private int currentPlayers;
    private int maxPlayers;

    public Room(int id, String name, int currentPlayers, int maxPlayers) {
        this.id = id;
        this.name = name;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getCurrentPlayers() { return currentPlayers; }
    public int getMaxPlayers() { return maxPlayers; }

    @Override
    public String toString() {
        return "#" + id + " | " + name + " (" + currentPlayers + "/" + maxPlayers + ")";
    }
}

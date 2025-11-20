package common;

import java.util.ArrayList;
import java.util.List;

public class Room {

    private int id;
    private String name;
    private List<String> players;  // 방에 속한 플레이어 이름들
    private int limit = 10;        // 최대 인원

    public Room(int id, String name) {
        this.id = id;
        this.name = name;
        this.players = new ArrayList<>();
    }

    public int getId() { return id; }
    public String getName() { return name; }

    public List<String> getPlayers() {
        return players;
    }

    public int getLimit() {
        return limit;
    }

    public int getCurrentPlayers() {
        return players.size();
    }

    @Override
    public String toString() {
        return "#" + id + " " + name + " (" + getCurrentPlayers() + "/" + limit + ")";
    }
}

package common;

import java.util.ArrayList;
import java.util.List;

/**
 * 게임 방 정보 클래스.
 * - id: 방 고유 번호
 * - name: 방 이름
 * - players: 방에 속한 플레이어 닉네임 목록
 * - limit: 최대 인원 수
 * - hostNickname: 방장 닉네임 (게임 시작 권한자)
 */
public class Room {

    private int id;
    private String name;
    private List<String> players;   // 방에 속한 플레이어 이름들
    private int limit = 10;         // 최대 인원
    private String hostNickname;    // 방장 닉네임

    public Room(int id, String name) {
        this.id = id;
        this.name = name;
        this.players = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getPlayers() {
        return players;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    /** 방장 닉네임 반환 (없으면 null) */
    public String getHostNickname() {
        return hostNickname;
    }

    /** 방장 닉네임 설정 */
    public void setHostNickname(String hostNickname) {
        this.hostNickname = hostNickname;
    }

    /** 현재 인원 수 */
    public int getCurrentPlayers() {
        return players.size();
    }

    @Override
    public String toString() {
        return "#" + id + " " + name + " (" + getCurrentPlayers() + "/" + limit + ")";
    }
}

package server;

import common.Room;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * 마피아 게임용 ClientHandler
 * - 방 입장 / 채팅
 * - 역할 배정
 * - 낮(토론 → 투표) / 밤(능력 사용)
 * - 사망 / 고스트 채팅 / 승리 조건
 */
public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private List<ClientHandler> clients;
    private List<Room> rooms;

    // 접속 유저 정보
    private Room currentRoom;      // 현재 들어간 방
    private String nickname;       // 유저 닉네임

    // 🔥 낮 투표 저장소 (방별: Room → (투표자 → 대상))
    private static Map<Room, Map<String, String>> roomVotes = new HashMap<>();

    // 🔥 밤 능력 저장 (방별)
    private static Map<Room, String> mafiaTargets  = new HashMap<>();
    private static Map<Room, String> doctorTargets = new HashMap<>();

    // 🔥 방별 역할 저장 (Room → (닉네임 → 역할))
    private static Map<Room, Map<String, String>> roomRoles = new HashMap<>();

    // 🔥 방별 사망자 저장 (Room → Set<닉네임>)
    private static Map<Room, Set<String>> deadPlayers = new HashMap<>();

    // 🔥 종료된 방 (더 이상 낮/밤 진행 X)
    private static Set<Room> finishedRooms =
            Collections.synchronizedSet(new HashSet<>());

    public ClientHandler(Socket socket, List<ClientHandler> clients, List<Room> rooms) {
        this.socket = socket;
        this.clients = clients;
        this.rooms = rooms;

        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        } catch (IOException e) {
            System.out.println("스트림 생성 실패");
        }
    }

    @Override
    public void run() {
        try {
            String message;

            while ((message = in.readLine()) != null) {
                System.out.println("📨 받은 메시지: " + message);

                if ("GET_ROOMS".equals(message)) {
                    sendRoomList();

                } else if (message.startsWith("CREATE_ROOM|")) {
                    createRoom(message.substring("CREATE_ROOM|".length()));

                } else if (message.startsWith("JOIN_ROOM|")) {
                    handleJoinRoom(message);

                } else if (message.startsWith("CHAT|")) {
                    handleChat(message);

                } else if (message.startsWith("GET_PLAYERS|")) {
                    handleGetPlayers(message);

                } else if (message.startsWith("START_GAME|")) {
                    handleStartGame(message);

                } else if (message.startsWith("VOTE|")) {
                    handleVote(message);          // 낮 투표

                } else if (message.startsWith("NIGHT_ACTION|")) {
                    handleNightAction(message);   // 밤 능력
                }
            }

        } catch (IOException e) {
            System.out.println("❗ 클라이언트 연결 종료됨");
        } finally {
            try {
                clients.remove(this);
                socket.close();
            } catch (IOException ex) {}
        }
    }

    // ---------------------------------------------------------
    //                 기본 방 / 채팅 / 목록
    // ---------------------------------------------------------

    /** 🔵 방 목록 전송 (이 클라에게만) */
    private void sendRoomList() {
        StringBuilder builder = new StringBuilder("ROOM_LIST|");

        synchronized (rooms) {
            for (Room r : rooms) {
                builder.append("#")
                        .append(r.getId()).append(" ")
                        .append(r.getName())
                        .append(" (")
                        .append(r.getPlayers().size())
                        .append("/")
                        .append(r.getLimit())
                        .append("),");
            }
        }

        send(builder.toString());
    }

    /** 🔵 방 생성 */
    /** 🔵 방 생성 */
private void createRoom(String payload) {
    // payload 형식: nickname|roomName  (예: CREATE_ROOM|다예|테스트방)
    String creatorNickname;
    String roomName;

    String[] parts = payload.split("\\|");
    if (parts.length >= 2) {
        creatorNickname = parts[0].trim();
        roomName = parts[1].trim();
    } else {
        // 예전 방식 호환: payload 전체를 방 이름으로 사용
        creatorNickname = null;
        roomName = payload.trim();
    }

    Room newRoom = new Room(Server.roomIdCounter++, roomName);

    // 방장 닉네임 저장 (게임 시작 권한 부여용)
    if (creatorNickname != null && !creatorNickname.isEmpty()) {
        newRoom.setHostNickname(creatorNickname);
    }

    synchronized (rooms) {
        rooms.add(newRoom);
    }

    System.out.println("🆕 방 생성됨 → " + newRoom.getName()
            + (newRoom.getHostNickname() != null ? " (host=" + newRoom.getHostNickname() + ")" : ""));
    Server.broadcastRoomList();
}


    /** 🔵 방 참가 */
    private void handleJoinRoom(String msg) {

        String[] parts = msg.split("\\|");
        if (parts.length < 3) return;

        this.nickname = parts[1];
        String roomId = parts[2];

        Room target = Server.findRoomById(roomId);
        if (target == null) {
            send("JOIN_FAIL|NOT_FOUND");
            return;
        }

        if (target.getPlayers().size() >= target.getLimit()) {
            send("JOIN_FAIL|FULL");
            return;
        }

        target.getPlayers().add(nickname);
        this.currentRoom = target;

       System.out.println("🙋 참가 성공 → " + nickname + " / " + target.getName());

// 방장 정보가 비어 있으면 첫 참가자를 방장으로 설정 (구버전 호환)
String host = target.getHostNickname();
if (host == null || host.isEmpty()) {
    if (!target.getPlayers().isEmpty()) {
        host = target.getPlayers().get(0);
    } else {
        host = nickname;
    }
    target.setHostNickname(host);
}

// 클라이언트에게 방장 닉네임까지 함께 전달
send("JOIN_OK|" + target.getId() + "|" + target.getName() + "|" + host);

broadcastPlayerList(target);
Server.broadcastRoomList();

    }

    /** 🔵 GET_PLAYERS 요청 처리 */
    private void handleGetPlayers(String msg) {
        String roomId = msg.substring("GET_PLAYERS|".length());
        Room target = Server.findRoomById(roomId);
        if (target == null) return;

        StringBuilder sb = new StringBuilder("PLAYER_LIST|");
        for (String p : target.getPlayers())
            sb.append(p).append(",");

        send(sb.toString());
    }

    /** 🔥 방 전체에 PLAYER_LIST 전송 */
    private void broadcastPlayerList(Room room) {
        StringBuilder sb = new StringBuilder("PLAYER_LIST|");

        for (String p : room.getPlayers())
            sb.append(p).append(",");

        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.currentRoom == room) {
                    ch.send(sb.toString());
                }
            }
        }
    }

    /** 🔥 현재 방에서 이 닉네임이 죽었는지 여부 */
    private boolean isDead(Room room, String nick) {
        if (room == null || nick == null) return false;
        Set<String> set = deadPlayers.get(room);
        return set != null && set.contains(nick);
    }

    /** 🔥 사망 처리 */
    private void markDead(Room room, String nick) {
        if (room == null || nick == null) return;
        deadPlayers.computeIfAbsent(room, r -> new HashSet<>()).add(nick);
        System.out.println("💀 사망 처리: " + nick + " (방: " + room.getName() + ")");
    }

    /** 🔵 채팅 처리 (생존 채팅 / 고스트 채팅 분리) */
    private void handleChat(String msg) {
        if (currentRoom == null) return;

        String[] p = msg.split("\\|", 3);
        if (p.length < 3) return;

        String sender = p[1];
        String text   = p[2];

        boolean senderDead = isDead(currentRoom, sender);

        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.currentRoom != currentRoom) continue;

                // 👻 관전(ghost) 채팅: 죽은 사람끼리만 공유
                if (senderDead) {
                    if (isDead(ch.currentRoom, ch.nickname)) {
                        ch.send("GHOST_CHAT|" + sender + "|" + text);
                    }
                }
                // 일반 채팅: 생존자끼리만 공유
                else {
                    if (!isDead(ch.currentRoom, ch.nickname)) {
                        ch.send(msg);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    //            🔥🔥 게임 시작 + 역할 배정 🔥🔥
    // ---------------------------------------------------------

   private void handleStartGame(String msg) {

    if (currentRoom == null) return;
    if (finishedRooms.contains(currentRoom)) return;

    // 🔐 방장만 게임 시작 가능하도록 체크
    String requester = null;
    String[] parts = msg != null ? msg.split("\\|") : new String[0];
    if (parts.length >= 2) {
        requester = parts[1].trim();
    }

    String host = currentRoom.getHostNickname();

    // host 정보가 없으면 첫 번째 플레이어를 방장으로 간주 (구버전 호환)
    if (host == null || host.isEmpty()) {
        if (!currentRoom.getPlayers().isEmpty()) {
            host = currentRoom.getPlayers().get(0);
            currentRoom.setHostNickname(host);
        }
    }

    // 요청자가 방장이 아니면 거절
    if (host != null && requester != null && !host.equals(requester)) {
        send("ERROR|NOT_HOST");
        System.out.println("⛔ 비방장(" + requester + ")의 게임 시작 요청 거절. host=" + host);
        return;
    }

    System.out.println("🎮 게임 시작 요청! 방: " + currentRoom.getName()
            + " / host=" + host + ", requester=" + requester);

    Map<String, String> roles = assignRoles(currentRoom);
    roomRoles.put(currentRoom, roles);

    synchronized (clients) {
        for (ClientHandler ch : clients) {
            if (ch.currentRoom == currentRoom) {
                String playerNickname = ch.nickname;
                String role = roles.get(playerNickname);
                ch.send("ROLE|" + playerNickname + "|" + role);
            }
        }
    }

    // 낮/밤 루프 시작
    startDayPhase();
}


    /** 🔥 역할 자동 배정 */
    private Map<String, String> assignRoles(Room room) {

        List<String> players = room.getPlayers();
        int count = players.size();

        int mafiaCount;
        if (count <= 6) mafiaCount = 1;
        else if (count <= 8) mafiaCount = 2;
        else mafiaCount = 3;

        int doctorCount = 1;
        int policeCount = 1;

        int civilianCount = count - (mafiaCount + doctorCount + policeCount);

        List<String> rolesPool = new ArrayList<>();

        for (int i = 0; i < mafiaCount; i++) rolesPool.add("MAFIA");
        for (int i = 0; i < doctorCount; i++) rolesPool.add("DOCTOR");
        for (int i = 0; i < policeCount; i++) rolesPool.add("POLICE");
        for (int i = 0; i < civilianCount; i++) rolesPool.add("CIVILIAN");

        Collections.shuffle(rolesPool);

        Map<String, String> assigned = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            assigned.put(players.get(i), rolesPool.get(i));
        }

        System.out.println("🧩 역할 배정 완료: " + assigned);
        return assigned;
    }

    /** 🔵 방 전체에 메시지 전송 (현재 방 기준) */
    private void broadcastToRoom(String msg) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.currentRoom == currentRoom) {
                    ch.send(msg);
                }
            }
        }
    }

    // ---------------------------------------------------------
    //                 🔥 낮 / 밤 / 투표 루프
    // ---------------------------------------------------------

    /** 🔥 낮 시작 → 토론 후 투표로 */
    private void startDayPhase() {
        if (currentRoom == null || finishedRooms.contains(currentRoom)) return;

        broadcastToRoom("DAY_START|discussion");

        new Thread(() -> {
            try {
                // 🔥 테스트용: 낮 토론 10초
                Thread.sleep(10000);
            } catch (Exception ignored) {}

            if (currentRoom == null || finishedRooms.contains(currentRoom)) return;

            // 낮 투표 시작 알림
            broadcastToRoom("VOTE_START");

            try {
                // 🔥 테스트용: 낮 투표 10초
                Thread.sleep(10000);
            } catch (Exception ignored) {}

            if (currentRoom == null || finishedRooms.contains(currentRoom)) return;

            finishVotePhase();
        }).start();
    }

    // ---------------------------------------------------------
    //                       🔥 낮 투표
    // ---------------------------------------------------------

    /** 🔥 클라이언트에서 온 VOTE 처리 */
    private void handleVote(String msg) {

    // VOTE|투표자|대상
    String[] p = msg.split("\\|");
    if (p.length < 3 || currentRoom == null) return;

    String voter  = p[1];
    String target = p[2];

    // 죽은 사람은 투표 불가
    if (isDead(currentRoom, voter)) {
        return;
    }

    // 🔥 방별 투표 맵에 저장
    synchronized (roomVotes) {
        Map<String, String> voteMap = roomVotes.computeIfAbsent(currentRoom, r -> new HashMap<>());
        voteMap.put(voter, target);
    }

    System.out.println("🗳 낮 투표: " + voter + " → " + target);
}


    private void finishVotePhase() {

        if (currentRoom == null || finishedRooms.contains(currentRoom)) return;

        Map<String, String> voteMap;
        synchronized (roomVotes) {
            voteMap = roomVotes.get(currentRoom);
        }

    // 아무도 투표 안 했으면
        if (voteMap == null || voteMap.isEmpty()) {
            broadcastToRoom("VOTE_RESULT|NONE");
            return;
        }

        Map<String, Integer> counter = new HashMap<>();

        for (String target : voteMap.values()) {
            counter.put(target, counter.getOrDefault(target, 0) + 1);
        }

        String dead = null;
        int max = 0;

        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            String user = entry.getKey();
            int c = entry.getValue();
            if (c > max) {
                max = c;
                dead = user;
            }
        }

        if (dead == null) {
            broadcastToRoom("VOTE_RESULT|NONE");
        } else {
            markDead(currentRoom, dead);
            broadcastToRoom("VOTE_RESULT|" + dead);
        }

        broadcastPlayerList(currentRoom);

    // 🔁 다음 낮에는 이전 투표 기록 초기화
        synchronized (roomVotes) {
            roomVotes.remove(currentRoom);
        }

    // 승리 조건 체크
        if (checkGameOver()) {
            return;
        }

    // 밤으로 이동
        startNightPhase();
    }


    // ---------------------------------------------------------
    //                      🔥 밤 능력
    // ---------------------------------------------------------

    /** 🔥 밤 시작 (능력 사용 단계) */
    private void startNightPhase() {
        if (currentRoom == null || finishedRooms.contains(currentRoom)) return;

        broadcastToRoom("NIGHT_START|power");

        new Thread(() -> {
            try {
                // 🔥 테스트용: 밤 10초
                Thread.sleep(10000);
            } catch (Exception ignored) {}

            if (currentRoom == null || finishedRooms.contains(currentRoom)) return;

            resolveNightActions();

            if (checkGameOver()) {
                return;
            }

            startDayPhase();
        }).start();
    }

    /** 🔥 클라이언트 밤 능력 처리 */
    private void handleNightAction(String msg) {
        // NIGHT_ACTION|닉네임|ROLE|타겟닉
        String[] p = msg.split("\\|");
        if (p.length < 4 || currentRoom == null) return;

        String actor  = p[1];
        String role   = p[2];
        String target = p[3];

        // 죽은 사람은 능력 사용 불가
        if (isDead(currentRoom, actor)) {
            return;
        }

        // 마피아 자기 자신 선택 불가
        if ("MAFIA".equals(role) && actor.equals(target)) {
            System.out.println("❌ 마피아 자기 자신 선택 시도 → 무시");
            synchronized (clients) {
                for (ClientHandler ch : clients) {
                    if (ch.currentRoom == currentRoom && actor.equals(ch.nickname)) {
                        ch.send("CHAT|SERVER|❌ 마피아는 자신을 선택할 수 없습니다.");
                    }
                }
            }
            return; // 자기 자신 선택을 무시
        }

        System.out.println("🌙 야간 행동: " + actor + " (" + role + ") → " + target);

        switch (role) {
            case "MAFIA":
                mafiaTargets.put(currentRoom, target); // 자기 자신 선택이 무시된 경우 실행되지 않음
                break;

            case "DOCTOR":
                doctorTargets.put(currentRoom, target);
                break;

            case "POLICE":
                // 경찰은 바로 결과 보내기
                Map<String, String> roles = roomRoles.get(currentRoom);
                String targetRole = roles != null ? roles.get(target) : null;
                String team = (targetRole != null && targetRole.equals("MAFIA")) ? "MAFIA" : targetRole;

                synchronized (clients) {
                    for (ClientHandler ch : clients) {
                        if (ch.currentRoom == currentRoom && actor.equals(ch.nickname)) {
                            ch.send("POLICE_RESULT|" + target + "|" + team);
                        }
                    }
                }
                break;
        }
    }

    /** 🔥 밤 능력 결과 계산 (마피아 킬 vs 의사 힐) */
    private void resolveNightActions() {

        if (currentRoom == null || finishedRooms.contains(currentRoom)) return;

        String mafiaTarget  = mafiaTargets.get(currentRoom);
        String doctorTarget = doctorTargets.get(currentRoom);

        String dead = null;

        if (mafiaTarget != null) {
            if (mafiaTarget.equals(doctorTarget)) {
                // 🚑 의사가 살림
                dead = null;
            } else {
                dead = mafiaTarget;
                markDead(currentRoom, dead);
            }
        }

        mafiaTargets.remove(currentRoom);
        doctorTargets.remove(currentRoom);

        if (dead == null) {
            broadcastToRoom("NIGHT_RESULT|NONE");
        } else {
            broadcastToRoom("NIGHT_RESULT|" + dead);
        }

        broadcastPlayerList(currentRoom);
    }

    // ---------------------------------------------------------
    //                     🔥 승리 조건 체크
    // ---------------------------------------------------------

    /**
     * 마피아 전멸 → 시민 승
     * 마피아 수 >= 시민측 수 → 마피아 승
     */
    private boolean checkGameOver() {
        if (currentRoom == null) return false;
        if (finishedRooms.contains(currentRoom)) return true;

        Map<String, String> roles = roomRoles.get(currentRoom);
        if (roles == null) return false;

        Set<String> dead = deadPlayers.getOrDefault(currentRoom, Collections.emptySet());

        int mafia = 0;
        int others = 0;

        for (String p : currentRoom.getPlayers()) {
            if (dead.contains(p)) continue;
            String role = roles.get(p);
            if ("MAFIA".equals(role)) mafia++;
            else others++;
        }

        String winner = null;
        if (mafia == 0 && (mafia + others) > 0) {
            winner = "CIVIL";
        } else if (mafia >= others && mafia > 0) {
            winner = "MAFIA";
        }

        if (winner != null) {
            finishedRooms.add(currentRoom);
            broadcastToRoom("GAME_OVER|" + winner);
            System.out.println("🏁 게임 종료! 승자: " + winner + " (방: " + currentRoom.getName() + ")");
            return true;
        }

        return false;
    }

    /** 🔵 한 명에게 메시지 전송 */
    public void send(String msg) {
        out.println(msg);
    }
}

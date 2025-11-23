package client.ui;

import client.network.Client;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.util.Duration;
import server.ClientHandler;
import javafx.animation.PauseTransition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GameRoomController {

    @FXML private Label roomTitle;
    @FXML private Label roleLabel;
    @FXML private Label timerLabel;
    @FXML private ListView<String> playerList;
    @FXML private TextArea chatArea;
    @FXML private TextArea ghostChatArea;
    @FXML private TextField inputField;
    @FXML private TextField ghostInput;
    @FXML private Button sendButton;
    @FXML private Button ghostSendButton;
    @FXML private Button startButton;

    private static Client client;
    private static String roomId;
    private static String roomName;
    private static String nickname;
    private static String hostNickname;

    private Thread timerThread;

    private boolean isVoteMode   = false;
    private boolean isNightPhase = false;
    private boolean iAmDead      = false;
    private String myRole;

    private Set<String> deadPlayers = new HashSet<>();
    private String myVoteTarget = null;
    private boolean abilityUsed = false;

    /** LobbyController에서 화면 전환 전에 호출됨 */
    public static void init(Client c, String rId, String rName, String nick, String hostNick) {
        client = c;
        roomId = rId;
        roomName = rName;
        nickname = nick;
        hostNickname = hostNick;
    }

    @FXML
    public void initialize() {

        roomTitle.setText(roomName != null ? "방 이름: " + roomName : "방 이름 불러오는 중...");

        // 방장만 게임 시작 버튼 가능
        if (startButton != null) {
            startButton.setDisable(!nickname.equals(hostNickname));
        }

        if (client != null) {
            client.setMessageHandler(this::onMessageReceived);
            client.requestPlayerList(roomId);
        }

        sendButton.setOnAction(e -> sendChat());
        inputField.setOnAction(e -> sendChat());

        // 고스트 채팅 비활성화
        if (ghostInput != null) ghostInput.setDisable(true);
        if (ghostSendButton != null) ghostSendButton.setDisable(true);

        if (ghostSendButton != null)
            ghostSendButton.setOnAction(e -> sendGhostChat());
        if (ghostInput != null)
            ghostInput.setOnAction(e -> sendGhostChat());

        /** ⭐ 플레이어 클릭 → 투표/능력 처리 */
        playerList.setOnMouseClicked(e -> {
            String target = playerList.getSelectionModel().getSelectedItem();
            if (target == null) return;

            // "(나)" 제거
            String pureTarget = target.replace(" (나)", "");

            // 죽은 사람은 행동 불가
            if (deadPlayers.contains(pureTarget)) {
                chatArea.appendText("❌ 죽은 플레이어에게는 행동할 수 없습니다.\n");
                return;
            }

            // 내가 죽었으면 불가
            if (iAmDead) {
                chatArea.appendText("❌ 사망 상태에서는 행동할 수 없습니다.\n");
                return;
            }

            // ⭐ 자기 자신 선택 방지 (의사만 예외)
            if (isNightPhase && pureTarget.equals(nickname) && !"DOCTOR".equals(myRole)) {
                chatArea.appendText("❌ 자신에게는 능력을 사용할 수 없습니다.\n");
                return;
            }

            // 밤 능력 중복 사용 방지
            if (isNightPhase && abilityUsed) {
                chatArea.appendText("❌ 밤 능력은 한 번만 사용할 수 있습니다.\n");
                return;
            }

            // ⭐ 경찰 능력
            if (isNightPhase && "POLICE".equals(myRole)) {
                client.send("NIGHT_ACTION|" + nickname + "|POLICE|" + pureTarget);
                chatArea.appendText("🔍 [" + pureTarget + "]님을 조사합니다...\n");
                abilityUsed = true;
                return;
            }

            // ⭐ 의사 또는 마피아 능력
            if (isNightPhase && ("MAFIA".equals(myRole) || "DOCTOR".equals(myRole))) {

                // 의사가 자기 자신을 선택한 경우
                if ("DOCTOR".equals(myRole) && pureTarget.equals(nickname)) {
                    chatArea.appendText("💉 자신을 보호합니다!\n");
                } else {
                    chatArea.appendText("🌙 [" + myRole + "] 능력을 [" + pureTarget + "]님에게 사용합니다.\n");
                }

                client.send("NIGHT_ACTION|" + nickname + "|" + myRole + "|" + pureTarget);
                abilityUsed = true;
                return;
            }

            if (isVoteMode && !isNightPhase) {
        myVoteTarget = pureTarget;  // 내 투표 대상 기록
        chatArea.appendText("🗳 [" + pureTarget + "]님에게 투표했습니다.\n");

        // 서버로 투표 메시지 전송
        client.send("VOTE|" + nickname + "|" + pureTarget);

        // UI에 선택 표시를 주고 싶으면 여기서 처리
        refreshPlayerListUI();
    }
        });
    }

    /** 🔵 일반 채팅 */
    private void sendChat() {
        if (iAmDead) {
            chatArea.appendText("❌ 사망 상태에서는 일반 채팅을 사용할 수 없습니다.\n");
            return;
        }
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        client.send("CHAT|" + nickname + "|" + text);
        inputField.clear();
    }

    /** 👻 고스트 채팅 */
    private void sendGhostChat() {
        if (!iAmDead) {
            ghostChatArea.appendText("❌ 살아있는 동안에는 고스트 채팅 불가.\n");
            return;
        }
        String text = ghostInput.getText().trim();
        if (text.isEmpty()) return;

        client.send("GHOST_CHAT|" + nickname + "|" + text);
        ghostInput.clear();
    }

    /** 🔥 서버 메시지 처리 */
    private void onMessageReceived(String msg) {

        if (msg.startsWith("CHAT|")) {
            String[] p = msg.split("\\|", 3);
            Platform.runLater(() ->
                chatArea.appendText(p[1] + ": " + p[2] + "\n")
            );
        }

        else if (msg.startsWith("PLAYER_LIST|")) {
            String[] players = msg.substring("PLAYER_LIST|".length()).split(",");
            Platform.runLater(() -> updatePlayerList(players));
        }

        else if (msg.startsWith("ROLE|")) {
            String[] p = msg.split("\\|");
            if (p[1].equals(nickname)) {
                myRole = p[2];
                Platform.runLater(() -> {
                    roleLabel.setText("당신의 역할: " + myRole);
                    chatArea.appendText("🎭 역할: [" + myRole + "]\n");
                });
            }
        }

        else if (msg.startsWith("DAY_START|")) {
            Platform.runLater(() -> {
                chatArea.appendText("\n🌞 낮이 시작되었습니다!\n");
                if (!iAmDead) {
                    inputField.setDisable(false);
                    sendButton.setDisable(false);
                }
                myVoteTarget = null;
                refreshPlayerListUI();
            });
            isNightPhase = false;
            isVoteMode = false;
            startTimer(10, "낮");
        }

        else if (msg.equals("VOTE_START")) {
            Platform.runLater(() -> {
                chatArea.appendText("\n🗳 투표 시작! 플레이어를 선택하세요.\n");
                playerList.setStyle("-fx-border-color: #ff6b6b; -fx-border-width: 2px;");
                timerLabel.setText("투표 시간: 30초");
            });
            isVoteMode = true;

            startTimer(30, "투표");
        }

        else if (msg.startsWith("VOTE_RESULT|")) {
            // 서버에서 "VOTE_RESULT|deadPlayer" 형태로 메시지를 보낸다고 가정
            String deadPlayer = msg.substring("VOTE_RESULT|".length());

            Platform.runLater(() -> {
                if (!"NONE".equals(deadPlayer)) {
                    handleDeathResult(deadPlayer, "낮");
                } else {
                    chatArea.appendText("\n⚖ 투표 결과: 아무도 죽지 않았습니다.\n");
                }

                isVoteMode = false;
                playerList.setStyle("");
                myVoteTarget = null;
            });
        }

        else if (msg.startsWith("NIGHT_START|")) {
            Platform.runLater(() -> {
                chatArea.appendText("\n🌙 밤이 되었습니다!\n");
                inputField.setDisable(true);
                sendButton.setDisable(true);
            });
            isNightPhase = true;
            isVoteMode = false;
            abilityUsed = false;
            startTimer(30, "밤");
        }

        else if (msg.startsWith("NIGHT_RESULT|")) {
            Platform.runLater(() -> handleDeathResult(msg.substring(13), "밤"));
            isNightPhase = false;
        }

        else if (msg.startsWith("GHOST_CHAT|")) {
            String[] p = msg.split("\\|", 3);
            Platform.runLater(() ->
                ghostChatArea.appendText("👻 " + p[1] + ": " + p[2] + "\n")
            );
        }

        else if (msg.startsWith("GAME_START") || msg.startsWith("GAME_STARTED")
                || msg.startsWith("START_GAME_ACK") || msg.equals("ENTER_GAME")) {
            Platform.runLater(() -> goToGame());
        }

        else if (msg.startsWith("GAME_OVER|")) {
            String winner = msg.substring("GAME_OVER|".length());
            Platform.runLater(() -> handleGameOver(winner));
        }

        else if (msg.startsWith("POLICE_RESULT|")) {
            String[] parts = msg.split("\\|");
            String targetNickname = parts[1];
            String targetRole = parts[2];

            Platform.runLater(() -> {
                if (myRole.equals("POLICE")) {
                    chatArea.appendText("🔍 조사 결과: " + targetNickname + "님의 직업은 [" + targetRole + "]입니다.\n");
                }
            });
        }
    }

   /** 🔥 사망 UI 처리 */
private void handleDeathResult(String dead, String phase) {
    if (dead.equals("NONE")) {
        chatArea.appendText("\n⚖ [" + phase + "] 아무도 죽지 않았습니다.\n");
        return;
    }

    chatArea.appendText("\n💀 [" + phase + "] " + dead + "님 사망\n");
    deadPlayers.add(dead); // 죽은 플레이어를 목록에 추가
    refreshPlayerListUI(); // 플레이어 목록 업데이트

    // 자신이 죽은 경우 처리
    if (dead.equals(nickname)) {
        iAmDead = true;

        inputField.setDisable(true);
        sendButton.setDisable(true);

        ghostInput.setDisable(false);
        ghostSendButton.setDisable(false);

        ghostChatArea.appendText("⚠ 당신은 사망했습니다 → 고스트 채팅만 가능합니다.\n");
    }
}

    /** 🔵 플레이어 리스트 업데이트 */
    private void updatePlayerList(String[] players) {
        playerList.getItems().clear();
        for (String player : players) {
            if (player.equals(nickname)) {
                playerList.getItems().add(player + " (나)");
            } else {
                playerList.getItems().add(player);
            }
        }
        refreshPlayerListUI();
    }

    /** ⭐ 투표 UI + 사망 UI 업데이트 */
    private void refreshPlayerListUI() {

        playerList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setDisable(false);
                    return;
                }

                setText(item);

                
            }
        });
    }

    /** 🔥 타이머 시작 */
    private void startTimer(int seconds, String phaseName) {
        if (timerThread != null && timerThread.isAlive())
            timerThread.interrupt();

        timerThread = new Thread(() -> {
            int time = seconds;

            while (time >= 0 && !Thread.currentThread().isInterrupted()) {

                int t = time;
                Platform.runLater(() ->
                        timerLabel.setText(phaseName + " - " + t + "초"));

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                time--;
            }

            Platform.runLater(() ->
                timerLabel.setText(phaseName + " 종료!")
            );
        });

        timerThread.setDaemon(true);
        timerThread.start();
    }

    /** 🔵 게임 시작 버튼 */
    @FXML
    private void handleStartGame() {
        client.send("START_GAME|" + nickname);
    }

    /** 🔥 게임 종료 처리 */
    private void handleGameOver(String winner) {
        String message = winner.equals("CIVIL")
                ? "🎉 시민팀 승리!"
                : "💀 마피아 팀 승리!";

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("게임 종료");
            alert.setHeaderText(null);
            alert.setContentText(message + "\n로비로 이동합니다.");
            alert.show();

            if (timerThread != null && timerThread.isAlive()) {
                timerThread.interrupt();
            }

            inputField.setDisable(true);
            sendButton.setDisable(true);
            playerList.setDisable(true);
            ghostInput.setDisable(true);
            ghostSendButton.setDisable(true);

            // 3초 후 로비로 이동
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(event -> goToLobby());
            pause.play();
        });
    }

    /** 🔵 게임 화면으로 이동 */
    private void goToGame() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/GameScene.fxml"));
            Parent root = loader.load();

            if (timerThread != null && timerThread.isAlive()) {
                timerThread.interrupt();
            }

            roomTitle.getScene().setRoot(root);

        } catch (Exception e) {
            e.printStackTrace();
            chatArea.appendText("❌ 게임 화면으로 이동 실패\n");
        }
    }

    /** 🔵 로비로 이동 */
    private void goToLobby() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/Lobby.fxml"));
            Parent root = loader.load();

            // 현재 씬의 루트를 로비 화면으로 교체
            roomTitle.getScene().setRoot(root);

        } catch (Exception e) {
            e.printStackTrace();
            chatArea.appendText("❌ 로비로 이동 실패\n");
        }
    }

    private void broadcastGameOver(String winner) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.currentRoom == this.currentRoom) {
                    ch.send("GAME_OVER|" + winner);
                }
            }
        }
    }

    private void checkGameOver() {
        int mafiaCount = 0;
        int civilCount = 0;

        for (String player : roomRoles.get(currentRoom).keySet()) {
            String role = roomRoles.get(currentRoom).get(player);
            if (!isDead(currentRoom, player)) {
                if ("MAFIA".equals(role)) mafiaCount++;
                else civilCount++;
            }
        }

        if (mafiaCount == 0) {
            broadcastGameOver("CIVIL");
        } else if (civilCount == 0) {
            broadcastGameOver("MAFIA");
        }
    }

    private void calculateVoteResult() {
        Map<String, Integer> voteCounts = new HashMap<>();
        String mostVotedPlayer = null;
        int maxVotes = 0;

        // 투표 결과 계산
        for (String voter : votes.keySet()) {
            String target = votes.get(voter);
            if (target == null) continue;

            voteCounts.put(target, voteCounts.getOrDefault(target, 0) + 1);
            if (voteCounts.get(target) > maxVotes) {
                maxVotes = voteCounts.get(target);
                mostVotedPlayer = target;
            }
        }

        // 동률 처리: 아무도 죽지 않음
        long maxVoteCount = voteCounts.values().stream().filter(v -> v == maxVotes).count();
        if (maxVoteCount > 1) {
            mostVotedPlayer = "NONE";
        }

        // 죽은 플레이어 처리
        if (!"NONE".equals(mostVotedPlayer)) {
            markPlayerAsDead(mostVotedPlayer);
        }

        // 투표 결과 브로드캐스트
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.currentRoom == this.currentRoom) {
                    ch.send("VOTE_RESULT|" + mostVotedPlayer);
                }
            }
        }
    }

    private void markPlayerAsDead(String player) {
        if (roomRoles.containsKey(currentRoom)) {
            deadPlayers.get(currentRoom).add(player); // 죽은 플레이어 목록에 추가
            System.out.println("💀 " + player + "님이 사망했습니다.");
        }
    }
}


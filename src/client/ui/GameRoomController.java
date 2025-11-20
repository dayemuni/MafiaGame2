package client.ui;

import client.network.Client;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.HashSet;
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

    private Thread timerThread;

    private boolean isVoteMode   = false;   
    private boolean isNightPhase = false;   
    private boolean iAmDead      = false;   
    private String myRole;                  

    private Set<String> deadPlayers = new HashSet<>();
    private String myVoteTarget = null;      // ⭐ 내가 투표한 대상 저장

    /** LobbyController에서 화면 전환 전에 호출됨 */
    public static void init(Client c, String rId, String rName, String nick) {
        client = c;
        roomId = rId;
        roomName = rName;
        nickname = nick;
    }

    @FXML
    public void initialize() {

        roomTitle.setText(roomName != null ? "방 이름: " + roomName : "방 이름 불러오는 중...");

        if (client != null) {
            client.setMessageHandler(this::onMessageReceived);
            client.requestPlayerList(roomId);
        }

        sendButton.setOnAction(e -> sendChat());
        inputField.setOnAction(e -> sendChat());

        // 👻 고스트 채팅 비활성화
        if (ghostInput != null) ghostInput.setDisable(true);
        if (ghostSendButton != null) ghostSendButton.setDisable(true);

        if (ghostSendButton != null)
            ghostSendButton.setOnAction(e -> sendGhostChat());
        if (ghostInput != null)
            ghostInput.setOnAction(e -> sendGhostChat());

        // ⭐ 플레이어 클릭 → 투표 또는 능력
        playerList.setOnMouseClicked(e -> {
            String target = playerList.getSelectionModel().getSelectedItem();
            if (target == null) return;

            // 죽은 사람은 클릭 불가
            if (deadPlayers.contains(target)) {
                chatArea.appendText("❌ 죽은 플레이어에게는 투표할 수 없습니다.\n");
                return;
            }

            // 내 죽음
            if (iAmDead) {
                chatArea.appendText("❌ 사망 상태에서는 행동할 수 없습니다.\n");
                return;
            }

            // ⭐ 낮 투표
            if (isVoteMode) {
                myVoteTarget = target;      // UI 표시용
                client.send("VOTE|" + nickname + "|" + target);
                chatArea.appendText("🗳 투표 완료: " + target + "\n");

                refreshPlayerListUI();
                isVoteMode = false;
                return;
            }

            // ⭐ 밤 능력
            if (isNightPhase && myRole != null &&
                    (myRole.equals("MAFIA") || myRole.equals("DOCTOR") || myRole.equals("POLICE"))) {

                client.send("NIGHT_ACTION|" + nickname + "|" + myRole + "|" + target);
                chatArea.appendText("🌙 [" + myRole + "] 대상 선택: " + target + "\n");
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

                // ⭐ 투표 UI 강조
                playerList.setStyle("-fx-border-color: #ff6b6b; -fx-border-width: 2px;");

            });
            isVoteMode = true;
        }

        else if (msg.startsWith("VOTE_RESULT|")) {
            Platform.runLater(() -> handleDeathResult(msg.substring(12), "낮"));
            isVoteMode = false;

            playerList.setStyle(""); // 강조 제거
            myVoteTarget = null;
        }

        else if (msg.startsWith("NIGHT_START|")) {
            Platform.runLater(() -> {
                chatArea.appendText("\n🌙 밤이 되었습니다!\n");
                inputField.setDisable(true);
                sendButton.setDisable(true);
            });
            isNightPhase = true;
            isVoteMode = false;
            startTimer(10, "밤");
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

        // 12) 게임 종료
        else if (msg.startsWith("GAME_OVER|")) {
            String winner = msg.substring("GAME_OVER|".length());
            Platform.runLater(() -> handleGameOver(winner));
        }

    }

    /** 🔥 사망 UI 처리 */
    private void handleDeathResult(String dead, String phase) {

        if (dead.equals("NONE")) {
            chatArea.appendText("\n⚖ [" + phase + "] 아무도 죽지 않았습니다.\n");
            return;
        }

        chatArea.appendText("\n💀 [" + phase + "] " + dead + "님 사망\n");
        deadPlayers.add(dead);
        refreshPlayerListUI();

        if (dead.equals(nickname)) {
            iAmDead = true;

            inputField.setDisable(true);
            sendButton.setDisable(true);

            ghostInput.setDisable(false);
            ghostSendButton.setDisable(false);

            ghostChatArea.appendText("⚠ 당신은 사망했습니다 → 고스트 채팅만 가능.\n");
        }
    }

    /** 🔵 플레이어 리스트 업데이트 */
    private void updatePlayerList(String[] players) {
        playerList.getItems().clear();
        for (String p : players) {
            if (!p.trim().isEmpty())
                playerList.getItems().add(p.trim());
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
                    setStyle("");
                    return;
                }

                // 기본 표시
                String label = item;

                // ⭐ 내가 투표한 사람 표시
                if (item.equals(myVoteTarget)) {
                    label = "✔ " + item;
                }

                setText(label);

                // 죽은 사람은 회색 + 비활성
                if (deadPlayers.contains(item)) {
                    setStyle("-fx-text-fill: gray;");
                } else {
                    setStyle("-fx-text-fill: black;");
                }
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

    /** 🔥 게임 종료 처리 → 팝업 후 로비 이동 */
private void handleGameOver(String winner) {

    String message = winner.equals("CIVIL")
            ? "🎉 시민팀 승리!"
            : "💀 마피아 팀 승리!";

    // 팝업 창
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("게임 종료");
    alert.setHeaderText(null);
    alert.setContentText(message + "\n3초 후 로비로 돌아갑니다.");
    alert.show();

    // 타이머 종료
    if (timerThread != null && timerThread.isAlive()) {
        timerThread.interrupt();
    }

    // 모든 행동 비활성화
    inputField.setDisable(true);
    sendButton.setDisable(true);
    playerList.setDisable(true);
    ghostInput.setDisable(true);
    ghostSendButton.setDisable(true);

    // 3초 후 로비로 이동
    new Thread(() -> {
        try { Thread.sleep(3000); } catch (Exception ignored) {}

        Platform.runLater(() -> goToLobby());
    }).start();
}
/** 🔵 로비 화면으로 이동 */
private void goToLobby() {
    try {
        javafx.fxml.FXMLLoader loader =
                new javafx.fxml.FXMLLoader(getClass().getResource("/client/ui/Lobby.fxml"));

        javafx.scene.Parent root = loader.load();

        // 씬 변경
        roomTitle.getScene().setRoot(root);

    } catch (Exception e) {
        e.printStackTrace();
        chatArea.appendText("❌ 로비로 이동 실패\n");
    }
}


}

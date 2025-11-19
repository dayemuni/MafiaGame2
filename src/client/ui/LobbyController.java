package client.ui;

import client.network.Client;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class LobbyController {

    @FXML
    private TextField nicknameField;
    @FXML
    private Button createRoomButton;
    @FXML
    private Button joinRoomButton;
    @FXML
    private ListView<String> roomList;
    @FXML
    private Label statusLabel;

    private Client client = new Client();

    @FXML
    public void initialize() {
        boolean connected = client.connect("localhost", 6000, this::onMessageReceived);

        if (!connected) {
            statusLabel.setText("❌ 서버 연결 실패");
            return;
        }

        statusLabel.setText("서버와 연결됨!");

        // 처음 실행 시 방 목록 요청
        client.requestRoomList();
    }

    /**
     * 🔵 서버에서 온 메시지 처리
     */
    private void onMessageReceived(String msg) {
        System.out.println("서버 → " + msg);

        if (msg.startsWith("ROOM_LIST")) {
            updateRoomList(msg);
        }
    }

    /**
     * 🔵 방 목록 업데이트
     */
    private void updateRoomList(String msg) {
        // 예: ROOM_LIST|#1 방1 (1/10),#2 방2 (5/10)
        String data = msg.substring("ROOM_LIST|".length());
        String[] rooms = data.split(",");

        javafx.application.Platform.runLater(() -> {
            roomList.getItems().clear();
            for (String r : rooms) {
                if (!r.trim().isEmpty()) {
                    roomList.getItems().add(r.trim());
                }
            }
        });
    }

    /**
     * 🔵 방 생성 버튼
     */
    @FXML
    private void handleCreateRoom() {
        String nickname = nicknameField.getText().trim();

        if (nickname.isEmpty()) {
            statusLabel.setText("닉네임을 먼저 입력하세요.");
            return;
        }

        String roomName = nickname + "의 방";
        client.createRoom(roomName);

        statusLabel.setText("방 생성 요청 보냄!");

        client.requestRoomList();
    }

    /**
     * 🔵 방 참가 버튼
     */
    @FXML
    private void handleJoinRoom() {
        String nickname = nicknameField.getText().trim();
        String selectedRoom = roomList.getSelectionModel().getSelectedItem();

        if (nickname.isEmpty()) {
            statusLabel.setText("닉네임을 먼저 입력해주세요.");
            return;
        }

        if (selectedRoom == null) {
            statusLabel.setText("참가할 방을 선택해주세요.");
            return;
        }

        statusLabel.setText("🛠 JOIN_ROOM은 다음 단계에서 구현할게!");
    }
}

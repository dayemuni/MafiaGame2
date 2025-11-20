package client.ui;

import client.network.Client;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LobbyController {

    @FXML private TextField nicknameField;
    @FXML private ListView<String> roomList;
    @FXML private Label statusLabel;
    @FXML private Button createRoomButton;
    @FXML private Button joinRoomButton;

    private Client client = new Client();
    private String nickname;

    @FXML
    public void initialize() {

        boolean connected = client.connect("localhost", 6000, this::onMessageReceived);

        if (!connected) {
            statusLabel.setText("❌ 서버 연결 실패");
            return;
        }

        statusLabel.setText("서버 연결됨!");

        client.requestRoomList();
    }

    /** 🔵 서버에서 오는 메시지 처리 (로비 단계) */
    private void onMessageReceived(String msg) {
        System.out.println("서버 → " + msg);

        if (msg.startsWith("ROOM_LIST|")) {
            updateRoomList(msg);
        }

        // JOIN_OK|방ID|방이름
        if (msg.startsWith("JOIN_OK|")) {
            String[] parts = msg.split("\\|", 3);
            if (parts.length < 3) return;

            String roomId = parts[1];
            String roomName = parts[2];

            Platform.runLater(() -> {
                try {
                    // GameRoomController에 데이터 전달
                    GameRoomController.init(client, roomId, roomName, nickname);

                    // GameRoom.fxml 로드
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/client/ui/GameRoom.fxml")
                    );

                    Scene scene = new Scene(loader.load());
                    Stage stage = (Stage) nicknameField.getScene().getWindow();

                    stage.setScene(scene);
                    stage.show();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    /** 🔵 방 목록 업데이트 */
    private void updateRoomList(String msg) {
        String data = msg.substring("ROOM_LIST|".length());
        String[] rooms = data.split(",");

        Platform.runLater(() -> {
            roomList.getItems().clear();
            for (String r : rooms) {
                if (!r.trim().isEmpty()) {
                    roomList.getItems().add(r.trim());
                }
            }
        });
    }

    /** 🔵 방 생성 버튼 */
    @FXML
    private void handleCreateRoom() {
        nickname = nicknameField.getText().trim();

        if (nickname.isEmpty()) {
            statusLabel.setText("닉네임 입력!");
            return;
        }

        client.createRoom(nickname + "의 방");
        statusLabel.setText("방 생성 요청 보냄!");

        client.requestRoomList();
    }

    /** 🔵 방 참가 버튼 */
    @FXML
    private void handleJoinRoom() {
        nickname = nicknameField.getText().trim();

        if (nickname.isEmpty()) {
            statusLabel.setText("닉네임 입력!");
            return;
        }

        String selected = roomList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("방 선택!");
            return;
        }

        // "#1 ㅇㅇ의 방 (1/10)" → roomId = "1"
        String cleaned = selected.replace(",", "");
        String roomId = cleaned.split(" ")[0].replace("#", "").trim();

        client.joinRoom(nickname, roomId);
    }
}

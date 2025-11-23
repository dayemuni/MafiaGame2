package client.ui;

import client.network.Client;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.Parent;
import javafx.stage.Stage;

public class LobbyController {

    @FXML private TextField nicknameField;
    @FXML private TextField roomNameField;
    @FXML private ListView<String> roomList;
    @FXML private Label statusLabel;

    private Client client;

    @FXML
    public void initialize() {

        client = new Client();

        // 서버 연결 (여기서 메시지 핸들러 등록됨)
        if (!client.connect("localhost", 6000, this::onMessageReceived)) {
            statusLabel.setText("❌ 서버 연결 실패");
            return;
        }

        // 방 목록 요청
        client.send("GET_ROOMS");
    }

    /** 서버에서 오는 메시지 처리 */
    private void onMessageReceived(String msg) {

        // 방 리스트 업데이트
        if (msg.startsWith("ROOM_LIST|")) {
            Platform.runLater(() -> updateRoomList(msg));
        }

        // 방 생성 완료 → 새 목록 자동 반영됨 (서버 broadcast)
        else if (msg.startsWith("ROOM_CREATED")) {
            client.send("GET_ROOMS");
        }

        // 방 입장 성공
        // 방 입장 성공
        else if (msg.startsWith("JOIN_OK|")) {
            String[] p = msg.split("\\|");
            String roomId = p[1];
            String roomName = p[2];
            String hostNickname = (p.length >= 4) ? p[3] : null;

            String myNickname = nicknameField.getText().trim();

            Platform.runLater(() -> enterGameRoom(roomId, roomName, hostNickname, myNickname));
}


        // 방 입장 실패
        else if (msg.startsWith("JOIN_FAIL|")) {
            Platform.runLater(() -> {
                String reason = msg.contains("FULL") ? "방이 꽉 찼습니다." : "방을 찾을 수 없습니다.";
                statusLabel.setText("❌ 입장 실패: " + reason);
            });
        }
    }

    /** 방 리스트 업데이트 */
    private void updateRoomList(String msg) {
        roomList.getItems().clear();

        String data = msg.substring("ROOM_LIST|".length());
        String[] rooms = data.split(",");

        for (String r : rooms) {
            if (!r.trim().isEmpty()) {
                roomList.getItems().add(r.trim());
            }
        }
    }

    /** 방 생성 버튼 클릭 */
    @FXML
    private void handleCreateRoom() {

    String nickname = nicknameField.getText().trim();
    String roomName = roomNameField.getText().trim();

    if (nickname.isEmpty()) {
        statusLabel.setText("❌ 닉네임을 입력하세요.");
        return;
    }
    if (roomName.isEmpty()) {
        statusLabel.setText("❌ 방 이름을 입력하세요.");
        return;
    }

    // 서버에 방 생성 요청 (방장 닉네임 함께 전송)
    client.send("CREATE_ROOM|" + nickname + "|" + roomName);

    statusLabel.setText("방 생성 완료! (방장: " + nickname + ")");
}


    /** 방 입장 버튼 클릭 */
    @FXML
    private void handleJoinRoom() {

        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            statusLabel.setText("❌ 닉네임을 입력하세요.");
            return;
        }

        String selected = roomList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("❌ 입장할 방을 선택하세요.");
            return;
        }

        // "#0 TestRoom (1/10)" → #0 → id = 0
        String roomId = selected.split(" ")[0].substring(1).trim();

        client.send("JOIN_ROOM|" + nickname + "|" + roomId);
        statusLabel.setText("입장 시도 중...");
    }

    /** GameRoom으로 화면 전환 */
    private void enterGameRoom(String roomId, String roomName, String hostNickname, String myNickname) {
    try {
        // 반드시 FXMLLoader.load() 호출 전에 GameRoomController.init(...)을 호출해야
        // GameRoomController.initialize()가 client/room 정보를 사용할 때 null 이 아님.
        // hostNickname 정보를 함께 전달하여 방장만 게임 시작 버튼을 누를 수 있도록 함
        GameRoomController.init(client, roomId, roomName, myNickname, hostNickname);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ui/GameRoom.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        Stage stage = (Stage) nicknameField.getScene().getWindow();
        stage.setScene(scene);

        statusLabel.setText(""); // 입장 상태 초기화

    } catch (Exception e) {
        e.printStackTrace();
        statusLabel.setText("❌ 게임방 화면 로딩 실패");
    }
}

}

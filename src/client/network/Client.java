package client.network;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class Client {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Consumer<String> onMessage;

    public boolean connect(String host, int port, Consumer<String> onMessage) {
        this.onMessage = onMessage;

        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            System.out.println("클라이언트 접속 성공!");

            // 메시지 수신 스레드
            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        if (onMessage != null) {
                            onMessage.accept(msg);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("서버 연결 끊김");
                }
            }).start();

            return true;

        } catch (IOException e) {
            System.out.println("접속 실패: " + e.getMessage());
            return false;
        }
    }

    public void send(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    public void createRoom(String roomName) {
        send("CREATE_ROOM|" + roomName);
    }

    public void requestRoomList() {
        send("GET_ROOMS");
    }
}

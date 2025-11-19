package server;

import java.io.*;
import java.net.*;
import java.util.List;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private List<ClientHandler> clients;
    private Server server;  // 🔵 서버 참조 (방 목록을 가져오기 위함)

    public ClientHandler(Socket socket, Server server, List<ClientHandler> clients) {
        this.socket = socket;
        this.server = server;
        this.clients = clients;

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
            String msg;

            while ((msg = in.readLine()) != null) {
                System.out.println("📨 받은 메시지: " + msg);

                // =============================
                // 🔥 메시지 타입에 따라 분기 처리
                // =============================

                if (msg.startsWith("CREATE_ROOM")) {
                    String roomName = msg.split("\\|")[1];
                    server.createRoom(roomName);
                    server.broadcastRooms();
                }

                else if (msg.equals("GET_ROOMS")) {
                    server.broadcastRooms();
                }

                else {
                    // 기본 채팅 메시지는 전체에게 전송
                    broadcast(msg);
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

    private void broadcast(String msg) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.out.println(msg);
            }
        }
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }
}
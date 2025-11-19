package server;

import java.io.*;
import java.net.*;
import java.util.*;

import common.Room;

public class Server {

    private static final int PORT = 6000;
    public static List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private List<Room> rooms = new ArrayList<>();
    private int roomIdCounter = 1;

    public static void main(String[] args) {
        System.out.println("💡 서버 시작됨! PORT: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("✨ 클라이언트 연결됨: " + socket);

                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);

                new Thread(handler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* ========================================
         🔵 [추가할 코드] 방 생성 기능
       ======================================== */

    public synchronized Room createRoom(String name) {
        Room room = new Room(roomIdCounter++, name, 1, 10); // 기본 최대 10명
        rooms.add(room);
        return room;
    }

    /* ============================
         🔵 모든 클라이언트에게 방 정보 보내기
       ============================ */

    public synchronized void broadcastRooms() {
        StringBuilder sb = new StringBuilder("ROOM_LIST");

        for (Room r : rooms) {
            sb.append("|")
              .append(r.getId()).append(",")
              .append(r.getName()).append(",")
              .append(r.getCurrentPlayers()).append(",")
              .append(r.getMaxPlayers());
        }

        String msg = sb.toString();

        for (ClientHandler ch : clients) {
            ch.sendMessage(msg);
        }
    }

    /* ============================
         🔵 방 목록 가져오기
       ============================ */
    public List<Room> getRooms() {
        return rooms;
    }
}



package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import common.Room;

public class Server {

    private static final int PORT = 6000;

    // 전체 클라이언트 목록
    protected static List<ClientHandler> clients =
            Collections.synchronizedList(new ArrayList<>());

    // 방 목록
    protected static List<Room> rooms =
            Collections.synchronizedList(new ArrayList<>());
    protected static int roomIdCounter = 1;

    public static void main(String[] args) {
        System.out.println("💡 서버 시작됨! PORT: " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("✨ 클라이언트 연결됨: " + socket);

                ClientHandler handler = new ClientHandler(socket, clients, rooms);
                clients.add(handler);

                new Thread(handler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 🔵 방 ID로 Room 찾기 */
    public static Room findRoomById(String id) {
        synchronized (rooms) {
            for (Room r : rooms) {
                if (String.valueOf(r.getId()).equals(id)) {
                    return r;
                }
            }
        }
        return null;
    }

    /** 🔵 전체 방 목록을 모든 클라이언트에게 전송 */
    public static void broadcastRoomList() {
        StringBuilder sb = new StringBuilder("ROOM_LIST|");

        synchronized (rooms) {
            for (Room r : rooms) {
                sb.append("#")
                  .append(r.getId())
                  .append(" ")
                  .append(r.getName())
                  .append(" (")
                  .append(r.getPlayers().size())
                  .append("/")
                  .append(r.getLimit())
                  .append("),");
            }
        }

        String msg = sb.toString();

        synchronized (clients) {
            for (ClientHandler ch : clients) {
                ch.send(msg);
            }
        }
    }
}

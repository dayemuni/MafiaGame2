package client;

import client.network.Client;

public class TestClient {
    public static void main(String[] args) {
        Client client = new Client();

        if (client.connect("localhost", 6000)) {
            System.out.println("클라이언트 접속 성공!");

            // 서버에서 오는 메시지 듣기
            client.listen(msg -> {
                System.out.println("서버 → " + msg);
            });

            // 메시지 전송
            client.send("안녕! 나는 다예 클라이언트야!");

            // 프로그램 종료 방지
            try {
                Thread.sleep(5000); // 5초 동안 대기
            } catch (InterruptedException e) {
            }

        } else {
            System.out.println("❌ 서버 접속 실패!");
        }
    }
}



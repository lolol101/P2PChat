package p2pChat;

import java.util.Scanner;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ChatNode node = new ChatNode();
        node.init();

        System.out.println();
        System.out.println("Libp2p Chatter!");
        System.out.println("===============");
        System.out.println();
        System.out.println("This node is " + node.currentChatName + ", listening on " + node.address);
        System.out.println();
        System.out.println("Enter 'bye' to quit, enter 'alias <name>' to set chat name");
        System.out.println();

        String message;
        do {
            System.out.println(">> ");
            Scanner scanner = new Scanner(System.in);
            message = scanner.nextLine();

            if (message == null || message.isEmpty()) {
                continue;
            }

            node.send(message);
        } while (!"bye".equals(message));
        node.stop();
    }
}

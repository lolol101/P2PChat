package p2pChat;

import java.net.SocketException;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException, SocketException {
        ChatNode node = new ChatNode();
        node.init();

        System.out.println();
        System.out.println("P2P Chatter!");
        System.out.println("===============");
        System.out.println();
        System.out.println("This node is listening on " + node.address);
        System.out.println();
        System.out.println("Enter chat 'name' make or enter existing chat");
        System.out.println("Then write anything you want!)");
        System.out.println();

        String command;
        do {
            System.out.println(">> ");
            Scanner scanner = new Scanner(System.in);
            command = scanner.nextLine();
        } while (node.processCommand(Objects.requireNonNull(command)) != -1);

        node.stop();
    }
}

package p2pChat;

import io.libp2p.core.multiformats.Multiaddr;

import java.net.SocketException;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ChatNode node = new ChatNode();
        String input;

        System.out.println();
        System.out.println("P2P Chatter!");
        System.out.println("===============");
        System.out.println("Here is yours ID:\n\t" + node.getMultiAddress().toString());
        System.out.println("If you want to join the network, please paste any other's user ID");

        Scanner scanner = new Scanner(System.in);
        System.out.print(">> ");
        input = scanner.nextLine();
        if (!input.equals("host")) {
            while (!node.initConnect(new Multiaddr(input))) {
                System.out.println("Sorry, but ID is incorrect(\nTry again!");
                System.out.print(">> ");
                input = scanner.nextLine();
            }
        }

        System.out.println("\n\n\nNow you can enjoy messaging!)");
        System.out.println("Input _help_ for list of commands\n");

        do {
            input = scanner.nextLine();
            if (input.isEmpty()) continue;
            System.out.println();
        } while (node.processCommand(Objects.requireNonNull(input)) != -1);

        node.stop();
    }
}

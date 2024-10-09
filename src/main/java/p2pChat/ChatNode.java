package p2pChat;

import io.libp2p.core.*;
import io.libp2p.core.dsl.HostBuilder;
import io.libp2p.discovery.MDnsDiscovery;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ChatNode {
    private static class Friend {
        public String hashedPeerId;
        public ChatManager.ChatController controller;

        public Friend(String id, ChatManager.ChatController controller) {
            hashedPeerId = id;
            this.controller = controller;
        }
    }

    private HashSet<PeerId> knownNodes = new HashSet<>();
    private HashMap<PeerId, Friend> peersMap = new HashMap<>();
    private Discoverer peerFinder;
    private final Host chatHost;

    public InetAddress address;
    public String hashedPeerId;

    public ChatNode() {
        try {
            var interfaces = NetworkInterface.networkInterfaces();
            var addresses = interfaces
                    .flatMap(i -> Collections.list(i.getInetAddresses()).stream())
                    .filter(a -> a instanceof Inet4Address)
                    .map(a -> (Inet4Address) a)
                    .filter(InetAddress::isSiteLocalAddress)
                    .sorted(Comparator.comparing(InetAddress::getHostAddress))
                    .toList();
            if (!addresses.isEmpty())
                address = addresses.getFirst();
            else {
                address = InetAddress.getLoopbackAddress();
            }
        } catch (SocketException e) {
            System.out.println(e.getMessage());
        }

        chatHost = new HostBuilder()
                .protocol(new ChatManager.Chat(this::messageReceived))
                .listen("/ip4" + address + "/tcp/0")
                .build();
    }

    public void init() throws ExecutionException, InterruptedException {
        chatHost.start().get();
        hashedPeerId = chatHost.getPeerId().toBase58();
        peerFinder = new MDnsDiscovery(chatHost, "$ServiceTag.local.", 120, address);
        peerFinder.start();
    }

    public void stop() {
        peerFinder.stop();
        chatHost.stop();
    }

    public void send(String message) {
        peersMap.forEach((k, v) -> v.controller.send(message));
        
        if (message.startsWith("alias ")) {
            hashedPeerId = message.substring(6).trim();
        }
    }

    private Void messageReceived(PeerId peerId, String message) {
        if (Objects.equals(message, "/who")) {
            peersMap.get(peerId).controller.send("alias " + hashedPeerId);
            return null;
        }

        if (message.startsWith("alias ")) {
            Friend friend = peersMap.get(peerId);
            if (friend == null) return null;
            String previousAlias = friend.hashedPeerId;
            String newAlias = message.substring(6).trim();
            if (!previousAlias.equals(newAlias)) {
                friend.hashedPeerId = newAlias;
                System.out.println(previousAlias + " is now " + newAlias);
            }
        }

        String alias = Optional.ofNullable(peersMap.get(peerId))
                .map(f -> f.hashedPeerId)
                .orElse(peerId.toBase58());
        System.out.println(alias + " > " + message);
        return null;
    }

    private Pair<Stream, ChatManager.ChatController> connectChat(PeerInfo info) {
        try {
            var chat = new ChatManager.Chat(this::messageReceived)
                    .dial(chatHost, info.getPeerId(), info.getAddresses().getFirst());
            return new Pair<>(chat.getStream().get(), chat.getController().get());
        } catch (Exception e) {
            return null;
        }
    }

    private void peerFound(PeerInfo info) {
        if (info.getPeerId().equals(chatHost.getPeerId()) || knownNodes.contains(info.getPeerId())) {
            return;
        }

        knownNodes.add(info.getPeerId());
        Pair<Stream, ChatManager.ChatController> chatConnection = connectChat(info);
        if (chatConnection == null) return;

        chatConnection.getFirst().closeFuture().thenAccept(v -> {
            System.out.println(Optional.ofNullable(peersMap
                    .get(info.getPeerId()))
                    .map(f -> f.hashedPeerId)
                    .orElse("") + " disconnected.");
            peersMap.remove(info.getPeerId());
            knownNodes.remove(info.getPeerId());
        });

        System.out.println("Connected to new peer " + info.getPeerId());
        chatConnection.getSecond().send("/who");
        peersMap.put(info.getPeerId(), new Friend(info.getPeerId().toBase58(), chatConnection.getSecond()));
    }

    private static class Pair<T, U> {
        private final T first;
        private final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }

        public T getFirst() {
            return first;
        }

        public U getSecond() {
            return second;
        }
    }
}


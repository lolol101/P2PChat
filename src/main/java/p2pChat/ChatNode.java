package p2pChat;

import io.libp2p.core.*;
import io.libp2p.core.dsl.HostBuilder;
import io.libp2p.discovery.MDnsDiscovery;
import kotlin.Unit;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class ChatNode {
    private static class Friend {
        public String currentChatName;
        public ChatManager.ChatController controller;

        public Friend(String id, ChatManager.ChatController controller) {
            currentChatName = id;
            this.controller = controller;
        }
    }

    private final HashSet<PeerId> knownNodes = new HashSet<>();
    private final HashMap<PeerId, Friend> peersMap = new HashMap<>();
    private Discoverer peerFinder;
    private final Host chatHost;

    public InetAddress address;
    public String currentChatName;

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
                address = Inet4Address.getLoopbackAddress();
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
        currentChatName = chatHost.getPeerId().toBase58();
        peerFinder = new MDnsDiscovery(chatHost, "_ipfs-discovery._udp.local.", 120, address);
        peerFinder.getNewPeerFoundListeners().add(this::peerFound);
        peerFinder.start();
    }

    public void stop() {
        peerFinder.stop();
        chatHost.stop();
    }

    public void send(String message) {
        peersMap.values().forEach(v -> v.controller.send(message));

        if (message.startsWith("alias ")) {
            currentChatName = message.substring(6).trim();
        }
    }

    private Void messageReceived(PeerId peerId, String message) {
        if (Objects.equals(message, "/who")) {
            peersMap.get(peerId).controller.send("alias " + currentChatName);
            return null;
        }

        if (message.startsWith("alias ")) {
            Friend friend = peersMap.get(peerId);
            if (friend == null) return null;
            String previousAlias = friend.currentChatName;
            String newAlias = message.substring(6).trim();
            if (!previousAlias.equals(newAlias)) {
                friend.currentChatName = newAlias;
                System.out.println(previousAlias + " is now " + newAlias);
            }
        }

        String alias = Optional.ofNullable(peersMap.get(peerId))
                .map(f -> f.currentChatName)
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

    @SuppressWarnings("SameReturnValue")
    private Unit peerFound(PeerInfo info) {
        if (info.getPeerId().equals(chatHost.getPeerId()) || knownNodes.contains(info.getPeerId())) {
            return null;
        }

        knownNodes.add(info.getPeerId());
        Pair<Stream, ChatManager.ChatController> chatConnection = connectChat(info);
        if (chatConnection == null) return null;

        chatConnection.first().closeFuture().thenAccept(v -> {
            System.out.println(Optional.ofNullable(peersMap
                    .get(info.getPeerId()))
                    .map(f -> f.currentChatName)
                    .orElse("") + " disconnected.");
            peersMap.remove(info.getPeerId());
            knownNodes.remove(info.getPeerId());
        });

        System.out.println("Connected to new peer " + info.getPeerId());
        chatConnection.second().send("/who");
        peersMap.put(info.getPeerId(), new Friend(info.getPeerId().toBase58(), chatConnection.second()));
        return null;
    }

    private record Pair<T, U>(T first, U second) {
    }
}


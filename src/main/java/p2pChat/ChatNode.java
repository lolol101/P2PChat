package p2pChat;

import io.libp2p.core.Discoverer;
import io.libp2p.core.Host;
import io.libp2p.core.PeerInfo;
import io.libp2p.core.dsl.HostBuilder;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.discovery.MDnsDiscovery;
import io.libp2p.pubsub.gossip.Gossip;
import io.netty.buffer.Unpooled;
import kotlin.Unit;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import io.ipfs.multiaddr.MultiAddress;

public class ChatNode {
    private final List<Topic> topics;
    private final Host chatHost;
    private final Gossip gossip;
    private final PubsubPublisherApi publisher;
    private Discoverer peerFinder;
    private final Consumer<MessageApi> subscriber = (v) -> {
        System.out.println(v.getData().toString(Charset.defaultCharset()));
    };

    public InetAddress address;
    public String hashedPeerId;

    public ChatNode() throws SocketException {
        topics = new ArrayList<>();
        gossip = new Gossip();
        var interfaces = NetworkInterface.networkInterfaces();
        var addresses = interfaces
                .flatMap(i -> Collections.list(i.getInetAddresses()).stream())
                .filter(a -> a instanceof Inet4Address)
                .map(a -> (Inet4Address) a)
                .filter(InetAddress::isSiteLocalAddress)
                .sorted(Comparator.comparing(InetAddress::getHostAddress))
                .toList();

        if (!addresses.isEmpty()) {
            address = addresses.getFirst();
        }
        else {
            address = Inet4Address.getLoopbackAddress();
        }

        ArrayList<ProtocolBinding> protocols = new ArrayList<>();
        ArrayList<MultiAddress> multiAddresses = new ArrayList<>();
        protocols.add(gossip);
        multiAddresses.add(new MultiAddress("/ip4" + address + "/tcp/0"));


        chatHost = new HostBuilder()
                .protocol(gossip)
                .listen("/ip4" + address + "/tcp/0")
                .build();
        publisher = gossip.createPublisher(chatHost.getPrivKey(), 0);
    }

    public void init() throws ExecutionException, InterruptedException {
        chatHost.start().get();
        hashedPeerId = chatHost.getPeerId().toBase58();
        peerFinder = new MDnsDiscovery(chatHost, "_ipfs-discovery._udp.local.", 120, address);
        peerFinder.getNewPeerFoundListeners().add(this::peerFound);
        peerFinder.start().get();
    }

    public void stop() throws ExecutionException, InterruptedException {
        peerFinder.stop().get();
        chatHost.stop().get();
    }

    public int processCommand(String command) throws ExecutionException, InterruptedException {
        if (command.startsWith("chat ")) {
            var chatName = command.substring(5);
            topics.add(new Topic(chatName));
            gossip.subscribe(subscriber, topics.toArray(new Topic[0]));
        }
        else if (command.startsWith("bye "))
                return 1;
        else {
            publisher.publish(Unpooled.wrappedBuffer(command.getBytes()), topics.toArray(new Topic[0]));
        }
        return 0;
    }

    @SuppressWarnings("SameReturnValue")
    private Unit peerFound(PeerInfo info) {
        try {
            chatHost.getNetwork().connect(info.getPeerId(), info.getAddresses().toArray(new Multiaddr[0])).get();
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

}


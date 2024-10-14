package p2pChat;

import io.ipfs.multiaddr.MultiAddress;
import io.libp2p.core.Host;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.PubsubSubscription;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.pubsub.gossip.Gossip;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.peergos.HostBuilder;
import org.peergos.blockstore.RamBlockstore;
import org.peergos.protocol.dht.RamProviderStore;
import org.peergos.protocol.dht.RamRecordStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class ChatNode {
    private Topic curTopic = null;
    private PubsubSubscription subscription = null;
    private final Gossip gossip = new Gossip();
    private final Host chatHost;
    private final PubsubPublisherApi publisher;


    private final InetAddress address;
    private final int port;
    public ChatNode() {
        address = getAddress();
        port = getPort();
        HostBuilder builder = HostBuilder.create(0,
                new RamProviderStore(1000), new RamRecordStore(), new RamBlockstore(), (c, p, a) -> CompletableFuture.completedFuture(true));

        chatHost = builder
                .addProtocols(List.of(gossip))
                .listen(List.of(new MultiAddress("/ip4/" + address.getHostAddress() + "/tcp/" + port)))
                .build();


        publisher = gossip.createPublisher(chatHost.getPrivKey(), 0);

        try { chatHost.start().get(); }
        catch(Exception e) { System.out.println(e.getMessage()); }
    }

    public boolean initConnect(Multiaddr address) {
        try {
            chatHost.getNetwork().connect(address).get();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private final Consumer<MessageApi> subscriber = (data) -> {
        ByteBuf buf = data.getData();
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        try {
            MessageData messageData = (MessageData) Serializer.deserialize(bytes);
            if (messageData.message != null)
                System.out.println("Other: " + messageData.message);
            if (messageData.fileBytes != null) {
                File file = new File("../P2PChat/DownLoads/" + messageData.message);
                if (file.createNewFile())
                    System.out.println("File received!");
                try (FileOutputStream fos = new FileOutputStream("../P2PChat/DownLoads/" + messageData.message)) {
                    fos.write(messageData.fileBytes);
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    };

    public int processCommand(String command) throws ExecutionException, InterruptedException {
        String help =
                """


                        _chat_ "name" -- To join chat if exists or create your own
                        _file_  "path_to_file" -- To send file
                        "your text message" -- To send text in chat
                        _quit_ -- To quit application
                        
                        
                        """;

        if (command.startsWith("_help_")) {
            System.out.println(help);
        } else if (command.startsWith("_chat_ ")) {
            var line = command.substring(7);
            if (line.isEmpty()) return 1;
            curTopic = new Topic(line);
            if (subscription != null)
                subscription.unsubscribe();
            subscription = gossip.subscribe(subscriber, curTopic);
        } else if (command.startsWith("_file_ ")) {
            var str = command.substring(7);
            if (str.isEmpty()) return 1;
            File file = new File(str);
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] byteArray = new byte[(int) file.length()];
                if (in.read(byteArray) > 0) {
                    ByteBuf data = Unpooled.wrappedBuffer(Serializer.serialize(new MessageData(file.getName(), byteArray)));
                    publisher.publish(data, curTopic);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        else if (command.startsWith("_quit_"))
            return 1;
        else {
            if (curTopic.toString().isEmpty()) {
                System.out.println("\nYou are not in any chat now\n");
                return 1;
            }
            try {
                ByteBuf data = Unpooled.wrappedBuffer(Serializer.serialize(new MessageData(command, null)));
                publisher.publish(data, curTopic);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        return 0;
    }

    public Multiaddr getMultiAddress() {
        return new Multiaddr("/ip4/" + address.getHostAddress() + "/tcp/" + port + "/p2p/" + chatHost.getPeerId().toBase58());
    }

    public void stop() throws ExecutionException, InterruptedException {
        chatHost.stop().get();
    }

    private InetAddress getAddress() {
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
               //noinspection SequencedCollectionMethodCanBeUsed
               return addresses.get(0);
       } catch(Exception e) {
           System.out.println(e.getMessage());
       }
       return Inet4Address.getLoopbackAddress();
    }

    private int getPort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            System.out.println("Port is not available");
            return -1;
        }
    }
}



package p2pChat;

import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import io.libp2p.core.multistream.StrictProtocolBinding;
import io.libp2p.protocol.ProtocolHandler;
import io.libp2p.protocol.ProtocolMessageHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class ChatManager {
    public interface ChatController {
        void send(String msg);
    }

    private static final String protocolId = "/p2pChat/chatProtocol/1.0.0";

    public static class Chat extends StrictProtocolBinding<ChatController> {
        Chat(BiFunction<PeerId, String, Void> onMessageFunc) {
            super(protocolId, new ChatProtocol(onMessageFunc));
        }
    }
    @SuppressWarnings("NullableProblems")
    private static class ChatProtocol extends ProtocolHandler<ChatController> {
        private final BiFunction<PeerId, String, Void> onMessageFunc;
        public ChatProtocol(BiFunction<PeerId, String, Void> onMessageFunc) {
            super(Long.MAX_VALUE, Long.MAX_VALUE);
            this.onMessageFunc = onMessageFunc;
        }

        private CompletableFuture<ChatController> onStart(Stream stream) {
            CompletableFuture<Void> connection = new CompletableFuture<>();
            ChatMessageProtocol handler = new ChatMessageProtocol(onMessageFunc, connection);
            stream.pushHandler(handler);
            return connection.thenApply(v -> handler);
        }
        
        protected CompletableFuture<ChatController> onStartInitiator(Stream stream) {
            return super.onStartInitiator(stream);
        }

        @Override
        protected CompletableFuture<ChatController> onStartResponder(Stream stream) {
            return super.onStartResponder(stream);
        }
    }

    @SuppressWarnings("NullableProblems")
    private static class ChatMessageProtocol implements ProtocolMessageHandler<ByteBuf>, ChatController {
        private final BiFunction<PeerId, String, Void> onMessageFunc;
        private final CompletableFuture<Void> connection;
        private Stream stream;
        public ChatMessageProtocol(BiFunction<PeerId, String, Void> onMessageFunc, CompletableFuture<Void> connection) {
            this.onMessageFunc = onMessageFunc;
            this.connection = connection;
        }
        @Override
        public void onActivated(Stream stream)  {
            this.stream = stream;
            connection.complete(null);
        }

        @Override
        public void onMessage(Stream stream, ByteBuf msg) {
            onMessageFunc.apply(stream.remotePeerId(), msg.toString(Charset.defaultCharset()));
        }

        @Override
        public void send(String msg) {
            byte[] outData = msg.getBytes(Charset.defaultCharset());
            stream.writeAndFlush(Unpooled.wrappedBuffer(outData));
        }
    }
}

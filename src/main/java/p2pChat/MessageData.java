package p2pChat;

import java.io.Serial;
import java.io.Serializable;

public class MessageData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public String message;
    public byte[] fileBytes;

    public MessageData(String message, byte[] fileBytes) {
        this.message = message;
        this.fileBytes = fileBytes;
    }
}

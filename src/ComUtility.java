import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ComUtility {
    public static void sendError(int code, String message, SocketChannel channel) throws IOException {
        JSONObject response = new JSONObject();
        response.put("errCode", code);
        response.put("errMsg", message);
        send(response.toString(), channel);
    }

    public static void sendAck(SocketChannel channel) throws IOException {
        JSONObject response = new JSONObject();
        response.put("errCode", 0);
        response.put("errMsg", "OK");
        send(response.toString(), channel);
    }

    public static void send(String toSend, SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(toSend.getBytes().length + 4);
        // Send the buffer size
        buffer.putInt(toSend.getBytes().length);
        buffer.put(toSend.getBytes());
        buffer.flip();
        channel.write(buffer);
    }

    public static String receive(SocketChannel channel) throws IOException {
        // Allocate a buffer to read the size of the request
        ByteBuffer reader = ByteBuffer.allocate(4);

        // Read the size of the request
        if (channel.read(reader) == -1) {
            return "";
        }
        reader.flip();
        int size = reader.getInt();

        // Read the actual request
        reader = ByteBuffer.allocate(size);
        channel.read(reader);
        reader.position(0);

        StringBuilder builder = new StringBuilder();
        for (int i=0; i<size; i++) {
            builder.append((char)reader.get());
        }

        return builder.toString();
    }
}

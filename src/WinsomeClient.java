import netscape.javascript.JSObject;
import org.json.JSONObject;
import requests.SignupRequest;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

class WinsomeClient {
    public static void main(String[] args) throws IOException {
        /*TableList ta = new TableList(4, "sas", "sus", "sos", "sis").withUnicode(true);
        ta.addRow("WOW", "NICE", "69420", "POGGERS");
        ta.print();*/
        String[] tags = new String[5];
        tags[0] = "Sas";
        tags[1] = "SUS";
        JSONObject json = new JSONObject();
        json.put("username", "Fintaman");
        json.put("password", "ha detto");
        json.put("tags", tags);
        String toSend = json.toString();

        // Connect to the server, prepare the buffer
        SocketAddress address = new InetSocketAddress("localhost", 6666);
        SocketChannel clientChannel = SocketChannel.open(address);

        while (!clientChannel.finishConnect()) {}

        ByteBuffer buffer = ByteBuffer.allocate(toSend.getBytes().length + 4);
        // Send the buffer size
        buffer.putInt(toSend.getBytes().length);
        buffer.put(toSend.getBytes());
        buffer.flip();
        clientChannel.write(buffer);

        // Receive the echoed string
        buffer.flip();
        buffer.clear();
        clientChannel.read(buffer);

        // Get the error message
        int code = buffer.getInt();
        System.out.println("Error code: " + code);

        // Send the json
        buffer.put(toSend.getBytes());
        buffer.flip();
        clientChannel.write(buffer);

        System.out.println(new String(buffer.array()));
        clientChannel.close();
    }
}
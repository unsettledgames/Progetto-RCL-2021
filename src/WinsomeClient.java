import org.json.JSONObject;
import java.io.IOException;
import java.net.*;
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

        ComUtility.send(toSend, clientChannel);

        clientChannel.close();
    }
}
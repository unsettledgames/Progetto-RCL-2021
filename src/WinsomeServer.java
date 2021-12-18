import org.json.JSONObject;
import requests.LoginRequest;
import requests.Request;
import requests.SignupRequest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

class WinsomeServer {
    public static final String toAppend = " echoed by server";
    private static final int port = 6666;

    private HashMap<SelectionKey, List<JSONObject>> clientRequests;
    private HashMap<SelectionKey, List<JSONObject>> clientResponse;

    public static void main(String[] args) throws IOException {
        HashMap<SelectionKey, String> requests = new HashMap<>();
        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        InetSocketAddress address = new InetSocketAddress(port);

        server.bind(address);
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            selector.select();

            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIt = readyKeys.iterator();

            while (keyIt.hasNext()) {
                SelectionKey currKey = keyIt.next();
                keyIt.remove();

                try {
                    if (currKey.isAcceptable()) {
                        SocketChannel client = server.accept();
                        System.out.println("Accepted connection from " + client.getLocalAddress());

                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    } else if (currKey.isReadable() && currKey.isValid()) {
                        // Get the current channel
                        SocketChannel channel = (SocketChannel) currKey.channel();
                        String content = ComUtility.receive(channel);

                        if (content.equals("")) {
                            currKey.cancel();
                            System.out.println("Client disconnected");
                        }
                        else {
                            JSONObject json = new JSONObject(content);
                            System.out.println("Password: " + json.getString("password"));
                            System.out.println("Request content: " + content);
                        }

                        // Save it in the request queue along with the client who sent it
                    } else if (currKey.isWritable() && requests.get(currKey) != null) {
                        // Send the response if it's ready
                    }
                }
                catch (IOException e) {
                    currKey.cancel();
                    System.out.println("Closed connection");
                }
            }
        }
    }
}
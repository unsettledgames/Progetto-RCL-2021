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

    private HashMap<SelectionKey, List<Request>> clientRequests;
    private HashMap<SelectionKey, List<Response>> clientResponse;

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
                    } else if (currKey.isReadable()) {
                        // Get the current channel
                        SocketChannel channel = (SocketChannel) currKey.channel();
                        // Allocate a buffer to read the size of the request
                        ByteBuffer reader = ByteBuffer.allocate(4);

                        // Read the size of the request
                        channel.read(reader);
                        reader.flip();
                        int size = reader.getInt();

                        System.out.println("Content size: " + size);

                        // Send an ACK
                        reader.flip();
                        reader.putInt(0);
                        channel.write(reader);

                        // Read the actual request
                        reader = ByteBuffer.allocate(size);
                        channel.read(reader);
                        reader.position(0);
                        StringBuilder builder = new StringBuilder();
                        for (int i=0; i<size; i++) {
                            builder.append((char)reader.get());
                        }
                        System.out.println("Request content: " + builder.toString());
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
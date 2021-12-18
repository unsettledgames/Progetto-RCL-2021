import exceptions.ConfigException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;

class WinsomeServer implements Runnable {
    private int port;
    private String address;

    private Selector selector;
    private ServerSocketChannel serverSocket;

    private ExecutorService threadPool;
    private BlockingQueue<ClientRequest> clientRequests;
    private HashMap<SelectionKey, LinkedBlockingQueue<JSONObject>> clientResponses;

    public WinsomeServer() {
        clientRequests = new LinkedBlockingQueue<>();
        clientResponses = new HashMap<>();
        // TODO: politica di rifiuto custom
        threadPool = new ThreadPoolExecutor(5, 20, 1000,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    public void config(String configFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;

            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (!line.startsWith("#") && !line.equals("")) {
                    if (line.startsWith("SERVER_ADDRESS"))
                        this.address = line.split(" ")[1].strip();
                    else if (line.startsWith("TCP_PORT"))
                        this.port = Integer.parseInt(line.split(" ")[1].strip());
                    else
                        throw new ConfigException("Parametro inaspettato " + line);
                }
            }
            System.out.println("Configurazione server avvenuta con successo");
        } catch (FileNotFoundException e) {
            throw new ConfigException("Nome del file errato");
        } catch (IOException e) {
            throw new ConfigException("Errore di lettura del file");
        }
    }

    public void open() throws IOException {
        InetSocketAddress address = new InetSocketAddress(port);
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();

        serverSocket.bind(address);
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server in ascolto...");
    }

    @Override
    public void run() {
        while (true) {
            try {
                selector.select();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIt = readyKeys.iterator();

            while (keyIt.hasNext()) {
                SelectionKey currKey = keyIt.next();
                keyIt.remove();

                try {
                    if (currKey.isAcceptable()) {
                        SocketChannel client = serverSocket.accept();
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
                            boolean inserted = false;

                            // Prova a inserire nella coda finché non ci riesci
                            while (!inserted) {
                                try {
                                    clientRequests.put(new ClientRequest(currKey, json));
                                    inserted = true;
                                } catch (InterruptedException e) {}
                            }
                        }

                        // Save it in the request queue along with the client who sent it
                    } else if (currKey.isWritable()) {
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

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new ConfigException(" File non indicato");
        }
        // Crea il server
        WinsomeServer server = new WinsomeServer();

        // Configuralo e aprilo secondo i parametri del file
        server.config(args[0]);
        server.open();

        // Inizia la routine di gestione delle connessioni
        new Thread(server).start();
    }
}
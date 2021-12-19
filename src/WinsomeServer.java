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
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

class WinsomeServer implements Runnable, IRemoteServer {
    // Parametri di rete e connessione
    private int port;
    private String address;
    private String rmiHost;
    private int rmiPort;

    // Infrastruttura del server
    private Selector selector;
    private ServerSocketChannel serverSocket;
    private ExecutorService threadPool;

    // Dati del social
    private HashMap<String, User> users;

    public WinsomeServer() {
        users = new HashMap<>();
        // TODO: politica di rifiuto custom
        threadPool = new ThreadPoolExecutor(5, 20, 1000,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
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
                    else if (line.startsWith("REG_HOST"))
                        this.rmiHost = line.split(" ")[1].strip();
                    else if (line.startsWith("REG_PORT"))
                        this.rmiPort = Integer.parseInt(line.split(" ")[1].strip());
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
                        System.out.println("Accettata connessione da: " + client.getLocalAddress());

                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    }
                    else if (currKey.isReadable() && currKey.isValid()) {
                        // Get the current channel
                        SocketChannel channel = (SocketChannel) currKey.channel();
                        String content = ComUtility.receive(channel);

                        if (content.equals("")) {
                            currKey.cancel();
                            System.out.println("Client disconnesso");
                        }
                        else {
                            // Ricrea l'oggetto json
                            JSONObject json = new JSONObject(content);
                            // Avvia l'esecuzione della richiesta ricevuta
                            this.threadPool.submit(new WinsomeWorker(this, new ClientRequest(currKey, json)));
                        }
                    }
                }
                catch (IOException e) {
                    currKey.cancel();
                    System.out.println("Connessione chiusa");
                }
            }
        }
    }

    public void enableRMI() throws RemoteException {
        WinsomeServer server = this;
        IRemoteServer stub = (IRemoteServer) UnicastRemoteObject.exportObject(server, 0);

        LocateRegistry.createRegistry(rmiPort);
        Registry r = LocateRegistry.getRegistry(rmiPort);
        r.rebind("WINSOME_SIGNUP", stub);

        System.out.println("Servizio di registrazione attivo");
    }

    @Override
    public int signup(String username, String password, String[] tags) throws RemoteException {
        return 0;
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
        server.enableRMI();

        // Inizia la routine di gestione delle connessioni
        new Thread(server).start();
    }


}
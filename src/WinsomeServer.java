import com.google.gson.Gson;
import exceptions.ConfigException;
import org.json.JSONObject;

import java.io.*;
import java.nio.channels.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.net.InetSocketAddress;
import java.rmi.*;


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
    private List<SelectionKey> activeSessions;

    public WinsomeServer() {
        users = new HashMap<>();
        activeSessions = new ArrayList<>();

        // TODO: politica di rifiuto custom
        threadPool = new ThreadPoolExecutor(5, 20, 1000,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    public void addSession(SelectionKey client) {
        activeSessions.add(client);
    }
    public void endSession(SelectionKey client){ activeSessions.remove(client);}

    public boolean isInSession(SelectionKey u) {
        if (activeSessions.contains(u)) {
            return true;
        }
        return false;
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
                            endSession(currKey);
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
    public String signup(String username, String password, String[] tags) throws RemoteException {
        JSONObject ret = new JSONObject();
        if (users.containsKey(username)) {
            ret.put("errCode", -1);
            ret.put("errMsg", "Utente già esistente");
        }
        else {
            User toAdd = new User(username, password, tags);
            users.put(username, toAdd);
            ret.put("errCode", 0);
            ret.put("errMsg", "Ok");
        }

        return ret.toString();
    }

    public User getUser(String name) {
        return users.get(name);
    }
    public HashMap<String, User> getUsers() {
        return users;
    }

    public void setUsers(HashMap<String, User> users) {
        this.users = users;
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

        // Carica il server con i dati salvati in precedenza se ce ne sono
        ServerPersistence.loadServer("data.json", server);

        // Inizia la routine di gestione delle connessioni
        new Thread(server).start();
        // Inizia la routine di salvataggio dei dati
        new ServerPersistence(server, "data.json", 5000).start();
    }
}
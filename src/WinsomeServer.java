import exceptions.ConfigException;
import org.json.JSONObject;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.net.InetSocketAddress;
import java.rmi.*;


class WinsomeServer implements Runnable, IRemoteServer {
    // Parametri di rete e connessione
    // Indirizzo UDP multicast del server
    private String multicastAddress;
    // Porta TCP del server
    private int tcpPort;
    // Porta UDP del server
    private int udpPort;
    // Porta dello stub RMI del server
    private int rmiPort;
    // Host name dell'RMI
    private String rmiHostName;

    // Infrastruttura del server
    // Selector usato per il channel multiplexing
    private Selector selector;
    // Socket del server
    private ServerSocketChannel serverSocket;
    // Socket udp multicast del server
    private DatagramSocket multicastSocket;
    // ThreadPool che si occupa di gestire le richieste provenienti dai client
    private final ExecutorService threadPool;
    // Lista di stub di client da notificare riguardo nuovi follower o unfollowing
    private final HashMap<String, IRemoteClient> toNotify;

    // Dati del social
    // Sessioni attive al momento: a uno username si collega la SelectionKey del rispettivo client
    private final HashMap<String, SelectionKey> activeSessions;

    // Dati relativi agli utenti e alle relazioni tra loro
    // Utenti: a ogni username corrisponde un oggetto che rappresenta il rispettivo utente
    private ConcurrentHashMap<String, User> users;
    // Followers: a ogni username corrisponde la lista degli username degli utenti che lo seguono
    private ConcurrentHashMap<String, List<String>> followers;
    // Following: a ogni username corrisponde la lista degli username degli utenti seguiti
    private ConcurrentHashMap<String, List<String>> following;

    // Dati relativi a post, voti, commenti e rewin
    // Mette in relazione ogni username con la lista di post che ha creato
    private final ConcurrentHashMap<String, List<Post>> authorPost;
    // Posts: a ogni id di post corrisponde l'oggetto che rappresenta l'oggetto
    private ConcurrentHashMap<Long, Post> posts;
    // Voti: a ogni id di post corrisponde la lista delle valutazioni che quel post ha ricevuto
    private ConcurrentHashMap<Long, List<Vote>> votes;
    // Commenti: a ogni id di post corrisponde la lista dei commenti che quel post ha ricevuto
    private ConcurrentHashMap<Long, List<Comment>> comments;
    // Rewins: a ogni id di post originale corrisponde la lista degli id dei post di rewin di quel post originale
    private ConcurrentHashMap<Long, List<Long>> rewins;

    // Altri parametri
    // Intervallo di tempo che intercorre tra un calcolo delle ricompense e l'altro
    private long rewardRate;
    // Percentuale di ricompense assegnate all'autore di un post. Gli n curatori, a seconda di quanto hanno contribuito,
    // ricevono una frazione del valore (totalReward - authorRewardPercentage)
    private float authorRewardPercentage;
    // Intervallo di tempo che intercorre tra un salvataggio del server e l'altro
    private long autoSaveRate;

    /** Semplice costruttore che si occupa di inizializzare le strutture dati
     *
     */
    public WinsomeServer() {
        toNotify = new HashMap<>();
        activeSessions = new HashMap<>();

        users = new ConcurrentHashMap<>();
        followers = new ConcurrentHashMap<>();
        following = new ConcurrentHashMap<>();

        authorPost = new ConcurrentHashMap<>();
        posts = new ConcurrentHashMap<>();
        votes = new ConcurrentHashMap<>();
        comments = new ConcurrentHashMap<>();
        rewins = new ConcurrentHashMap<>();

        // TODO: politica di rifiuto custom
        threadPool = new ThreadPoolExecutor(5, 20, 1000,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    /** Aggiunge alla lista delle sessioni la SelectionKey specificata come parametro, assegandola allo username
     *  anch'esso specificato come argomento
     *
     * @param name Nome dell'utente loggato nella sessione
     * @param client SelectionKey relativa al client
     */
    public void addSession(String name, SelectionKey client) {
        activeSessions.put(name, client);
    }

    /** Elimina la sessione di un certo utente specificato come parametro
     *
     * @param name Nome dell'utente che ha terminato la sessione
     */
    public void endSession(String name) {
        activeSessions.remove(name);
    }

    /** Elimina la sessione di un certo utente usando la SelectionKey che gli corrisponde
     *
     * @param client SelectionKey da rimuovere
     */
    public void endSession(SelectionKey client) {
        // Cerco la SelectionKey tra i valori della mappa per trovare la chiave ed eliminare l'entry
        for (String key : activeSessions.keySet()) {
            if (activeSessions.get(key).equals(client)) {
                activeSessions.remove(key);
                return;
            }
        }
    }

    /** Indica se l'utente il cui username passato come parametro si trova all'interno di una sessione o meno
     *
     * @param username Username dell'utente
     * @return true se l'utente è coinvolto in una sessione, false altrimenti
     */
    public boolean isInSession(String username) {
        return activeSessions.containsKey(username);
    }

    /** Configura il server utilizzando un file di configurazione il cui path è passato come parametro.
     *
     * @param configFile
     */
    public void config(String configFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;

            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (!line.startsWith("#") && !line.equals("")) {
                    String address;

                    if (line.startsWith("SERVER_ADDRESS"))
                        address = line.split(" ")[1].strip();
                    else if (line.startsWith("MULTICAST_ADDRESS"))
                        this.multicastAddress = line.split(" ")[1].strip();
                    else if (line.startsWith("UDP_PORT"))
                        this.udpPort = Integer.parseInt(line.split(" ")[1].strip());
                    else if (line.startsWith("TCP_PORT"))
                        this.tcpPort = Integer.parseInt(line.split(" ")[1].strip());
                    else if (line.startsWith("REG_HOST"))
                        rmiHostName = line.split(" ")[1].strip();
                    else if (line.startsWith("REG_PORT"))
                        this.rmiPort = Integer.parseInt(line.split(" ")[1].strip());
                    else if (line.startsWith("REWARD_RATE"))
                        this.rewardRate = Long.parseLong(line.split(" ")[1].strip());
                    else if (line.startsWith("AUTOSAVE_RATE"))
                        this.autoSaveRate = Long.parseLong(line.split(" ")[1].strip());
                    else if (line.startsWith("REWARD_PERCENTAGE"))
                        this.authorRewardPercentage = Float.parseFloat(line.split(" ")[1].strip());
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
        InetSocketAddress address = new InetSocketAddress(tcpPort);
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();

        // Carica il server con i dati salvati in precedenza se ce ne sono
        ServerPersistence.loadServer("data.json", this);
        System.out.println("Caricati dati del server");

        // Inizia la routine di salvataggio dei dati
        new ServerPersistence(this, "data.json", autoSaveRate).start();
        System.out.println("Abilitato salvataggio server");

        // Inizia la routine di calcolo delle ricompense
        new ServerRewards(this, rewardRate, authorRewardPercentage).start();

        // Apri connessione multicast per notifica delle ricompense
        multicastSocket = new DatagramSocket(this.udpPort);

        // Binding indirizzo e registrazione selector
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
                    else if (currKey.isWritable() && currKey.isValid() && currKey.attachment() != null) {
                        // Send the attachment
                        ComUtility.sendAsync(currKey);
                    }
                }
                catch (IOException e) {
                    endSession(currKey);
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
        r.rebind(rmiHostName, stub);

        System.out.println("Servizio di registrazione attivo");
    }

    @Override
    public String signup(String username, String password, String[] tags) throws RemoteException {
        // TODO: espressione regolare per la validità del nome utente
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

    @Override
    public void registerNotifications(String username, IRemoteClient client) throws RemoteException {
        // Registra il client al servizio di notifiche
        toNotify.put(username, client);

        // Invia i follower correnti del client
        List<String> followers = this.followers.get(username);
        if (followers != null) {
            for (String follower : followers) {
                client.newFollower(follower, false);
            }
        }
    }
    @Override
    public void unregisterNotifications(String client) throws RemoteException {
        toNotify.remove(client);
    }
    public void notifyNewFollower(String follower, String following, boolean isNew) throws RemoteException {
        if (toNotify.get(following) != null)
            toNotify.get(following).newFollower(follower, isNew);
    }
    public void notifyUnfollow(String follower, String following) throws RemoteException {
        if (toNotify.get(following) != null)
            toNotify.get(following).unfollowed(follower);
    }

    public void notifyReward() {
        String toSend = "Calcolo delle ricompense eseguito. Controlla il portafoglio per " +
                "verificare la presenza di nuove transazioni.";
        try {
            DatagramPacket packet = new DatagramPacket(toSend.getBytes(StandardCharsets.UTF_8), toSend.length(),
                    InetAddress.getByName(multicastAddress), this.udpPort);
            try {
                multicastSocket.send(packet);
            }
            catch (IOException e) {
                System.err.println("Impossibile inviare la notifica di calcolo delle ricompense");
            }
        } catch (UnknownHostException e) {
            System.err.println("Host multicast non valido. Impossibile inviare la notifica di calcolo delle ricompense");
        }
    }

    public User getUser(String name) {
        return users.get(name);
    }
    public ConcurrentHashMap<String, User> getUsers() {
        return users;
    }
    public ConcurrentHashMap<String, List<String>> getFollowers() {return followers;}
    public ConcurrentHashMap<String, List<String>> getFollowing() {return following;}
    public ConcurrentHashMap<String, List<Post>> getAuthorPost() {return authorPost;}
    public ConcurrentHashMap<Long, List<Vote>> getVotes() {return votes;}
    public ConcurrentHashMap<Long, Post> getPosts() {return posts;}
    public ConcurrentHashMap<Long, List<Comment>> getComments() {return this.comments;}
    public ConcurrentHashMap<Long, List<Long>> getRewins() {return this.rewins;}
    public String getMulticastAddress() {return this.multicastAddress;}
    public int getMulticastPort() {return this.udpPort;}

    public void setUsers(ConcurrentHashMap<String, User> users) {
        this.users = users;
    }
    public void setFollowers(ConcurrentHashMap<String, List<String>> followers) {
        this.followers = followers;
    }
    public void setFollowing(ConcurrentHashMap<String, List<String>> following) {
        this.following = following;
    }
    public void setPosts(ConcurrentHashMap<Long, Post> posts) {
        long postId = 0;
        this.posts = posts;

        for (Post p : posts.values()) {
            this.authorPost.computeIfAbsent(p.getAuthor(), k -> new ArrayList<>());
            this.authorPost.get(p.getAuthor()).add(p);
            postId = Math.max(postId, p.getId());
        }

        Post.setMinId(postId + 1);
    }
    public void setVotes(ConcurrentHashMap<Long, List<Vote>> votes){this.votes = votes;}
    public void setComments(ConcurrentHashMap<Long, List<Comment>> comments){this.comments = comments;}
    public void setRewins(ConcurrentHashMap<Long, List<Long>> rewins) { this.rewins = rewins; }

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
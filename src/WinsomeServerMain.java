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


class WinsomeServerMain implements Runnable, IRemoteServer {
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

    // Parametri del threadpool
    // Core threads
    private int nCoreThreads;
    // Massimo numero di thread
    private int maxThreads;
    // Keepalive per ogni thread
    private long threadKeepAlive;
    // Rejection policy: numero tentativi
    private int rejectionAttempts;
    // Rejection policy: attesa tra un tentativo e l'altro
    private long rejectionWait;

    // Infrastruttura del server
    // Selector usato per il channel multiplexing
    private Selector selector;
    // Socket del server
    private ServerSocketChannel serverSocket;
    // Socket udp multicast del server
    private DatagramSocket multicastSocket;
    // ThreadPool che si occupa di gestire le richieste provenienti dai client
    private ExecutorService threadPool;
    // Lista di stub di client da notificare riguardo nuovi follower o unfollowing
    private final ConcurrentHashMap<String, IRemoteClient> toNotify;

    // Dati del social
    // Sessioni attive al momento: a uno username si collega la SelectionKey del rispettivo client
    private final ConcurrentHashMap<String, SelectionKey> activeSessions;

    // Dati relativi agli utenti e alle relazioni tra loro
    // Utenti: a ogni username corrisponde un oggetto che rappresenta il rispettivo utente
    private ConcurrentHashMap<String, User> users;
    // Followers: a ogni username corrisponde la lista degli username degli utenti che lo seguono
    private ConcurrentHashMap<String, Vector<String>> followers;
    // Following: a ogni username corrisponde la lista degli username degli utenti seguiti
    private ConcurrentHashMap<String, Vector<String>> following;

    // Dati relativi a post, voti, commenti e rewin
    // Mette in relazione ogni username con la lista di post che ha creato
    private final ConcurrentHashMap<String, Vector<Long>> authorPost;
    // Posts: a ogni id di post corrisponde l'oggetto che rappresenta l'oggetto
    private ConcurrentHashMap<Long, Post> posts;
    // Voti: a ogni id di post corrisponde la lista delle valutazioni che quel post ha ricevuto
    private ConcurrentHashMap<Long, Vector<Vote>> votes;
    // Commenti: a ogni id di post corrisponde la lista dei commenti che quel post ha ricevuto
    private ConcurrentHashMap<Long, Vector<Comment>> comments;
    // Rewins: a ogni id di post originale corrisponde la lista degli id dei post di rewin di quel post originale
    private ConcurrentHashMap<Long, Vector<Long>> rewins;

    // Threads
    // Thread gestore della persistenza
    private ServerPersistence persistenceThread;

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
    public WinsomeServerMain() {
        toNotify = new ConcurrentHashMap<>();
        activeSessions = new ConcurrentHashMap<>();

        users = new ConcurrentHashMap<>();
        followers = new ConcurrentHashMap<>();
        following = new ConcurrentHashMap<>();

        authorPost = new ConcurrentHashMap<>();
        posts = new ConcurrentHashMap<>();
        votes = new ConcurrentHashMap<>();
        comments = new ConcurrentHashMap<>();
        rewins = new ConcurrentHashMap<>();
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
     * @param configFile File contenente le opzioni di configurazione del server
     */
    public void config(String configFile, int nExpected) {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            int read = 0;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("#") && !line.equals("")) {
                    if (line.startsWith("MULTICAST_ADDRESS"))
                        this.multicastAddress = line.split(" ")[1].trim();
                    else if (line.startsWith("UDP_PORT"))
                        this.udpPort = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("TCP_PORT"))
                        this.tcpPort = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("REG_HOST"))
                        rmiHostName = line.split(" ")[1].trim();
                    else if (line.startsWith("REG_PORT"))
                        this.rmiPort = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("REWARD_RATE"))
                        this.rewardRate = Long.parseLong(line.split(" ")[1].trim());
                    else if (line.startsWith("AUTOSAVE_RATE"))
                        this.autoSaveRate = Long.parseLong(line.split(" ")[1].trim());
                    else if (line.startsWith("REWARD_PERCENTAGE"))
                        this.authorRewardPercentage = Float.parseFloat(line.split(" ")[1].trim());
                    else if (line.startsWith("THREAD_POOL_CORE"))
                        this.nCoreThreads = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("THREAD_POOL_MAX"))
                        this.maxThreads = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("THREAD_POOL_KEEPALIVE_MS"))
                        this.threadKeepAlive = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("REPEAT_POLICY_TIMES"))
                        this.rejectionAttempts = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("REPEAT_POLICY_WAIT_MS"))
                        this.rejectionWait = Long.parseLong(line.split(" ")[1].trim());
                    else
                        throw new ConfigException("Parametro inaspettato " + line);

                    read++;
                }
            }

            if (read < nExpected) {
                throw new ConfigException("Parametri di configurazione non sufficienti");
            }
            System.out.println("Configurazione server avvenuta con successo");
        } catch (FileNotFoundException e) {
            throw new ConfigException("Nome del file errato");
        } catch (IOException e) {
            throw new ConfigException("Errore di lettura del file");
        }
    }

    /** Apre tutte le connessioni necessarie (multicast per notifiche, tcp per richieste) e avvia i thread di supporto
     *  al server (salvataggio dei dati, calcolo delle ricompense)
     *
     * @throws IOException In caso di fallimento del Selector o del Socket
     */
    public void open() throws IOException {
        // Apertura del socket TCP
        InetSocketAddress address = new InetSocketAddress(tcpPort);
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();

        // Inizializzazione del threadpool
        threadPool = new ThreadPoolExecutor(nCoreThreads, maxThreads, threadKeepAlive,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new RepeatPolicy(rejectionAttempts, rejectionWait));

        // Carica il server con i dati salvati in precedenza se ce ne sono
        ServerPersistence.loadServer("data.json", this);
        System.out.println("Caricati dati del server");

        // Inizia la routine di salvataggio dei dati
        persistenceThread = new ServerPersistence(this, "data.json", autoSaveRate);
        persistenceThread.setDaemon(true);
        persistenceThread.start();
        System.out.println("Abilitato salvataggio server");

        // Inizia la routine di calcolo delle ricompense
        Thread sr = new ServerRewards(this, rewardRate, authorRewardPercentage);
        sr.setDaemon(true);
        sr.start();

        // Apri connessione multicast per notifica delle ricompense
        multicastSocket = new DatagramSocket();

        // Binding indirizzo e registrazione selector
        serverSocket.bind(address);
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server in ascolto...");
    }

    /** Routine di gestione delle richieste. I client inviano delle richieste, che il client assegna a un thread worker:
     *  quando i thread worker finiscono di elaborarle, allegano una stringa che rappresenta un oggetto JSON di risposta.
     *  Quando il client è pronto per ricevere dati, tale allegato viene spedito.
     *
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            // Select
            try {
                selector.select();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Ottenimento delle chiavi pronte
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIt = readyKeys.iterator();

            while (keyIt.hasNext()) {
                SelectionKey currKey = keyIt.next();
                keyIt.remove();

                try {
                    // Se è acceptable, stabilisco una connessione con il nuovo client e lo configuro per la lettura
                    // e per la scrittura
                    if (currKey.isAcceptable()) {
                        SocketChannel client = serverSocket.accept();
                        System.out.println("Accettata connessione da: " + client.getLocalAddress());

                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    }
                    // Se è readable e valida, leggo la richiesta e la assegno a un worker
                    else if (currKey.isReadable() && currKey.isValid()) {
                        // Ottengo il channel del client
                        SocketChannel channel = (SocketChannel) currKey.channel();
                        // Ricevo il contenuto dal channel
                        String content = ComUtility.receive(channel);

                        // In tal caso si è verificato un errore ed è meglio disconnettersi dal client per evitare
                        // problemi in futuro
                        if (content.equals("")) {
                            endSession(currKey);
                            currKey.cancel();
                            System.out.println("Client disconnesso");
                        } else {
                            // Ricrea l'oggetto json
                            JSONObject json = new JSONObject(content);
                            // Avvia l'esecuzione della richiesta ricevuta
                            this.threadPool.execute(new WinsomeWorker(this, new ClientRequest(currKey, json)));
                        }
                    }
                    // Se la chiave è writable, è valida e ha un allegato, lo spedisco
                    else if (currKey.isWritable() && currKey.isValid() && currKey.attachment() != null) {
                        // Spedisci l'attachment
                        ComUtility.sendAsync(currKey);
                    }
                }
                // In caso di eccezione, chiudo la connessione
                catch (IOException e) {
                    endSession(currKey);
                    currKey.cancel();
                    System.out.println("Connessione chiusa");
                }
            }
        }

        System.out.println("Chiusura del server avviata");
        System.exit(0);
    }

    /** Abilita lo stub RMI usato dal client per registrare nuovi utenti
     *
     * @throws RemoteException In caso di fallimento nella creazione dello stub
     */
    public void enableRMI() throws RemoteException {
        // Esportazione dello stub
        IRemoteServer stub = (IRemoteServer) UnicastRemoteObject.exportObject(this, 0);

        // Registrazione
        LocateRegistry.createRegistry(rmiPort);
        Registry r = LocateRegistry.getRegistry(rmiPort);
        r.rebind(rmiHostName, stub);

        System.out.println("Servizio di registrazione attivo");
    }

    public void configShutdown() {
        Runtime.getRuntime().addShutdownHook(
            new Thread(
                () -> {
                    System.out.println("Chiusura del server avviata");
                    // Tento di finire i task correnti
                    threadPool.shutdown();

                    // Se non ci sono riuscito in un minuto, chiudo comunque
                    try {
                        if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                            threadPool.shutdownNow();
                        }
                    }
                    catch (InterruptedException e) {
                        threadPool.shutdownNow();
                    }
                    System.out.println("Task terminati");

                    // Invio eventuali risposte e chiudo le connessioni
                    // Select
                    try {
                        selector.select();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Ottenimento delle chiavi pronte
                    Set<SelectionKey> readyKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIt = readyKeys.iterator();

                    while (keyIt.hasNext()) {
                        SelectionKey currKey = keyIt.next();
                        keyIt.remove();

                        if (currKey.isWritable() && currKey.isValid() && currKey.attachment() != null) {
                            try {
                                ComUtility.sendAsync(currKey);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            currKey.cancel();
                        }
                    }
                    System.out.println("Connessioni chiuse");

                    // Salvo lo stato del server
                    persistenceThread.saveServer();
                    System.out.println("Stato del server salvato");

                }
            )
        );
    }

    /** Implementa la procedura di registrazione per un utente
     *
     * @param username Nome utente
     * @param password Password dell'utente
     * @param tags Tag selezionati
     * @return Stringa in formato JSON contenente l'esito dell'operazione
     * @throws RemoteException In caso di errore nell'RMI
     */
    @Override
    public String signup(String username, String password, String[] tags) throws RemoteException {
        // Non registrare un nuovo utente se lo username è già preso
        JSONObject ret = new JSONObject();

        synchronized (users) {
            if (users.containsKey(username)) {
                ret.put("errCode", -1);
                ret.put("errMsg", "Utente gia' esistente");
            }
            // Verifica che lo username sia lungo da 1 a 15 caratteri, che contenga solo caratteri alfanumerici o underscores
            else if (!username.matches("^@?(\\w){1,15}$")) {
                ret.put("errCode", -2);
                ret.put("errMsg", "Username non valido: dev'essere lungo almeno 1 carattere e al massimo 15 e puo' contenere solo " +
                        "caratteri latini, underscores e numeri");
            }
            // Se tutto va bene, aggiungi un nuovo utente con le caratteristiche specificate
            else {
                User toAdd = new User(username, password, tags);
                users.put(username, toAdd);
                ret.put("errCode", 0);
                ret.put("errMsg", "Ok");
            }
        }

        // Ritorna l'esito
        return ret.toString();
    }

    /** Permette a un client di registrarsi al servizio di notifica dei nuovi followers
     *
     * @param username Utente loggato nel client che richiede la registrazione
     * @param client Client che richiede la registrazione al servizio
     * @throws RemoteException In caso di errore nell'RMI
     */
    @Override
    public void registerNotifications(String username, IRemoteClient client) throws RemoteException {
        // Registra il client al servizio di notifiche
        toNotify.put(username, client);

        // Invia i follower correnti del client
        Vector<String> followers = this.followers.get(username);
        if (followers != null) {
            for (String follower : followers) {
                client.newFollower(follower, false);
            }
        }
    }

    /** Annulla l'iscrizione di un client al servizio di notifiche
     *
     * @param client Il nome dell'utente loggato nel client che intende disiscriversi
     * @throws RemoteException In caso di errore RMI
     */
    @Override
    public void unregisterNotifications(String client) throws RemoteException {
        toNotify.remove(client);
    }

    /** Notifica al client corretto che un nuovo utente ha iniziato a seguirlo
     *
     * @param follower Nome utente del nuovo follower
     * @param following Nome dell'utente che il follower ha iniziato a seguire
     * @param isNew Indica se il follower è nuovo (cioè nel corso della sessione di following, follower ha iniziato a
     *              seguirlo) oppure se non lo è (cioè questa funzione è stata chiamata per sincronizzare lo stato del
     *              server con quello del client)
     * @throws RemoteException
     */
    public void notifyNewFollower(String follower, String following, boolean isNew) throws RemoteException {
        if (toNotify.get(following) != null)
            toNotify.get(following).newFollower(follower, isNew);
    }

    /** Notifica al client corretto che un utente ha smesso di seguirlo
     *
     * @param follower Nome dell'utente che stava seguendo l'utente da notificare
     * @param following Nome dell'utente da notificare
     * @throws RemoteException In caso di errore RMI
     */
    public void notifyUnfollow(String follower, String following) throws RemoteException {
        if (toNotify.get(following) != null)
            toNotify.get(following).unfollowed(follower);
    }

    /** Notifica a tuti i client iscritti al gruppo di multicast che è stato effettuato il calcolo delle ricompense
     *
     */
    public void notifyReward() {
        // Messaggio da inviare
        String toSend = "Calcolo delle ricompense eseguito. Controlla il portafoglio per " +
                "verificare la presenza di nuove transazioni.";
        try {
            // Preparazione del pacchetto
            DatagramPacket packet = new DatagramPacket(toSend.getBytes(StandardCharsets.UTF_8), toSend.length(),
                    InetAddress.getByName(multicastAddress), this.udpPort);
            try {
                // Invio del pacchetto
                multicastSocket.send(packet);
            }
            catch (IOException e) {
                System.err.println("Impossibile inviare la notifica di calcolo delle ricompense");
            }
        } catch (UnknownHostException e) {
            System.err.println("Host multicast non valido. Impossibile inviare la notifica di calcolo delle ricompense");
        }
    }

    /** Ritorna un utente dato il suo username
     *
     * @param name Nome dell'utente da ottenere
     * @return L'oggetto che rappresenta l'utente desiderato
     */
    public User getUser(String name) {
        return users.get(name);
    }

    // Semplici getters per gli attributi
    public ConcurrentHashMap<String, SelectionKey> getActiveSessions(){return activeSessions;}
    public ConcurrentHashMap<String, User> getUsers() {return users;}
    public ConcurrentHashMap<String, Vector<String>> getFollowers() {return followers;}
    public ConcurrentHashMap<String, Vector<String>> getFollowing() {return following;}
    public ConcurrentHashMap<String, Vector<Long>> getAuthorPost() {return authorPost;}
    public ConcurrentHashMap<Long, Vector<Vote>> getVotes() {return votes;}
    public ConcurrentHashMap<Long, Post> getPosts() {return posts;}
    public ConcurrentHashMap<Long, Vector<Comment>> getComments() {return this.comments;}
    public ConcurrentHashMap<Long, Vector<Long>> getRewins() {return this.rewins;}
    public String getMulticastAddress() {return this.multicastAddress;}
    public int getMulticastPort() {return this.udpPort;}

    // Semplici setters per gli attributi
    public void setUsers(ConcurrentHashMap<String, User> users) {this.users = users;}
    public void setFollowers(ConcurrentHashMap<String, Vector<String>> followers) {this.followers = followers;}
    public void setFollowing(ConcurrentHashMap<String, Vector<String>> following) {this.following = following;}
    public void setVotes(ConcurrentHashMap<Long, Vector<Vote>> votes){this.votes = votes;}
    public void setComments(ConcurrentHashMap<Long, Vector<Comment>> comments){this.comments = comments;}
    public void setRewins(ConcurrentHashMap<Long, Vector<Long>> rewins) { this.rewins = rewins; }

    /** Imposta la lista dei post. Essendo chiamata dal ServerPersistence per caricare il server, oltre a caricare i
     *  post, si assegnano anche i post originali agli autori (i rewin non vengono assegnati a un autore)
     *
     * @param posts I post caricati dal ServerPersistence thread
     */
    public void setPosts(ConcurrentHashMap<Long, Post> posts) {
        long postId = 0;
        this.posts = posts;

        for (Post p : posts.values()) {
            if (!p.isRewin()) {
                this.authorPost.computeIfAbsent(p.getAuthor(), k -> new Vector<>());
                this.authorPost.get(p.getAuthor()).add(p.getId());
                postId = Math.max(postId, p.getId());
            }
        }

        Post.setMinId(postId + 1);
    }


    public static void main(String[] args) {
        if (args.length < 1) {
            throw new ConfigException(" File non indicato");
        }
        // Crea il server
        WinsomeServerMain server = new WinsomeServerMain();

        // Configuralo e aprilo secondo i parametri del file
        try {
            server.config(args[0], 13);
            server.open();
            server.enableRMI();
            server.configShutdown();

            // Inizia la routine di gestione delle connessioni
            Thread serverThread = new Thread(server);
            serverThread.start();
        }
        catch (IOException e) {
            System.err.println("Errore fatale di inizializzazione, impossibile eseguire il server");
        }
        catch (ConfigException e) {
            e.printErr();
            return;
        }
    }
}
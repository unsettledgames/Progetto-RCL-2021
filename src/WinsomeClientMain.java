import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import exceptions.ConfigException;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

/** Classe utilizzata per implementare un client Winsome. Il main si occupa di ottenere i comandi dell'utente e di
 *  invocare la funzione ad essi corrispondente.
 *
 *  Ogni funzione non fa altro che inviare una richiesta al server in formato JSON e ricevere la sua risposta, stampando
 *  i dati richiesti dall'utente oppure un messaggio di errore in caso di fallimento del server o del client.
 *
 *  Prima di inviare una richiesta, si verificano le proprietà che possono essere controllate con soltanto i dati del
 *  client a disposizione, in modo da risparmiare ulteriori controlli al server. Ad esempio, la lunghezza massima del
 *  contenuto del post viene controllata lato client: in tal modo si risparmia l'invio di una richiesta che, essendo
 *  non esaudibile, caricherebbe il server di inutile lavoro.
 *
 */
class WinsomeClientMain extends RemoteObject implements IRemoteClient {
    // Dati di connessione
    // Nome host del server
    private String serverName;
    // Porta del server
    private int serverPort;

    // Dati RMI
    // Nome registro
    private String registryName;
    // Porta registro
    private int registryPort;

    // Stub del server usato per registrare nuovi utenti
    private IRemoteServer signupObject;
    // Stub del client usato dal server per essere notificato di nuovi follower
    private IRemoteClient clientStub;
    // Socket con cui si comunica con il server
    private SocketChannel socket;

    // Dati di sessione
    // Username dell'utente loggato al momento: se non è ancora loggato, contiene la stringa vuota
    private String currUsername;
    // Lista contenente i followers correnti del client. Viene assegnata per la prima volta dal server al momento del login
    // e viene poi aggiornata tramite RMI callback da parte del server
    private final List<String> followers;

    // Altri attributi
    // Thread che si occupa di visualizzare le notifiche di calcolo delle ricompense
    private RewardNotifier rewardThread;
    // Indica se le tabelle di output possono essere codificate in Unicode o meno
    private boolean tableUnicode;
    // Socket timeout
    private long socketTimeout;

    /** Costruttore del Client: inizializza la lista dei followers, crea lo stub del client per la notifica dei follower
     *  e cerca lo stub del server per la procedura di registrazione.
     *
     */
    public WinsomeClientMain() {
        super();

        followers = new ArrayList<>();
    }

    /** Callback chiamata dal server per notificare un nuovo follower
     *
     * @param follower Il nuovo follower dell'utente loggato con questo client
     * @param isNew Indica se il follower è effetivamente nuovo o se è stato aggiunto in seguito al login dell'utente:
     *              in tal caso la callback è chiamata per sincronizzare lo stato del client con quello del server
     * @throws RemoteException In caso di fallimento remoto
     */
    public void newFollower(String follower, boolean isNew) throws RemoteException {
        // Aggiungo il nuovo follower alla lista
        followers.add(follower);

        // Se il follower è effettivamente nuovo, stampo una breve notifica
        if (isNew)
            System.out.println(follower + " ha iniziato a seguirti!");
    }

    /** Callback chiamata dal server per notificare un follower che ha smesso di seguire l'utente loggato in questo client
     *
     * @param follower Ex-follower
     * @throws RemoteException In caso di fallimento remoto
     */
    public void unfollowed(String follower) throws RemoteException {
        // Rimuovi semplicemente il follower dalla lista (l'utente non viene avvisato nella stessa maniera in cui
        // social network come Twitter o Instagram non lo fanno)
        followers.remove(follower);
    }

    /** Permette di stabilire la connessione tra client e server
     *
     * @throws IOException In caso di errore di connessione
     */
    public void connect() throws IOException {
        // Creazione dell'indirizzo
        SocketAddress address = new InetSocketAddress(this.serverName, this.serverPort);
        // Apertura della connessione
        socket = SocketChannel.open(address);
        socket.socket().setSoTimeout((int)socketTimeout);

        // Resta in attesa finché la connessione non è pronta (impedisce l'invio di richieste prima che la connessione
        // sia stata stabilita)
        while (!socket.finishConnect()) {}
    }


    public void enableRMI() throws RemoteException, NotBoundException {
        Registry r = LocateRegistry.getRegistry(this.registryPort);
        Remote ro = r.lookup(this.registryName);
        // Esportazione del client per la ricezione delle notifiche
        clientStub = (IRemoteClient) UnicastRemoteObject.exportObject(this, 0);
        signupObject = (IRemoteServer) ro;
    }


    /** Configura il client usando i parametri contenuti nel file passato come argomento
     *
     * @param file Il path del file di configurazione
     * @throws IOException In caso di errore nella lettura del file
     */
    public void config(String file, int nExpected) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int read = 0;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("#") && !line.equals("")) {
                    if (line.startsWith("SERVER_ADDRESS"))
                        this.serverName = line.split(" ")[1].trim();
                    else if (line.startsWith("SERVER_PORT"))
                        this.serverPort = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("REG_HOST"))
                        this.registryName = line.split(" ")[1].trim();
                    else if (line.startsWith("REG_PORT"))
                        this.registryPort = Integer.parseInt(line.split(" ")[1].trim());
                    else if (line.startsWith("USE_UNICODE"))
                        this.tableUnicode = Boolean.parseBoolean(line.split(" ")[1].trim());
                    else if (line.startsWith("SOCKET_TIMEOUT"))
                        this.socketTimeout = Long.parseLong(line.split(" ")[1].trim());
                    else
                        throw new ConfigException("Parametro inaspettato " + line);

                    read++;
                }
            }

            if (read < nExpected) {
                throw new ConfigException("Troppi pochi parametri di configurazione");
            }
            System.out.println("Configurazione client avvenuta con successo");
        } catch (FileNotFoundException e) {
            throw new ConfigException("Nome del file errato");
        } catch (IOException e) {
            throw new ConfigException("Errore di lettura del file");
        }
    }


    /** Implementa la registrazione di un utente. Non viene portata a termine se:
     *  - Mancano dei parametri di registrazione
     *  - I tag sono più di 5
     *
     * @param comm I parametri di registrazione
     */
    public void signup(String comm) {
        if (currUsername != null) {
            System.err.println("Impossibile registrare un nuovo utente quando si e' loggati. Terminare la sessione " +
                    "corrente usando il comando logout");
            return;
        }
        // Ottenimento dei parametri di registrazione (minimo 3: nome utente, password e un tag)
        String[] args = getStringArgs(comm, 3);
        if (args != null) {
            if (args.length > 7) {
                System.err.println("Errore di registrazione: troppi argomenti (massimo 5 tag)");
            }
            else {
                try {
                    // Copia i tag in un nuovo array da spedire al server
                    String[] tags = Arrays.copyOfRange(args, 3, args.length);
                    // Hash della password con SHA256 in modo che il server non possa salvarla in chiaro
                    String password = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(args[2].getBytes(StandardCharsets.UTF_8)));

                    // Calcolo del numero di asterischi da stampare al posto della password
                    String asteriks = "*";
                    for (int i=0; i<password.length(); i++)
                        asteriks += "*";

                    // Invio la richiesta al server tramite RMI e ricevo la risposta in formato JSON
                    JSONObject reply = new JSONObject(signupObject.signup(args[1], password, tags));
                    // Creo una tabella di riepilogo della registrazione
                    TableList summary = new TableList(3, "Username", "Password", "Tags").addRow(args[1],
                            asteriks, Arrays.toString(tags));
                    summary.withUnicode(tableUnicode);
                    // Gestisco l'errore stampando la tabella in caso di successo, il messaggio di errore in caso contrario.
                    ClientError.handleError("Registrazione avvenuta con successo. Riepilogo:",
                            summary, reply.getInt("errCode"), reply.getString("errMsg"));
                }
                catch (RemoteException e) {
                    System.err.println("Errore di rete: impossibile completare la registrazione");
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            System.err.println("Errore di registrazione: troppi pochi parametri.");
        }
    }


    /** Implementa la funzione di login del client: in caso di successo, registra il client al servizio di notifica
     *  dei followers.
     *
     * @param comm Comando contenente i parametri di login del server
     */
    public void login(String comm) {
        // Ottenimento dei parametri
        String[] args = getStringArgs(comm, 2);

        if (args != null && currUsername == null) {
            // Preparazione della richiesta
            JSONObject req = new JSONObject();
            req.put("op", OpCodes.LOGIN);
            req.put("username", args[1]);
            // Hashing della password per permettere al server di poterla confrontare con l'hash fornito al momento
            // della registrazione
            try {
                req.put("password", Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(args[2].getBytes(StandardCharsets.UTF_8))));
            }
            catch (NoSuchAlgorithmException e) {
                System.err.println("Algoritmo di hashing non supportato");
                return;
            }

            try {
                // Invio della richiesta
                ComUtility.sendSync(req.toString(), socket);

                // Ricezione della risposta
                JSONObject response = new JSONObject(ComUtility.receive(socket));
                ClientError.handleError("Login avvenuto con successo. Benvenut@ " + args[1],
                        response.getInt("errCode"), response.getString("errMsg"));

                // In caso di successo, registro il client per la ricezione delle notifiche
                if (response.getInt("errCode") == 0) {
                    currUsername = args[1];

                    try {
                        // Registrazine alla callback del server per i nuovi following
                        IRemoteServer serverStub = (IRemoteServer) LocateRegistry.getRegistry(registryPort).lookup(registryName);
                        serverStub.registerNotifications(currUsername, clientStub);
                    }
                    catch (NotBoundException e) {
                        System.err.println("Registrazione al servizio di notifica fallito");
                    }

                    if (rewardThread == null) {
                        rewardThread = new RewardNotifier(response.getString("mcAddress"), response.getInt("mcPort"));
                        rewardThread.setDaemon(true);
                        rewardThread.start();
                    }
                }
            }
            catch (IOException e) {
                System.err.println("Errore di comunicazione tra client e server");
            }
        }
        else if (currUsername != null) {
            System.err.println("Errore di login: terminare la sessione corrente prima di cambiare account");
        }
        else {
            System.err.println("Errore di login: troppi pochi parametri");
        }
    }


    /** Implementa la funzione di logout e annulla la registrazione del client al servizio di notifica in caso di
     *  successo nel logout.
     *
     */
    public void logout() {
        if (currUsername != null) {
            // Preparazione della richiesta
            JSONObject req = new JSONObject();
            req.put("op", OpCodes.LOGOUT);
            req.put("user", currUsername);

            try {
                // Invio della richiesta
                ComUtility.sendSync(req.toString(), socket);

                // Ricezione della risposta e gestione dell'errore
                JSONObject reply = new JSONObject(ComUtility.receive(socket));
                ClientError.handleError("Logout eseguito correttamente",
                        reply.getInt("errCode"), reply.getString("errMsg"));

                // In caso di successo, tento di annullare l'iscrizione del client al servizio di notifica
                if (reply.getInt("errCode") == 0) {
                    try {
                        ((IRemoteServer) LocateRegistry.getRegistry(registryPort).lookup(registryName)).unregisterNotifications(currUsername);
                        // Annullo l'iscrizione al servizio di notifica del calcolo delle ricompense
                        rewardThread.close();
                        rewardThread = null;
                    }
                    catch (NotBoundException e) {
                        System.err.println("Impossibile disiscriversi dal servizio di notifica");
                    }
                    // Non c'è più nessun utente loggato su questo client
                    currUsername = null;
                }
            }
            catch (IOException e) {
                System.err.println("Errore di comunicazione tra client e server");
            }
        }
        else {
            System.err.println("Errore di logout: utente non loggato");
        }
    }


    /** Implementa i comandi di list users, list following e list followers. A seconda del secondo parametro,
     *  si comporta in maniera differente.
     *
     * @param comm Comando contenente i parametri della funzione list
     */
    public void list(String comm) {
        // Impedisci l'operazione se l'utente non è loggato
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per svolgere l'operazione.");
            return;
        }

        // Tabella contenente i dati richiesti dall'utente
        TableList output;
        // Argomenti (minimo uno, che specifica il tipo di contenuto da recuperare (users, following, followers)
        String[] args = getStringArgs(comm, 1);

        // Richiesta e risposta
        JSONObject request = new JSONObject();
        JSONObject reply;

        // In ogni caso, inserisco il nome dell'utente che ha richiesto l'operazione
        request.put("user", currUsername);

        // Se il numero di argomenti è corretto, posso proesguire
        if (args != null) {
            switch (args[1]) {
                // list users
                case "users": {
                    // Imposto l'operazione corretta
                    request.put("op", OpCodes.LIST_USERS);

                    try {
                        // Spedisco richiesta e ricevo risposta
                        ComUtility.sendSync(request.toString(), socket);
                        reply = new JSONObject(ComUtility.receive(socket));

                        // Gestione dell'errore
                        if (ClientError.handleError("Lista degli utenti con cui condividi degli interessi: ",
                                reply.getInt("errCode"), reply.getString("errMsg")) == 0) {
                            // In caso di successo, recupera la lista ritornata e stampala
                            HashMap<String, String[]> names = new Gson().fromJson(reply.getString("items"),
                                    new TypeToken<HashMap<String, String[]>>() {
                                    }.getType());
                            output = new TableList("Utente", "Interessi in comune");
                            output.withUnicode(tableUnicode);

                            for (String name : names.keySet())
                                output.addRow(name, Arrays.toString(names.get(name)));
                            output.print();
                        }
                    } catch (IOException e) {
                        System.err.println("Errore di comunicazione tra client e server");
                    }
                }
                break;
                case "followers": {
                    // Nel caso dei follower, basta stampare gli oggetti contenuti nella lista di followers
                    output = new TableList("Nome utente");
                    output.withUnicode(tableUnicode);
                    for (String follower : followers)
                        output.addRow(follower);
                    output.print();
                }
                break;
                case "following":{
                    request.put("op", OpCodes.LIST_FOLLOWING);

                    try {
                        ComUtility.sendSync(request.toString(), socket);
                        reply = new JSONObject(ComUtility.receive(socket));

                        // Stampa della lista in caso di successo della richiesta
                        if (ClientError.handleError("Lista degli utenti che segui: ",
                                reply.getInt("errCode"), reply.getString("errMsg")) == 0) {
                            List<String> names = new Gson().fromJson(reply.getString("items"),
                                    new TypeToken<List<String>>() {
                                    }.getType());
                            output = new TableList("Nome utente");
                            output.withUnicode(tableUnicode);

                            for (String name : names)
                                output.addRow(name);
                            output.print();
                        }
                    }
                    catch (IOException e) {
                        System.err.println("Errore di comunicazione tra client e server");
                    }
                }
                break;
            }
        }
        else
            System.err.println("Errore: specificare quale elenco si desidera");
    }


    /** Implementa la funzione di follow.
     *
     * @param comm Comando contenente i parametri della funzione follow
     */
    public void follow(String comm) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per svolgere l'operazione.");
            return;
        }

        String[] args = getStringArgs(comm, 1);
        if (args != null) {
            // Richiesta e risposta
            JSONObject req = new JSONObject();
            JSONObject reply;

            // Preparazione della richiesta
            req.put("user", currUsername);
            req.put("toFollow", args[1]);
            req.put("op", OpCodes.FOLLOW);

            try {
                // Ricezinoe della risposta e gestione dell'errore
                ComUtility.sendSync(req.toString(), socket);
                reply = new JSONObject(ComUtility.receive(socket));

                ClientError.handleError("Ora segui " + args[1],
                        reply.getInt("errCode"), reply.getString("errMsg"));
            }
            catch (IOException e) {
                System.err.println("Errore di comunicazione tra client e server");
            }
        }
        else {
            System.err.println("Errore: speciicare l'utente da seguire");
        }
    }


    /** Implementa la funzione di unfollowing
     *
     * @param comm Comando contenente i parametri della funzione unfollow
     */
    public void unfollow(String comm) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per svolgere l'operazione.");
            return;
        }
        String[] args = getStringArgs(comm, 1);
        if (args != null) {
            // Richiesta e risposta
            JSONObject req = new JSONObject();
            JSONObject reply;

            // Preparazione della richiesta
            req.put("user", currUsername);
            req.put("toUnfollow", args[1]);
            req.put("op", OpCodes.UNFOLLOW);

            try {
                // Invio della richiesta, ricezione della risposta e gestione dell'errore
                ComUtility.sendSync(req.toString(), socket);
                reply = new JSONObject(ComUtility.receive(socket));

                ClientError.handleError("Hai smesso di seguire " + args[1],
                        reply.getInt("errCode"), reply.getString("errMsg"));
            }
            catch (IOException e) {
                System.err.println("Errore di comunicazione tra client e server");
            }
        }
        else {
            System.err.println("Errore: speciicare l'utente da smettere di seguire");
        }
    }


    /** Implementa la creazione di un post da parte dell'utente
     *
     * @param comm Comando contenente i parametri della funzione post
     */
    public void post(String comm) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }
        // Ottieni il numero corretto di argomenti (minimo 2, titolo e contenuto del post)
        String[] args = getStringArgs(comm, 2, "\"");

        // Creazione della richiesta
        JSONObject req = new JSONObject();

        // Se i parametri erano sufficienti
        if (args != null) {
            // Ritorna se il titolo è troppo lungo
            if (args[0].length() > 20) {
                System.err.println("Errore di creazione del post: il titolo deve essere lungo meno di 20 caratteri");
                return;
            }
            // Ritorna se il contenuto è troppo lungo
            if (args[1].length() > 500) {
                System.err.println("Errore di creazione del post: il contenuto deve essere lungo meno di 500 caratteri");
                return;
            }

            // Se il post è ben formato, formatta la richiesta
            req.put("user", currUsername);
            req.put("op", OpCodes.CREATE_POST);
            req.put("postTitle", args[0]);
            req.put("postContent", args[1]);

            try {
                // Invia richiesta, ricevi risposta e gestisci l'errore
                ComUtility.sendSync(req.toString(), socket);
                JSONObject reply = new JSONObject(ComUtility.receive(socket));

                TableList output = new TableList("Titolo", "Contenuto")
                        .withUnicode(true).addRow(args[0], args[1]);
                output.withUnicode(tableUnicode);

                ClientError.handleError("Post creato con successo.", output,
                        reply.getInt("errCode"), reply.getString("errMsg"));
            }
            catch (IOException e) {
                System.err.println("Errore di comunicazione tra client e server");
            }
        }
        else {
            System.err.println("Errore di creazione del post: parametri mancanti.");
        }
    }


    /** Implementa la visualizzazione del blog dell'utente
     *
     */
    public void showBlog() {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }
        // Preparazione della tabella di risultati
        TableList out = new TableList("Id post", "Titolo", "Autore", "Rewinner");
        out.withUnicode(tableUnicode);
        JSONObject req = new JSONObject();

        // Preparazione della richiesta
        req.put("op", OpCodes.SHOW_BLOG);
        req.put("user", currUsername);

        try {
            // Invio della richiesa e ricezione della risposta
            ComUtility.sendSync(req.toString(), socket);
            JSONObject reply = new JSONObject(ComUtility.receive(socket));

            // Stampa dei post del blog
            List<Post> posts = new Gson().fromJson(reply.getString("items"), new TypeToken<List<Post>>() {
            }.getType());
            for (Post p : posts)
                out.addRow("" + p.getId(), p.getTitle(), p.getAuthor(), p.getRewinner());
            out.print();
        }
        catch (IOException e) {
            System.err.println("Errore di comunicazione tra client e server");
        }
    }


    /** Implementa la visualizzazione del blog dell'utente. In aggiunta alla specifica, si stampa anche il nome dello
     *  utente che ha rewinnato un post, in modo da distinguere tra eventuali post originali e rewin di quel post
     *
     */
    public void showFeed() {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        // Preparazione della tabella dei risultati
        TableList out = new TableList("Id post", "Titolo", "Autore", "Rewinner");
        out.withUnicode(tableUnicode);
        JSONObject req = new JSONObject();

        // Preparazione della richiesta
        req.put("op", OpCodes.SHOW_FEED);
        req.put("user", currUsername);

        try {
            ComUtility.sendSync(req.toString(), socket);
            JSONObject reply = new JSONObject(ComUtility.receive(socket));

            // Stampo i post del feed
            List<Post> posts = new Gson().fromJson(reply.getString("items"), new TypeToken<List<Post>>() {
            }.getType());
            for (Post p : posts)
                out.addRow("" + p.getId(), p.getTitle(), p.getAuthor(), p.getRewinner());
            out.print();
        }
        catch (IOException e) {
            System.err.println("Errore di comunicazione tra client e server");
        }
    }


    /** Implementa la votazione di un post
     *
     * @param command Comando contenente i parametri della funzione rate
     */
    public void rate(String command) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        // Recupero i parametri del comando (minimo 2: id del post e tipo del voto)
        String[] args = getStringArgs(command, 2);
        if (args == null) {
            System.err.println("Errore nella valutazione del post: troppi pochi argomenti.");
            return;
        }

        // Preparazione della richiesta
        JSONObject req = new JSONObject();
        req.put("op", OpCodes.RATE_POST);
        req.put("user", currUsername);
        // Siamo abbastanza tolleranti nei confronti dell'utente, invece di prendere il voto esatto e restituire errore
        // se non è esattamente 1 o -1, si prende il segno del valore
        req.put("value", Math.signum(Integer.parseInt(args[2])));
        req.put("post", Long.parseLong(args[1]));

        try {
            // Invio della richiesta, ricezione della risposta e gestione dell'errore
            ComUtility.sendSync(req.toString(), socket);

            JSONObject reply = new JSONObject(ComUtility.receive(socket));
            ClientError.handleError("Valutazione aggiunta", reply.getInt("errCode"),
                    reply.getString("errMsg"));
        }
        catch (IOException e) {
            System.err.println("Errore di comunicazione tra client e server");
        }
    }


    /** Implementa la funzione di commento
     *
     * @param command Comando contenente i parametri della funzione comment
     */
    public void comment(String command) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        // Recupero i parametri del comando
        String[] args = getStringArgs(command, 2);
        String[] commentContent = getStringArgs(command, 1, "\"");

        // Se non sono abbastanza, ritorno
        if (args == null || commentContent == null) {
            System.err.println("Errore nel commentare il post: troppi pochi parametri");
            return;
        }

        // Preparazione della richiesta
        JSONObject req = new JSONObject();
        req.put("op", OpCodes.COMMENT_POST);
        req.put("user", currUsername);
        req.put("post", Long.parseLong(args[1]));
        req.put("comment", commentContent[0]);

        try {
            // Invio della richiesta, ricezione della risposta e gestione dell'errore
            ComUtility.sendSync(req.toString(), socket);

            JSONObject reply = new JSONObject(ComUtility.receive(socket));
            ClientError.handleError("Commento aggiunto correttamente.", reply.getInt("errCode"),
                    reply.getString("errMsg"));
        }
        catch (IOException e) {
            System.err.println("Errore di comunicazione tra client e server");
        }
    }


    /** Implementa la visualizzazione di un post nel dettaglio
     *
     * @param command Comando contenente i parametri della funzione list
     */
    public void showPost(String command) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        // Recupero gli argomenti (minimo 2, ovvero la scritta post e l'id del post da vedere
        String[] args = getStringArgs(command, 2);
        if (args == null) {
            System.err.println("Errore di visualizzazione del post: troppi pochi parametri");
        }
        else {
            // Preparazione della richiesta
            JSONObject req = new JSONObject();
            TableList out = new TableList("Id post", "Titolo", "Contenuto", "Upvotes", "Downvotes").withUnicode(true);
            out.withUnicode(tableUnicode);
            req.put("op", OpCodes.SHOW_POST);
            req.put("user", currUsername);
            req.put("post", Long.parseLong(args[2]));

            try {
                ComUtility.sendSync(req.toString(), socket);
                String received = ComUtility.receive(socket);
                JSONObject reply = new JSONObject(received);

                // Se la richiesta è andata a buon fine
                if (ClientError.handleError("Dettagli post " + args[2] + ": ",
                        reply.getInt("errCode"), reply.getString("errMsg")) == 0) {

                    // Recupera la lista di commenti dal post
                    List<Comment> comments = new Gson().fromJson(reply.getString("comments"),
                            new TypeToken<List<Comment>>() {
                            }.getType());
                    // Stampa i dettagli del post
                    out.addRow(args[2], reply.getString("title"), reply.getString("content"),
                            "" + reply.getInt("nUpvotes"), "" + reply.getInt("nDownvotes"));
                    out.print();

                    // Se ci sono dei commenti, stampali
                    if (comments != null) {
                        System.out.println("Commenti: ");
                        TableList commentTable = new TableList("Autore", "Commento");
                        commentTable.withUnicode(tableUnicode);

                        for (Comment c : comments)
                            commentTable.addRow(c.getUser(), c.getContent());
                        commentTable.print();
                    }
                }
            }
            catch (IOException e) {
                System.err.println("Errore di comunicazione tra client e server");
            }
        }
    }


    /** Implementa l'eliminazione di un post
     *
     * @param command Comando contenente i parametri della funzione list
     */
    public void deletePost(String command) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        // Estrazione dei parametri (minimo 1, l'id del post da cancellare)
        String[] args = getStringArgs(command, 1);
        if (args == null) {
            System.err.println("Errore di eliminazione: id del post da cancellare mancante");
            return;
        }

        // Preparo la richiesta
        JSONObject req = new JSONObject();
        req.put("user", currUsername);
        req.put("op", OpCodes.DELETE_POST);
        req.put("post", Long.parseLong(args[1]));

        try {
            // Invio la richiesta, ricevo la risposta e gestisco l'errore
            ComUtility.sendSync(req.toString(), socket);

            JSONObject reply = new JSONObject(ComUtility.receive(socket));
            ClientError.handleError("Post eliminato", reply.getInt("errCode"), reply.getString("errMsg"));
        }
        catch (IOException e) {
            System.err.println("Errore di comunicazione tra client e server");
        }
    }


    /** Implementa il rewin di un post
     *
     * @param command Comando contenente i parametri della funzione rewin
     */
    public void rewinPost(String command) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        // Ottenimento dei parametri (minimo 1, l'id del post da rewinnare)
        String[] args = getStringArgs(command, 1);
        if (args == null) {
            System.err.println("Errore di rewin: specificare l'id del post da condividere");
            return;
        }

        // Preparazione della richiesta
        JSONObject req = new JSONObject();
        req.put("user", currUsername);
        req.put("post", args[1]);
        req.put("op", OpCodes.REWIN_POST);

        try {
            // Invio la richiesta, ricevo la risposta e gestisco l'errore
            ComUtility.sendSync(req.toString(), socket);

            JSONObject reply = new JSONObject(ComUtility.receive(socket));
            ClientError.handleError("Il post e' stato rewinnato", reply.getInt("errCode"),
                    reply.getString("errMsg"));
        }
        catch (IOException e) {
            System.err.println("Errore di comunicazione tra client e server");
        }
    }


    /** Funzione che implementa la visualizzazione del wallet, sia in Wincoins includendo le transazioni, che
     *  in Bitcoin visualizzando solo l'importo corrente.
     *
     * @param command Comando contenente i parametri della funzione wallet
     */
    public void wallet(String command) {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }
        String[] args = getStringArgs(command, 0);
        JSONObject req = new JSONObject();

        // Se ho un argomento solo (il nome del comando), stampo valore totale del portafoglio e storico delle transazioni
        if (args.length == 1) {
            // Preparazione della richiesta
            req.put("op", OpCodes.WALLET);
            req.put("user", currUsername);
            try {
                // Invio della richiesta
                ComUtility.sendSync(req.toString(), socket);
                // Ricezione della risposta
                JSONObject reply = new JSONObject(ComUtility.receive(socket));

                // Verifico la presenza di errori
                if (ClientError.handleError("", reply.getInt("errCode"),
                        reply.getString("errMsg")) == 0) {
                    // Se non si sono verificati errori, estraggo lo storico delle transazioni
                    TableList transactionOut = new TableList("Data", "Importo", "Causale");
                    transactionOut.withUnicode(tableUnicode);
                    List<Transaction> transactions = new Gson().fromJson(reply.getString("transactions"),
                            new TypeToken<List<Transaction>>() {
                            }.getType());
                    // Formattatore dei valori float
                    DecimalFormat twoDigits = new DecimalFormat("0.00");

                    // Stampa del totale
                    System.out.println("Totale nel portafoglio: " + twoDigits.format(reply.getDouble("amount")) + " wincoins");
                    // Stampa delle transazioni
                    for (Transaction t : transactions)
                        transactionOut.addRow(t.getDate(), "" + twoDigits.format(t.getAmount()), t.getCausal());
                    System.out.println("Lista delle transazioni: ");
                    transactionOut.print();
                }
            }
            catch (IOException e) {
                System.err.println("Errore di comunicazione tra client e server");
            }
        }
        // Altrimenti, se l'ultimo valore e' la stringa "btc", converto il valore del portafoglio in bitcoin
        else if (args.length > 1 && args[1].equals("btc")) {
            // Preparo la richiesta
            req.put("op", OpCodes.WALLET_BTC);
            req.put("user", currUsername);

            // Invio la richiesta, ricevo la risposta, gestisco l'errore e se non se ne sono verificati, stampo il
            // valore del portafoglio in bitcoin
            try {
                ComUtility.sendSync(req.toString(), socket);

                JSONObject reply = new JSONObject(ComUtility.receive(socket));
                System.out.println("Totale nel portafoglio in Bitcoin: " +
                        new DecimalFormat("0.00").format(reply.getDouble("btc")));
            }
            catch (IOException e) {
                System.err.println("Errore di comunicazione tra client e server");
            }
        }
        else {
            System.err.println("Errore di visualizzazione del portafoglio: valuta di conversione non supportata");
        }
    }


    public void configShutdown() {
        Runtime.getRuntime().addShutdownHook(
            new Thread(
                () -> {
                    System.out.println("Chiusura del client avviata");

                    // Mi disiscrivo da notifiche e calcolo ricompense
                    IRemoteServer serverStub = null;
                    try {
                        if (currUsername != null) {
                            serverStub = (IRemoteServer) LocateRegistry.getRegistry(6667).lookup("WINSOME_SERVER");
                            serverStub.unregisterNotifications(currUsername);
                        }
                    } catch (RemoteException e) {
                        System.err.println("Errore di comunicazione remota");
                    } catch (NotBoundException e) {
                        System.err.println("Impossibile reperire l'oggetto remoto");
                    }

                    // Interrompo il thread che notifica il calcolo delle ricompense
                    if (rewardThread != null && rewardThread.isAlive()) {
                        rewardThread.close();
                    }

                    // Chiudo la connessione
                    if (socket != null && socket.isConnected())
                        closeConnection();

                    System.out.println("Client chiuso");
                }
            )
        );
    }


    /** Permette di chiudere la connessione con il server
     *
     */
    public void closeConnection() {
        try {
            socket.close();
        }
        catch (IOException e) {
            System.err.println("Impossibile chiudere la connessione con il server");
        }
    }

    /** Data una stringa contenente comando e parametri per una certa funzione, estrae i parametri
     *
     * @param comm Comando da parsare
     * @param minExpected Numero minimo di argomenti (escluso il nome del comando) che il client si aspetta
     * @return Array di stringhe in cui la prima stringa è il nome del comando, le altre sono i parametri forniti,
     *         null se i parametri forniti sono meno del previsto
     */
    private String[] getStringArgs(String comm, int minExpected) {
        String[] ret = comm.split(" ");
        if (ret.length-1 >= minExpected)
            return ret;
        return null;
    }
    /** Data una stringa contenente comando e parametri per una certa funzione (ognuno racchiuso tra token specificati
     *  come parametro della funzione), estrae i parametri del comando
     *
     * @param comm Comando da parsare
     * @param minExpected Numero minimo di argomenti (escluso il nome del comando) che il client si aspetta
     * @return Array di stringhe in cui la prima stringa è il nome del comando, le altre sono i parametri forniti,
     *         null se i parametri forniti sono meno del previsto
     */
    private String[] getStringArgs(String comm, int minExpected, String token) {
        // Creazione della lista contenente i parametri
        List<String> args = new ArrayList<>();
        // Rimuovo il nome del comando
        if (comm.contains(token)) {
            comm = comm.substring(comm.indexOf(token) + 1);
        }
        // Estraggo ogni parametro
        while (comm.contains(token)) {
            args.add(comm.substring(0, comm.indexOf(token)));
            comm = comm.substring(min(comm.indexOf(token) + 3, comm.length()));
        }

        // Ritorno null se ho trovato meno argomenti del dovuto
        if (args.size() < minExpected)
            return null;
        // Altrimenti ritorno la conversione della lista in array
        return Arrays.copyOf(args.toArray(), args.size(), String[].class);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            new ConfigException("File non indicato").printErr();
            return;
        }

        try {
            // Crea il client e connettilo al server
            WinsomeClientMain client = new WinsomeClientMain();

            try {
                client.config(args[0], 6);
            }
            catch (ConfigException e) {
                e.printErr();
                return;
            }
            client.connect();
            client.enableRMI();
            client.configShutdown();
            // Lettore dei comandi dell'utente
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            // Ultimo comando inserito dall'utente
            String currCommand = "";

            // Finché l'utente non decide di uscire, leggi un comando alla volta ed eseguilo
            while (!currCommand.startsWith("quit")) {
                // Leggi il comando
                currCommand = reader.readLine();

                // Richiedi operazioni diverse in base al comando
                switch (currCommand.split(" ")[0]) {
                    case "register":
                        client.signup(currCommand);
                        break;
                    case "login":
                        client.login(currCommand);
                        break;
                    case "logout":
                        client.logout();
                        break;
                    case "list":
                        client.list(currCommand);
                        break;
                    case "follow":
                        client.follow(currCommand);
                        break;
                    case "unfollow":
                        client.unfollow(currCommand);
                        break;
                    case "post":
                        client.post(currCommand);
                        break;
                    case "blog":
                        client.showBlog();
                        break;
                    case "feed":
                        client.showFeed();
                        break;
                    case "rate":
                        client.rate(currCommand);
                        break;
                    case "comment":
                        client.comment(currCommand);
                        break;
                    case "show":
                        if (currCommand.split(" ")[1].equals("post"))
                            client.showPost(currCommand);
                        break;
                    case "delete":
                        client.deletePost(currCommand);
                        break;
                    case "rewin":
                        client.rewinPost(currCommand);
                        break;
                    case "wallet":
                        client.wallet(currCommand);
                        break;
                    case "quit":
                        break;
                    default:
                        System.err.println("Comando errato o non ammesso");
                        break;
                }
            }

            client.closeConnection();
        }
        catch (IOException e) {
            System.err.println("Errore fatale di I/O, chiusura del client");
            e.printStackTrace();
        } catch (NotBoundException e) {
            System.err.println("Impossibile trovare lo stub remoto del server");
            e.printStackTrace();
        }

        System.exit(0);
    }
}
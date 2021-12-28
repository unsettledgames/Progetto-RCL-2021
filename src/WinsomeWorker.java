import com.google.gson.Gson;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomeWorker implements Runnable {
    // Richiesta da risolvere
    private final ClientRequest request;
    // Server contenente i dati necessari per risolvere le richieste e a cui inoltrare eventuali risultati
    private final WinsomeServer server;
    // SelectionKey corrispondente al client che ha effettuato la richiesta
    private final SelectionKey key;

    /** Semplice costruttore di assegnazione degli attributi
     *
     * @param server Server da cui si ha ricevuto la richiesta
     * @param request Richiesta ricevuta
     */
    public WinsomeWorker(WinsomeServer server, ClientRequest request) {
        this.request = request;
        this.server = server;
        this.key = request.getKey();
    }

    /** Risolve le richieste di login
     *
     * @param user Utente che intende loggarsi
     * @param pass Hash della password inviata dall'utente
     */
    public void login(String user, String pass) {
        // Ottenimento dell'utente corretto
        User u = server.getUser(user);
        // Preparazione della risposta
        JSONObject reply = new JSONObject();

        if (u != null) {
            // Se l'utente non è già in una sessione
            if (!server.isInSession(user)) {
                // E se la password corrisponde
                if (u.getPassword().equals(pass)) {
                    // Posso dare l'ok al client
                    reply.put("errCode", 0);
                    reply.put("errMsg", "OK");
                    // E includere le informazioni per aggiungersi al gruppo multicast di ricezione delle
                    // notifiche di calcolo delle ricompense
                    reply.put("mcAddress", server.getMulticastAddress());
                    reply.put("mcPort", server.getMulticastPort());

                    // L'utente è adesso in una sessione
                    server.addSession(user, key);
                    key.attach(reply.toString());
                }
                else
                    ComUtility.attachError(-2, "Password errata", key);
            }
            else
                ComUtility.attachError(-1, "Utente gia' loggato", key);
        }
        else
            ComUtility.attachError(-3, "Utente non esistente", key);
    }

    /** Risolve le richieste di logout
     *
     */
    public void logout()  {
        // Utente che ha richiesto il logout
        String user = request.getJson().getString("user");

        // Se l'utente è all'interno di una sessione
        if (server.isInSession(user)) {
            // Posso rimuoverlo
            server.endSession(user);
            ComUtility.attachAck(key);
        }
        else
            ComUtility.attachError(-1, "Utente non loggato", key);
    }

    /** Risolve le richieste di visualizzazione degli utenti che hanno almeno un tag in comune con l'utente che ha
     *  effettuato la richiesta
     *
     */
    public void listUsers()  {
        // Utente che ha effettuato la richiesta
        String user = request.getJson().getString("user");
        // Oggetto corrispondente al suddetto utente
        User currUser = server.getUser(user);

        // Preparazione della risposta
        JSONObject json = new JSONObject();
        Gson gson = new Gson();

        // Itero lungo la lista degli utenti
        Iterator<Map.Entry<String, User>> it = server.getUsers().entrySet().iterator();
        // Creo un'hashmap per salvare, per ogni nome utente, la lista di tag in comune con esso
        HashMap<String, String[]> ret = new HashMap<>();

        while (it.hasNext()) {
            Map.Entry<String, User> item = it.next();

            // Ignoro l'utente che ha effetuato la richiesta
            if (!item.getValue().equals(currUser)) {
                // Ottengo i tag in comune
                String[] commonTags = getCommonTags(user, item.getValue().getUsername());

                // Se ce ne sono, aggiungo l'utente alla mappa di ritorno
                if (commonTags.length > 0)
                    ret.put(item.getKey(), commonTags);
            }
        }

        // Invio della risposta
        json.put("errCode", 0);
        json.put("errMsg", "OK");
        json.put("items", gson.toJson(ret));
        key.attach(json.toString());
    }


    /** Risolve la richiesta di visualizzazione della lista degli utenti seguiti dall'utente che ha effettuato
     *  la richiesta
     *
     */
    public void listFollowing() {
        // Utente che ha effettuato la richiesta
        String user = request.getJson().getString("user");

        // Preparazione della risposta
        JSONObject json = new JSONObject();
        Gson gson = new Gson();
        // Ottengo la lista dei following
        List<String> toConvert = server.getFollowing().get(user);
        if (toConvert == null)
            toConvert = new ArrayList<>();

        // Invio la risposta, convertendo la lista dei following in un array di nomi utente
        json.put("items", gson.toJson(toConvert));
        json.put("errCode", 0);
        json.put("errMsg", "OK");

        key.attach(json.toString());
    }


    /** Risolve la richiesta di follow di un utente
     *
     */
    public synchronized void follow() {
        // Risposta
        JSONObject reply = new JSONObject();
        // Utente da iniziare a seguire
        String toFollow = request.getJson().getString("toFollow");
        // Utente che ha inviato la richiesta
        String follower = request.getJson().getString("user");

        // Evito che l'utente da seguire non sia già seguito
        if (server.getFollowing().get(follower) != null && server.getFollowing().get(follower).contains(toFollow)) {
            ComUtility.attachError(-1, "Stai gia' seguendo questo utente", key);
            return;
        }
        // Verifico che l'utente da seguire esista
        if (!server.getUsers().containsKey(toFollow)) {
            ComUtility.attachError(-2, "L'utente da seguire non esiste", key);
            return;
        }
        // Evito che l'utente si segua da solo
        if (toFollow.equals(follower)) {
            ComUtility.attachError(-3, "Non puoi seguire te stess@", key);
            return;
        }
        // Ottengo i tag in comune
        String[] commonTags = getCommonTags(toFollow, follower);
        // Ed evito che un utente possa seguire un utente con uci non condivide nessun tag
        if (commonTags.length == 0) {
            ComUtility.attachError(-4, "L'utente da seguire non condivide alcun interesse con te", key);
            return;
        }

        // Altrimenti prendo la map dei followers e aggiungo il nuovo follower
        ConcurrentHashMap<String, List<String>> followers = server.getFollowers();
        if (!followers.containsKey(toFollow)) {
            followers.put(toFollow, new ArrayList<>());
        }
        followers.get(toFollow).add(follower);

        // Notifico il client dell'aggiunta di un follower
        try {
            server.notifyNewFollower(follower, toFollow, true);
        }
        catch (IOException e) {
            System.err.println("Impossibile notificare l'utente dell'aggiunta di un follower");
        }

        // Aggiungo l'utente da seguire alla lista dei following
        ConcurrentHashMap<String, List<String>> following = server.getFollowing();
        if (!following.containsKey(follower)) {
            following.put(follower, new ArrayList<>());
        }
        following.get(follower).add(toFollow);

        reply.put("errCode", 0);
        reply.put("errMsg", "OK");

        key.attach(reply.toString());
    }


    /** Risolve le richieste di unfollow
     *
     */
    public synchronized void unfollow() {
        // Risposta
        JSONObject reply = new JSONObject();
        // Utente da smettere di seguire
        String toUnfollow = request.getJson().getString("toUnfollow");
        // Utente che sta smettendo di seguire
        String follower = request.getJson().getString("user");

        // Verifico che l'utente da smettere di seguire esista
        if (!server.getUsers().containsKey(toUnfollow)) {
            ComUtility.attachError(-2, "L'utente da smettere di seguire non esiste", key);
            return;
        }
        ConcurrentHashMap<String, List<String>> followers = server.getFollowers();
        // Se l'utente non sta ancora seguendo l'utente da smettere di seguire
        if (followers.get(toUnfollow) == null || !followers.get(toUnfollow).contains(follower)) {
            // Ritorna un codice di errore
            ComUtility.attachError(-1, "Non stai ancora seguendo questo utente", key);
            return;
        }

        // Altrimenti posso continuare a impostare le relazioni di follower-following
        followers.get(toUnfollow).remove(follower);
        // Invia la notifica di unfollow ai client connessi
        try {
            server.notifyUnfollow(follower, toUnfollow);
        }
        catch (IOException e) {
            System.err.println("Errore nella notifica di unfollow");
        }

        // Aggiorna la lista dei following dell'ex-follower
        ConcurrentHashMap<String, List<String>> following = server.getFollowing();
        following.get(follower).remove(toUnfollow);

        reply.put("errCode", 0);
        reply.put("errMsg", "OK");

        key.attach(reply.toString());
    }

    /** Risolve le richieste di creazione di un nuovo post
     *
     */
    public synchronized void createPost() {
        // Ottenimento dei parametri di creazione
        JSONObject req = request.getJson();
        String user = req.getString("user");
        ConcurrentHashMap<String, List<Post>> posts = server.getAuthorPost();
        Post toAdd = new Post(req.getString("postTitle"), req.getString("postContent"), user);

        // Aggiunta del post alla lista
        posts.computeIfAbsent(user, k -> new ArrayList<>());
        posts.get(user).add(toAdd);
        server.getPosts().put(toAdd.getId(), toAdd);

        // Invio di un ack
        ComUtility.attachAck(key);
    }


    /** Risolve le richieste di visualizzazione del blog
     *
     */
    public void viewBlog() {
        // Risposta
        JSONObject reply = new JSONObject();
        // Utente che ha richiesto la visualizzazione del blog
        String user = request.getJson().getString("user");
        // Ottenimento dei post di cui user è l'autore
        List<Post> userBlog = server.getAuthorPost().get(user);

        // Ordino la lista dei post per data decrescente
        Collections.sort(userBlog);

        // Aggiungo i post alla risposta e la invio
        reply.put("items", new Gson().toJson(userBlog));
        key.attach(reply.toString());
    }


    /** Risolve le richieste di visualizzazione del feed
     *
     */
    public void viewFeed() {
        // Risposta
        JSONObject reply = new JSONObject();
        // Utente che ha inoltrato la richiesta
        String user = request.getJson().getString("user");
        // Ottenimento del feed
        List<Post> userFeed = getFeed(user);

        // Invio del feed
        reply.put("items", new Gson().toJson(userFeed));
        key.attach(reply.toString());
    }


    /** Risolve le richieste di voto di un post: se il voto riguarda un rewin, viene votato il post originale
     *
     */
    public synchronized void ratePost() {
        // Parametri della richiesta
        JSONObject req = request.getJson();
        // Utente che ha inoltrato la richiesta
        String author = req.getString("user");
        // Post da valutare
        Long post = req.getLong("post");
        // Salvo il post nel caso fosse un rewin
        Long originalPost = post;
        // Se è un rewin, allora ottengo il post originale
        if (server.getPosts().get(post).isRewin())
            post = getOriginalPost(post);

        // Prendo i voti correnti del post originale
        ConcurrentHashMap<Long, List<Vote>> votes = server.getVotes();
        List<Vote> currVotes = votes.get(post);

        // Verifico che il voto non sia già stato inserito
        if (currVotes != null) {
            for (Vote v : currVotes) {
                if (v.getUser().equals(author)) {
                    ComUtility.attachError(-1, "Errore di votazione: hai gia' votato questo post.", key);
                    return;
                }
            }
        }
        // E controllo che l'utente possa visualizzare il post che desidera votare nel proprio feed
        if (!getFeed(author).contains(server.getPosts().get(originalPost))) {
            ComUtility.attachError(-2, "Errore di votazione: non puoi votare un post che non fa " +
                    "parte del tuo feed", key);
            return;
        }
        // Infine evito che un utente si autovaluti
        if (server.getPosts().get(post).getAuthor().equals(author)) {
            ComUtility.attachError(-3, "Errore di votazione: non puoi votare un tuo post.", key);
            return;
        }

        // Se i controlli sono stati superati, aggiungo il post
        if (currVotes == null) {
            votes.put(post, new ArrayList<>());
            currVotes = votes.get(post);
        }
        Vote toAdd = new Vote(author, req.getInt("value"));
        currVotes.add(toAdd);

        ComUtility.attachAck(key);
    }


    /** Risolve le richieste di aggiunta di un commento a un post. Se l'oggetto del commento è un rewin, si appone
     *  il commento sul post originale
     *
     */
    public synchronized void addComment() {
        // Parametri di richiesta
        JSONObject req = request.getJson();
        // Utente che ha inoltrato la richiesta
        String user = req.getString("user");
        // Post da commentare
        Long post = req.getLong("post");
        // Usata
        ConcurrentHashMap<Long, List<Comment>> comments = server.getComments();
        List<Post> userFeed = getFeed(user);

        if (server.getAuthorPost().get(user) != null && server.getAuthorPost().get(user).contains(server.getPosts().get(post))) {
            ComUtility.attachError(-1, "Errore nell'aggiunta del commento: non puoi commentare i tuoi " +
                    "stessi post", key);
            return;
        }
        if (!userFeed.contains(server.getPosts().get(post))) {
            ComUtility.attachError(-2, "Errore nell'aggiunta del commento: impossibile commentare un" +
                    " post non presente all'interno del feed", key);
            return;
        }

        post = getOriginalPost(post);

        synchronized (comments) {
            comments.computeIfAbsent(post, k -> new ArrayList<>());
            comments.get(post).add(new Comment(user, req.getString("comment")));
        }

        ComUtility.attachAck(key);
    }


    /** Risolve le richieste di visualizzazione dettagliata di un post
     *
     */
    public void showPost() {
        // Parametri di richiesta
        JSONObject req = request.getJson();
        // Risposta da inviare
        JSONObject reply = new JSONObject();
        // Id del post da visualizzare
        Long post = req.getLong("post");
        // Utente che ha richiesto la visualizzazione
        String user = req.getString("user");
        // Feed dell'utente
        List<Post> feed = getFeed(user);
        // Oggetto Post da visualizzare
        Post toShow = server.getPosts().get(post);

        // Se il post esiste e (il post è nel feed o nel blog dell'utente)
        if (toShow != null && (feed.contains(toShow) || toShow.getAuthor().equals(user))) {
            // Tengo traccia dei voti
            int nNegative = 0;
            int nPositive = 0;

            // Ottengo il post originale nel caso l'id si riferisca a un suo rewin
            post = getOriginalPost(post);
            // Ottengo l'oggetto Post corrispondente
            toShow = server.getPosts().get(post);

            // Ottengo i commenti di quel post
            List<Comment> comments = server.getComments().get(post);
            // E i voti
            List<Vote> votes = server.getVotes().get(post);

            // Calcolo i voti positivi e quelli negativi
            if (votes != null) {
                for (Vote v : votes) {
                    if (v.isPositive())
                        nPositive++;
                    else
                        nNegative++;
                }
            }

            // Invio la risposta con i dettagli desiderati dal client
            reply.put("errCode", 0);
            reply.put("errMsg", "OK");
            reply.put("title", toShow.getTitle());
            reply.put("comments", new Gson().toJson(comments));
            reply.put("nUpvotes", nPositive);
            reply.put("nDownvotes", nNegative);
            reply.put("content", toShow.getContent());

            key.attach(reply.toString());
        }
        else {
            ComUtility.attachError(-1, "Errore di visualizzazione: non sei autorizzato a vedere questo post",
                    key);
        }
    }


    /** Risolve le richieste di eliminazione di un post
     *
     */
    public synchronized void deletePost() {
        // Dettagli richiesta
        JSONObject req = request.getJson();
        // Utente che ha richiesto l'eliminazione del post
        String user = req.getString("user");
        // Id del post da eliminare
        Long post = req.getLong("post");
        // Oggetto Post da eliminare
        Post toDelete = server.getPosts().get(post);

        // Verifico che il post da eliminare esista
        if (toDelete == null) {
            ComUtility.attachError(-1, "Il post da eliminare non esiste.", key);
            return;
        }
        // E che l'utente sia autorizzato a eliminare il post (cioè che lo abbia creato)
        if (!toDelete.getAuthor().equals(user) && !toDelete.getRewinner().equals(user)) {
            ComUtility.attachError(-2, "Impossibile eliminare un post di cui non si e' l'autore.",
                    key);
            return;
        }

        // Rimuovo il post dall'insieme dei post
        server.getPosts().remove(post);
        // Se non sto rimuovendo un rewin, allora cancello anche commenti e voti, oltre a rimuovere il post dalla
        // lista dei post creati dall'utente
        if (!toDelete.isRewin()) {
            server.getAuthorPost().get(user).remove(toDelete);
            // Rimuovo i voti
            server.getVotes().remove(post);
            // Rimuovo i commenti
            server.getComments().remove(post);
        }

        // Mando un ack al client
        ComUtility.attachAck(key);
    }


    /** Risolve le richieste di rewin di un post
     *
     */
    public synchronized void rewinPost() {
        // Parametri richiesta
        JSONObject req = request.getJson();
        // Utente che intende rewinnare il post
        String user = req.getString("user");
        // Id del post da rewinnare
        Long post = req.getLong("post");
        // Feed dell'utente
        List<Post> feed = getFeed(user);

        // Verifico che il post da rewinnare sia visibile dall'utente
        if (!feed.contains(server.getPosts().get(post))) {
            ComUtility.attachError(-1, "Il post da rewinnare non e' presente nel tuo feed.", key);
            return;
        }
        // Verifico che l'utente non abbia già rewinnato il post
        for (Long p : server.getRewins().keySet()) {
            for (Long p2 : server.getRewins().get(p)) {
                if (server.getPosts().get(p2).getRewinner().equals(user)) {
                    ComUtility.attachError(-2, "Hai gia' rewinnato questo post.", key);
                    return;
                }
            }
        }

        // Creo un post di rewin, basato sul post originale (quindi se sto rewinnando un rewin, non faccio altro che
        // rewinnare il post originale)
        Post toAdd = new Post(server.getPosts().get(getOriginalPost(post)), user);
        server.getRewins().computeIfAbsent(post, k -> new ArrayList<>());
        server.getRewins().get(post).add(toAdd.getId());
        server.getPosts().put(toAdd.getId(), toAdd);

        ComUtility.attachAck(key);
    }


    /** Risolve le richieste di visualizzazione del portafoglio
     *
     */
    public void wallet() {
        // Dettagli della richiesta
        JSONObject req = request.getJson();
        // Utente che ha richiesto la visualizzazione
        String user = req.getString("user");
        // Risposta da inviare
        JSONObject reply = new JSONObject();

        // Invio della risposta, allegando la lista delle transazioni effettuate e l'ammontare totale nel
        // portafoglio dell'utente
        reply.put("errCode", 0);
        reply.put("errMsg", "OK");
        reply.put("amount", server.getUser(user).getWallet());
        reply.put("transactions", new Gson().toJson(server.getUsers().get(user).getTransactions()));

        key.attach(reply.toString());
    }



    public void walletBtc() {
        JSONObject req = request.getJson();
        String user = req.getString("user");
        JSONObject reply = new JSONObject();
        double factor;

        try {
            URL url = new URL("https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new");
            InputStreamReader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8);
            StringBuilder randomNumber = new StringBuilder();

            int c;
            while ((c = reader.read()) > 0) {
                randomNumber.append((char) c);
            }

            factor = Double.parseDouble(randomNumber.toString());
        }
        catch (IOException e) {
            System.err.println("Impossibile recuperare il fattore di conversione da Wincoin a Bitcoin. Verra' restituito" +
                    " il valore del portafoglio in Wincoin");
            factor = 1;
        }

        // Invio el risultato
        reply.put("errCode", 0);
        reply.put("errMsg", "OK");
        reply.put("btc", server.getUser(user).getWallet() * factor);

        key.attach(reply.toString());
    }


    /** Funzione di utilità che, dato un utente, ritorna la lista di post che sono presenti nel suo feed. Ogni post
     *  del feed è stato creato o rewinnato da un follower dell'utente specificato come parametro
     *
     * @param user Utente di cui si desidera ottenere il feed
     * @return Lista di post che rappresenta il feed dell'utente al momento della chiamata
     */
    private List<Post> getFeed(String user) {
        List<Post> ret = new ArrayList<>();
        List<String> following = server.getFollowing().get(user);

        if (following != null) {
            for (Post p : server.getPosts().values()) {
                // Includo i rewin
                if (following.contains(p.getAuthor()) || following.contains(p.getRewinner())) {
                    ret.add(p);
                }
            }
        }

        Collections.sort(ret);

        return ret;
    }

    /** Funzione di utilità che, dato l'id di un post che si presume essere un rewin, ritorna il corrispettivo id del
     *  post originale se l'id appartiene effettivamente a un rewin, altrimenti ritorna il parametro stesso
     *
     * @param rewin Id del post che si suppone essere un rewin
     * @return L'id del post originale se rewin è un id di un post di rewin, rewin altrimenti
     */
    private synchronized Long getOriginalPost(Long rewin) {
        if (server.getPosts().get(rewin).isRewin()) {
            Iterator<Long> postsIt = server.getRewins().keys().asIterator();

            // Finché non ho trovato il post originale
            while (postsIt.hasNext()) {
                Long rewinned = postsIt.next();
                // Controllo tra i rewin e restituisco la chiave che contiene la versione rewinnata del post
                if (server.getRewins().get(rewinned).contains(rewin)) {
                    return rewinned;
                }
            }
        }
        return rewin;
    }

    /** Funzione di utilità che ritorna un array contenente i tag in comune tra due utenti
     *
     * @param userA Primo utente
     * @param userB Secondo utente
     * @return Un array di stringhe contenente i tag in comune tra il primo e il secondo utente
     */
    private String[] getCommonTags(String userA, String userB) {
        // Ottenimento dei rispettivi oggetti utente
        User a = server.getUser(userA);
        User b = server.getUser(userB);

        // Ottenimento dei tag
        String[] aTags = a.getTags();
        List<String> bTags = Arrays.asList(b.getTags());
        List<String> ret = new ArrayList<>();

        // Aggiunta dei tag se sono in comune
        for (String aTag : aTags)
            if (bTags.contains(aTag))
                ret.add(aTag);

        return Arrays.copyOf(ret.toArray(), ret.size(), String[].class);
    }


    /** Routine del worker: data la richiesta che deve risolvere, a seconda del codice operazione invoca la funzione
     *  corretta per gestirla
     *
     */
    @Override
    public void run() {
        // Ottieni richiesta e client (attraverso la selection key)
        JSONObject currRequest = request.getJson();

        try {
            // Esegui le diverse operazioni
            switch (currRequest.getInt("op")) {
                case OpCodes.LOGIN:
                    login(currRequest.getString("username"), currRequest.getString("password"));
                    break;
                case OpCodes.LOGOUT:
                    logout();
                    break;
                case OpCodes.LIST_USERS:
                    listUsers();
                    break;
                case OpCodes.LIST_FOLLOWING:
                    listFollowing();
                    break;
                case OpCodes.FOLLOW:
                    follow();
                    break;
                case OpCodes.UNFOLLOW:
                    unfollow();
                    break;
                case OpCodes.CREATE_POST:
                    createPost();
                    break;
                case OpCodes.SHOW_BLOG:
                    viewBlog();
                    break;
                case OpCodes.SHOW_FEED:
                    viewFeed();
                    break;
                case OpCodes.RATE_POST:
                    ratePost();
                    break;
                case OpCodes.COMMENT_POST:
                    addComment();
                    break;
                case OpCodes.SHOW_POST:
                    showPost();
                    break;
                case OpCodes.DELETE_POST:
                    deletePost();
                    break;
                case OpCodes.REWIN_POST:
                    rewinPost();
                    break;
                case OpCodes.WALLET:
                    wallet();
                    break;
                case OpCodes.WALLET_BTC:
                    walletBtc();
                    break;
                default:
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

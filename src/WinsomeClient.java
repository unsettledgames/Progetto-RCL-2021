import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jdk.jshell.spi.ExecutionControl;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.min;

class WinsomeClient extends RemoteObject implements IRemoteClient {
    // Dati di connessione
    private IRemoteServer signupObject;
    private IRemoteClient clientStub;
    private SocketChannel socket;

    // Dati di sessione
    private String currUsername;
    private List<String> followers;

    public WinsomeClient() throws RemoteException, NotBoundException {
        super();
        Registry r = LocateRegistry.getRegistry(6667);
        Remote ro = r.lookup("WINSOME_SERVER");
        // Esportazione del client per la ricezione delle notifiche
        clientStub = (IRemoteClient) UnicastRemoteObject.exportObject(this, 0);

        signupObject = (IRemoteServer) ro;
        followers = new ArrayList<>();
    }

    public void newFollower(String follower, boolean isNew) throws RemoteException {
        followers.add(follower);

        if (isNew)
            System.out.println(follower + " ha iniziato a seguirti!");
    }
    public void unfollowed(String follower) throws RemoteException {
        followers.remove(follower);
    }

    public void connect() throws IOException {
        // Connect to the server, prepare the buffer
        SocketAddress address = new InetSocketAddress("localhost", 6666);
        socket = SocketChannel.open(address);

        while (!socket.finishConnect()) {}
    }


    public void signup(String comm) {
        String[] args = getStringArgs(comm, 3);
        if (args != null) {
            if (args.length > 7) {
                System.err.println("Errore di registrazione: troppi argomenti (massimo 5 tag)");
            }
            else {
                try {
                    String[] tags = Arrays.copyOfRange(args, 3, args.length);
                    String password = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(args[2].getBytes(StandardCharsets.UTF_8)));

                    String asteriks = "";
                    for (int i=0; i<password.length(); i++)
                        asteriks += "*";

                    JSONObject reply = new JSONObject(signupObject.signup(args[1], password, tags));
                    TableList summary = new TableList(3, "Username", "Password", "Tags").addRow(args[1],
                            asteriks, Arrays.toString(tags)).withUnicode(true);
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


    public void login(String comm) throws NoSuchAlgorithmException, IOException, NotBoundException {
        String[] args = getStringArgs(comm, 2);
        if (args != null && currUsername == null) {
            JSONObject req = new JSONObject();
            req.put("op", OpCodes.LOGIN);
            req.put("username", args[1]);

            req.put("password", Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(args[2].getBytes(StandardCharsets.UTF_8))));
            ComUtility.sendSync(req.toString(), socket);

            JSONObject response = new JSONObject(ComUtility.receive(socket));
            ClientError.handleError("Login avvenuto con successo. Benvenut@ " + args[1],
                    response.getInt("errCode"), response.getString("errMsg"));

            if (response.getInt("errCode") == 0) {
                currUsername = args[1];

                // Registrazine alla callback del server per i nuovi following
                IRemoteServer serverStub = (IRemoteServer) LocateRegistry.getRegistry(6667).lookup("WINSOME_SERVER");
                serverStub.registerNotifications(currUsername, clientStub);
            }
        }
        else if (currUsername != null) {
            System.err.println("Errore di login: terminare la sessione corrente prima di cambiare account");
        }
        else {
            System.err.println("Errore di login: troppi pochi parametri");
        }
    }


    public void logout() throws IOException, NotBoundException {
        if (currUsername != null) {
            JSONObject req = new JSONObject();
            req.put("op", OpCodes.LOGOUT);
            req.put("user", currUsername);
            ComUtility.sendSync(req.toString(), socket);

            JSONObject reply = new JSONObject(ComUtility.receive(socket));
            ClientError.handleError("Logout eseguito correttamente",
                    reply.getInt("errCode"), reply.getString("errMsg"));
            if (reply.getInt("errCode") == 0) {
                ((IRemoteServer) LocateRegistry.getRegistry(6667).lookup("WINSOME_SERVER")).unregisterNotifications(currUsername);
                currUsername = null;
            }
        }
        else {
            System.err.println("Errore di logout: utente non loggato");
        }
    }


    public void list(String comm) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per svolgere l'operazione.");
            return;
        }

        TableList output;
        String[] args = getStringArgs(comm, 1);

        JSONObject request = new JSONObject();
        JSONObject reply;

        request.put("user", currUsername);

        if (args != null) {
            switch (args[1]) {
                case "users":
                    request.put("op", OpCodes.LIST_USERS);
                    ComUtility.sendSync(request.toString(), socket);
                    reply = new JSONObject(ComUtility.receive(socket));

                    if (ClientError.handleError("Lista degli utenti con cui condividi degli interessi: ",
                            reply.getInt("errCode"), reply.getString("errMsg")) == 0) {
                        HashMap<String, String[]> names = new Gson().fromJson(reply.getString("items"),
                                new TypeToken<HashMap<String, String[]>>(){}.getType());
                        output = new TableList("Utente", "Interessi in comune").withUnicode(true);

                        for (String name : names.keySet())
                            output.addRow(name, Arrays.toString(names.get(name)));
                        output.print();
                    }
                    else
                        return;
                    break;
                case "followers":
                    output = new TableList("Nome utente").withUnicode(true);
                    for (String follower : followers)
                        output.addRow(follower);
                    output.print();
                    break;
                case "following":
                    request.put("op", OpCodes.LIST_FOLLOWING);
                    ComUtility.sendSync(request.toString(), socket);
                    reply = new JSONObject(ComUtility.receive(socket));

                    if (ClientError.handleError("Lista degli utenti che segui: ",
                            reply.getInt("errCode"), reply.getString("errMsg")) == 0) {
                        List<String> names = new Gson().fromJson(reply.getString("items"),
                                new TypeToken<List<String>>(){}.getType());
                        output = new TableList("Nome utente").withUnicode(true);

                        for (String name : names)
                            output.addRow(name);
                        output.print();
                    }

                    break;
            }
        }
        else
            System.err.println("Errore: specificare quale elenco si desidera");
    }


    public void follow(String comm) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per svolgere l'operazione.");
            return;
        }
        String[] args = getStringArgs(comm, 1);
        if (args != null) {
            JSONObject req = new JSONObject();
            JSONObject reply;

            req.put("user", currUsername);
            req.put("toFollow", args[1]);
            req.put("op", OpCodes.FOLLOW);

            ComUtility.sendSync(req.toString(), socket);
            reply = new JSONObject(ComUtility.receive(socket));

            ClientError.handleError("Ora segui " + args[1],
                reply.getInt("errCode"), reply.getString("errMsg"));
        }
        else {
            System.err.println("Errore: speciicare l'utente da seguire");
        }
    }


    public void unfollow(String comm) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per svolgere l'operazione.");
            return;
        }
        String[] args = getStringArgs(comm, 1);
        if (args != null) {
            JSONObject req = new JSONObject();
            JSONObject reply;

            req.put("user", currUsername);
            req.put("toUnfollow", args[1]);
            req.put("op", OpCodes.UNFOLLOW);

            ComUtility.sendSync(req.toString(), socket);
            reply = new JSONObject(ComUtility.receive(socket));

            ClientError.handleError("Hai smesso di seguire " + args[1],
                    reply.getInt("errCode"), reply.getString("errMsg"));
        }
        else {
            System.err.println("Errore: speciicare l'utente da smettere di seguire");
        }
    }


    public void post(String comm) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }
        String[] args = getStringArgs(comm, 2, "\"");
        JSONObject req = new JSONObject();

        System.out.println("Args: " + Arrays.toString(args));

        if (args != null) {
            if (args[0].length() > 20) {
                System.err.println("Errore di creazione del post: il titolo deve essere lungo meno di 20 caratteri");
                return;
            }
            if (args[1].length() > 500) {
                System.err.println("Errore di creazione del post: il contenuto deve essere lungo meno di 500 caratteri");
                return;
            }

            req.put("user", currUsername);
            req.put("op", OpCodes.CREATE_POST);
            req.put("postTitle", args[0]);
            req.put("postContent", args[1]);

            ComUtility.sendSync(req.toString(), socket);
            JSONObject reply = new JSONObject(ComUtility.receive(socket));

            ClientError.handleError("Post creato con successo.", new TableList("Titolo", "Contenuto")
                            .withUnicode(true).addRow(args[0], args[1]), reply.getInt("errCode"), reply.getString("errMsg"));
        }
        else {
            System.err.println("Errore di creazione del post: parametri mancanti.");
        }
    }


    public void showBlog() throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }
        TableList out = new TableList("Id post", "Titolo", "Autore").withUnicode(true);
        JSONObject req = new JSONObject();
        req.put("op", OpCodes.SHOW_BLOG);
        req.put("user", currUsername);

        ComUtility.sendSync(req.toString(), socket);
        JSONObject reply = new JSONObject(ComUtility.receive(socket));

        List<Post> posts = new Gson().fromJson(reply.getString("items"), new TypeToken<List<Post>>(){}.getType());
        for (Post p : posts)
            out.addRow(""+p.getId(), p.getTitle(), currUsername);
        out.print();
    }


    public void showFeed() throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        TableList out = new TableList("Id post", "Titolo", "Autore").withUnicode(true);
        JSONObject req = new JSONObject();
        req.put("op", OpCodes.SHOW_FEED);
        req.put("user", currUsername);

        ComUtility.sendSync(req.toString(), socket);
        JSONObject reply = new JSONObject(ComUtility.receive(socket));

        List<Post> posts = new Gson().fromJson(reply.getString("items"), new TypeToken<List<Post>>(){}.getType());
        for (Post p : posts)
            out.addRow(""+p.getId(), p.getTitle(), p.getAuthor());
        out.print();
    }


    public void rate(String command) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        String[] args = getStringArgs(command, 2);
        if (args == null) {
            System.err.println("Errore nella valutazione del post: troppi pochi argomenti.");
            return;
        }
        JSONObject req = new JSONObject();
        req.put("op", OpCodes.RATE_POST);
        req.put("user", currUsername);
        req.put("value", Math.signum(Integer.parseInt(args[2])));
        req.put("post", Long.parseLong(args[1]));
        ComUtility.sendSync(req.toString(), socket);

        JSONObject reply = new JSONObject(ComUtility.receive(socket));
        ClientError.handleError("Valutazione aggiunta", reply.getInt("errCode"),
                reply.getString("errMsg"));
    }


    public void comment(String command) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        String[] args = getStringArgs(command, 2);
        String[] commentContent = getStringArgs(command, 1, "\"");

        if (args == null || commentContent == null) {
            System.err.println("Errore nel commentare il post: troppi pochi parametri");
            return;
        }

        JSONObject req = new JSONObject();
        req.put("op", OpCodes.COMMENT_POST);
        req.put("user", currUsername);
        req.put("post", Long.parseLong(args[1]));
        req.put("comment", commentContent[0]);
        ComUtility.sendSync(req.toString(), socket);

        JSONObject reply = new JSONObject(ComUtility.receive(socket));
        ClientError.handleError("Commento aggiunto correttamente.", reply.getInt("errCode"),
                reply.getString("errMsg"));
    }


    public void showPost(String command) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        String[] args = getStringArgs(command, 2);
        if (args == null) {
            System.err.println("Errore di visualizzazione del post: troppi pochi parametri");
        }
        else {
            JSONObject req = new JSONObject();
            TableList out = new TableList("Id post", "Titolo", "Contenuto", "Upvotes", "Downvotes").withUnicode(true);
            req.put("op", OpCodes.SHOW_POST);
            req.put("user", currUsername);
            req.put("post", Long.parseLong(args[2]));

            ComUtility.sendSync(req.toString(), socket);
            JSONObject reply = new JSONObject(ComUtility.receive(socket));

            if (ClientError.handleError("Dettagli post " + args[2] + ": ",
                    reply.getInt("errCode"), reply.getString("errMsg")) == 0) {

                List<Comment> comments = new Gson().fromJson(reply.getString("comments"),
                        new TypeToken<List<Comment>>(){}.getType());
                out.addRow(args[2], reply.getString("title"), reply.getString("content"),
                        ""+reply.getInt("nUpvotes"), ""+reply.getInt("nDownvotes"));
                out.print();

                System.out.println("Commenti: ");
                TableList commentTable = new TableList("Autore", "Commento").withUnicode(true);

                for (Comment c : comments)
                    commentTable.addRow(c.getUser(), c.getContent());
                commentTable.print();
            }
        }
    }


    public void deletePost(String command) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggat@. Esegui l'accesso per completare l'operazione.");
            return;
        }

        String[] args = getStringArgs(command, 1);
        if (args == null) {
            System.err.println("Errore di eliminazione: id del post da cancellare mancante");
            return;
        }

        JSONObject req = new JSONObject();
        req.put("user", currUsername);
        req.put("op", OpCodes.DELETE_POST);
        req.put("post", Long.parseLong(args[1]));
        ComUtility.sendSync(req.toString(), socket);

        JSONObject reply = new JSONObject(ComUtility.receive(socket));
        ClientError.handleError("Post eliminato", reply.getInt("errCode"), reply.getString("errMsg"));
    }


    public void closeConnection() throws IOException {
        socket.close();
    }

    private String[] getStringArgs(String comm, int minExpected) {
        String[] ret = comm.split(" ");
        if (ret.length-1 >= minExpected)
            return ret;
        return null;
    }
    private String[] getStringArgs(String comm, int minExpected, String token) {
        List<String> args = new ArrayList<>();
        if (comm.contains(token)) {
            comm = comm.substring(comm.indexOf(token) + 1, comm.length());
        }
        while (comm.contains(token)) {
            args.add(comm.substring(0, comm.indexOf(token)));
            comm = comm.substring(min(comm.indexOf(token) + 3, comm.length()), comm.length());
        }

        if (args.size() < minExpected)
            return null;

        return Arrays.copyOf(args.toArray(), args.size(), String[].class);
    }

    public static void main(String[] args) throws IOException, NotBoundException, NoSuchAlgorithmException {
        // Crea il client e connettilo al server
        WinsomeClient client = new WinsomeClient();
        client.connect();
        // Lettore dei comandi dell'utente
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        // Ultimo comando inserito dall'utente
        String currCommand = "";

        // Finché l'utente non decide di uscire, leggi un comando alla volta ed eseguilo
        while (!currCommand.equals("quit")) {
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
                case "quit":
                    break;
                default:
                    System.err.println("Comando errato o non ammesso");
                    break;
            }
        }

        // TODO: unbind register etc

        client.closeConnection();
    }
}
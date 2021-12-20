import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

class WinsomeClient {
    // Dati di connessione
    private IRemoteServer signupObject;
    private SocketChannel socket;

    // Dati di sessione
    private String currUsername;

    public WinsomeClient() throws RemoteException, NotBoundException {
        Registry r = LocateRegistry.getRegistry(6667);
        Remote ro = r.lookup("WINSOME_SIGNUP");
        signupObject = (IRemoteServer) ro;
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


    public void login(String comm) throws NoSuchAlgorithmException, IOException {
        String[] args = getStringArgs(comm, 2);
        if (args != null && currUsername == null) {
            JSONObject req = new JSONObject();
            req.put("op", OpCodes.LOGIN);
            req.put("username", args[1]);

            req.put("password", Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(args[2].getBytes(StandardCharsets.UTF_8))));
            ComUtility.send(req.toString(), socket);

            JSONObject response = new JSONObject(ComUtility.receive(socket));
            ClientError.handleError("Login avvenuto con successo. Benvenuto " + args[1],
                    response.getInt("errCode"), response.getString("errMsg"));
            if (response.getInt("errCode") == 0)
                currUsername = args[1];
        }
        else if (currUsername != null) {
            System.err.println("Errore di login: terminare la sessione corrente prima di cambiare account");
        }
        else {
            System.err.println("Errore di login: troppi pochi parametri");
        }
    }


    public void logout() throws IOException {
        if (currUsername != null) {
            JSONObject req = new JSONObject();
            req.put("op", OpCodes.LOGOUT);
            req.put("user", currUsername);
            ComUtility.send(req.toString(), socket);

            JSONObject reply = new JSONObject(ComUtility.receive(socket));
            ClientError.handleError("Logout eseguito correttamente",
                    reply.getInt("errCode"), reply.getString("errMsg"));
            if (reply.getInt("errCode") == 0)
                currUsername = null;
        }
        else {
            System.err.println("Errore di logout: utente non loggato");
        }
    }


    public void list(String comm) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggato. Esegui l'accesso per svolgere l'operazione.");
            return;
        }

        TableList output;
        String[] args = getStringArgs(comm, 1);

        JSONObject request = new JSONObject();
        JSONObject reply;
        Gson gson = new Gson();

        request.put("user", currUsername);

        if (args != null) {
            switch (args[1]) {
                case "users":
                    request.put("op", OpCodes.LIST_USERS);
                    ComUtility.send(request.toString(), socket);
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
                    break;
                case "following":
                    break;
            }
        }
        else
            System.err.println("Errore: specificare quale elenco si desidera");
    }


    public void follow(String comm) throws IOException {
        if (currUsername == null) {
            System.err.println("Non sei loggato. Esegui l'accesso per svolgere l'operazione.");
            return;
        }
        String[] args = getStringArgs(comm, 1);
        if (args != null) {
            JSONObject req = new JSONObject();
            JSONObject reply;

            req.put("user", currUsername);
            req.put("toFollow", args[1]);
            req.put("op", OpCodes.FOLLOW);

            ComUtility.send(req.toString(), socket);
            reply = new JSONObject(ComUtility.receive(socket));

            ClientError.handleError("Ora segui " + args[1],
                reply.getInt("errCode"), reply.getString("errMsg"));
        }
        else {
            System.err.println("Errore: speciicare l'utente da seguire");
        }
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
            }
        }

        client.closeConnection();
    }
}
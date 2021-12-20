import com.google.gson.Gson;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.sql.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomeWorker implements Runnable {
    private ClientRequest request;
    private WinsomeServer server;
    private SocketChannel socket;

    public WinsomeWorker(WinsomeServer server, ClientRequest request) {
        this.request = request;
        this.server = server;
        this.socket = (SocketChannel) request.getKey().channel();
    }

    public void login(String user, String pass) throws IOException {
        User u = server.getUser(user);

        if (u != null) {
            if (!server.isInSession(user)) {
                if (u.getPassword().equals(pass)) {
                    server.addSession(user, request.getKey());
                    ComUtility.sendAck(socket);
                }
                else
                    ComUtility.sendError(-2, "Password errata", socket);
            }
            else
                ComUtility.sendError(-1, "Utente già loggato", socket);
        }
        else
            ComUtility.sendError(-3, "Utente non esistente", socket);
    }

    public void logout() throws IOException {
        String user = request.getJson().getString("user");

        if (server.isInSession(user)) {
            server.endSession(user);
            ComUtility.sendAck(socket);
        }
        else
            ComUtility.sendError(-1, "Utente non loggato", socket);
    }


    public void listUsers() throws IOException {
        String user = request.getJson().getString("user");
        User currUser = server.getUser(user);
        String[] userTags = currUser.getTags();

        JSONObject json = new JSONObject();
        Gson gson = new Gson();

        Iterator<Map.Entry<String, User>> it = server.getUsers().entrySet().iterator();
        HashMap<String, String[]> ret = new HashMap<>();

        while (it.hasNext()) {
            Map.Entry<String, User> item = it.next();

            if (!item.getValue().equals(currUser)) {
                List<String> currTags = Arrays.asList(item.getValue().getTags());

                List<String> commonTags = new ArrayList<>();

                for (int i = 0; i < userTags.length; i++)
                    if (currTags.contains(userTags[i]))
                        commonTags.add(userTags[i]);

                if (commonTags.size() > 0)
                    ret.put(item.getKey(), Arrays.copyOf(commonTags.toArray(), commonTags.size(), String[].class));
            }
        }

        json.put("errCode", 0);
        json.put("errMsg", "OK");
        json.put("items", gson.toJson(ret));
        ComUtility.send(json.toString(), socket);
    }


    public synchronized void follow() throws IOException {
        JSONObject reply = new JSONObject();
        String toFollow = request.getJson().getString("toFollow");
        String follower = request.getJson().getString("user");

        if (!server.getUsers().containsKey(toFollow)) {
            reply.put("errCode", -2);
            reply.put("errMsg", "L'utente da seguire non esiste");
            ComUtility.send(reply.toString(), socket);
        }
        else {
            ConcurrentHashMap<String, List<String>> followers = server.getFollowers();
            if (!followers.containsKey(toFollow)) {
                followers.put(toFollow, new ArrayList<>());
            }
            // Se la condizione è verificata, il client sta già seguendo l'utente
            if (followers.get(toFollow).contains(follower)) {
                reply.put("errCode", -1);
                reply.put("errMsg", "Stai gia' seguendo questo utente");
                ComUtility.send(reply.toString(), socket);
                return;
            }
            // Altrimenti posso continuare a impostare le relazioni di follower-following
            followers.get(toFollow).add(follower);

            // TODO: notify clients

            ConcurrentHashMap<String, List<String>> following = server.getFollowing();
            if (!following.containsKey(follower)) {
                following.put(follower, new ArrayList<>());
            }
            following.get(follower).add(toFollow);

            reply.put("errCode", 0);
            reply.put("errMsg", "OK");

            ComUtility.send(reply.toString(), socket);
        }
    }


    public synchronized void unfollow() throws IOException {
        JSONObject reply = new JSONObject();
        String toUnfollow = request.getJson().getString("toUnfollow");
        String follower = request.getJson().getString("user");

        if (!server.getUsers().containsKey(toUnfollow)) {
            reply.put("errCode", -2);
            reply.put("errMsg", "L'utente da smettere di seguire non esiste");
            ComUtility.send(reply.toString(), socket);
        }
        else {
            ConcurrentHashMap<String, List<String>> followers = server.getFollowers();
            // Se l'utente non sta ancora seguendo l'utente da smettere di seguire
            if (followers.get(toUnfollow) == null || !followers.get(toUnfollow).contains(follower)) {
                // Ritorna un codice di errore
                reply.put("errCode", -1);
                reply.put("errMsg", "Non stai ancra seguendo questo utente");
                ComUtility.send(reply.toString(), socket);
                return;
            }

            // Altrimenti posso continuare a impostare le relazioni di follower-following
            followers.get(toUnfollow).remove(follower);

            // TODO: notify clients

            ConcurrentHashMap<String, List<String>> following = server.getFollowing();
            following.get(follower).remove(toUnfollow);

            reply.put("errCode", 0);
            reply.put("errMsg", "OK");

            ComUtility.send(reply.toString(), socket);
        }
    }


    @Override
    public void run() {
        // Ottieni richiesta e client (attraverso la selection key)
        JSONObject currRequest = request.getJson();

        try {
            // Esegui le diverse operazioni
            switch (currRequest.getInt("op")) {
                case OpCodes.LOGIN:
                    if (currRequest.has("username") && currRequest.has("password")) {
                        login(currRequest.getString("username"), currRequest.getString("password"));
                    } else {
                        ComUtility.sendError(-1, "Transmission error", socket);
                    }
                    break;
                case OpCodes.LOGOUT:
                    logout();
                    break;
                case OpCodes.LIST_USERS:
                    listUsers();
                    break;
                case OpCodes.FOLLOW:
                    follow();
                    break;
                case OpCodes.UNFOLLOW:
                    unfollow();
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

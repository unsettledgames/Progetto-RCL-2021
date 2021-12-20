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

    public WinsomeWorker(WinsomeServer server, ClientRequest request) {
        this.request = request;
        this.server = server;
    }

    public void login(String user, String pass) throws IOException {
        User u = server.getUser(user);

        if (u != null) {
            if (!server.isInSession(user)) {
                if (u.getPassword().equals(pass)) {
                    server.addSession(user, request.getKey());
                    ComUtility.sendAck((SocketChannel) request.getKey().channel());
                }
                else
                    ComUtility.sendError(-2, "Password errata", (SocketChannel)request.getKey().channel());
            }
            else
                ComUtility.sendError(-1, "Utente già loggato", (SocketChannel)request.getKey().channel());
        }
        else
            ComUtility.sendError(-3, "Utente non esistente", (SocketChannel)request.getKey().channel());
    }

    public void logout() throws IOException {
        String user = request.getJson().getString("user");

        if (server.isInSession(user)) {
            server.endSession(user);
            ComUtility.sendAck((SocketChannel) request.getKey().channel());
        }
        else
            ComUtility.sendError(-1, "Utente non loggato", (SocketChannel) request.getKey().channel());
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
        ComUtility.send(json.toString(), (SocketChannel) request.getKey().channel());
    }


    @Override
    public void run() {
        // Ottieni richiesta e client (attraverso la selection key)
        JSONObject currRequest = request.getJson();
        SelectionKey client = request.getKey();

        try {
            // Esegui le diverse operazioni
            switch (currRequest.getInt("op")) {
                case OpCodes.LOGIN:
                    if (currRequest.has("username") && currRequest.has("password")) {
                        login(currRequest.getString("username"), currRequest.getString("password"));
                    } else {
                        ComUtility.sendError(-1, "Transmission error", (SocketChannel) client.channel());
                    }
                    break;
                case OpCodes.LOGOUT:
                    logout();
                    break;
                case OpCodes.LIST_USERS:
                    listUsers();
                default:
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

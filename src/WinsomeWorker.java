import org.json.JSONObject;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class WinsomeWorker implements Runnable {
    private ClientRequest request;
    private WinsomeServer server;

    public WinsomeWorker(WinsomeServer server, ClientRequest request) {
        this.request = request;
        this.server = server;
    }

    public void login(String user, String pass) throws IOException {
        JSONObject response = new JSONObject();

        User u = server.getUser(user);

        if (u != null) {
            if (!server.isInSession(request.getKey())) {
                if (u.getPassword().equals(pass)) {
                    response.put("errCode", 0);
                    ComUtility.send(response.toString(), (SocketChannel) request.getKey().channel());
                }
                else {
                    ComUtility.sendError(-2, "Password errata", (SocketChannel)request.getKey().channel());
                }
            }
            else {
                ComUtility.sendError(-1, "Utente già loggato", (SocketChannel)request.getKey().channel());
            }
        }
        else {
            ComUtility.sendError(-3, "Utente non esistente", (SocketChannel)request.getKey().channel());
        }

    }

    @Override
    public void run() {
        // Ottieni richiesta e client (attraverso la selection key)
        JSONObject currRequest = request.getJson();
        SelectionKey client = request.getKey();

        try {
            // Esegui le diverse operazioni
            switch (currRequest.getInt("op")) {
                case 1:
                    if (currRequest.has("username") && currRequest.has("password")) {
                        login(currRequest.getString("username"), currRequest.getString("password"));
                    } else {
                        ComUtility.sendError(-1, "Transmission error", (SocketChannel) client.channel());
                    }
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

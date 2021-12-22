import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPersistence extends Thread {
    private long updateRateMillis;
    private String fileName;
    private WinsomeServer server;

    public ServerPersistence(WinsomeServer toSave, String fileName, long updateRateMillis) {
        this.updateRateMillis = updateRateMillis;
        this.server = toSave;
        this.fileName = fileName;
    }

    public static void loadServer(String fileName, WinsomeServer toLoad) throws IOException {
        try {
            JSONObject json = new JSONObject(Files.readString(Paths.get(fileName)));
            Gson gson = new Gson();

            // Carica utenti
            Type type = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType();
            if (json.has("users"))
                toLoad.setUsers(gson.fromJson(json.getString("users"), type));

            // Carica following e followers
            type = new TypeToken<ConcurrentHashMap<String, List<String>>>(){}.getType();
            if (json.has("following"))
                toLoad.setFollowing(gson.fromJson(json.getString("following"), type));
            if (json.has("followers"))
                toLoad.setFollowers(gson.fromJson(json.getString("followers"), type));

            // Carica posts
            type = new TypeToken<ConcurrentHashMap<String, List<Post>>>(){}.getType();
            if (json.has("posts"))
                toLoad.setPosts(gson.fromJson(json.getString("posts"), type));
        }
        catch (NoSuchFileException e) {
            System.err.println("File di persistenza non trovato, il server verrà caricato senza dati precedenti.");
        }
        catch (JSONException e) {
            System.err.println("File di persistenza corrotto, il server verrà caricato con informazioni parziali.");
        }
    }

    public void run() {
        while (true) {
            // Prepara utility per json
            JSONObject json = new JSONObject();
            Gson gson = new Gson();

            // Serializza oggetti principali
            json.put("users", gson.toJson(server.getUsers()));
            json.put("followers", gson.toJson(server.getFollowers()));
            json.put("following", gson.toJson(server.getFollowing()));
            json.put("posts", gson.toJson(server.getPosts()));

            // Salva su file
            try (FileWriter writer = new FileWriter(fileName)){
                writer.write(json.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Aspetta prima di salvare di nuovo
            try {
                sleep(updateRateMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

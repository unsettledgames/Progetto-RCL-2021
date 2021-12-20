import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashMap;

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
            Type type = new TypeToken<HashMap<String, User>>(){}.getType();
            toLoad.setUsers(gson.fromJson(json.getString("users"), type));
        }
        catch (NoSuchFileException e) {
            System.out.println("File di persistenza non trovato, il server verrà caricato senza dati precedenti.");
        }
    }

    public void run() {
        while (true) {
            // Prepara utility per json
            JSONObject json = new JSONObject();
            Gson gson = new Gson();

            // Serializza oggetti principali
            json.put("users", gson.toJson(server.getUsers()));

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

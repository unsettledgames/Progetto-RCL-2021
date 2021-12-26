import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** ServerPersistence è la classe, pensata per essere usata come thread indipendente, che si occupa di salvare lo stato
 *  del server a intervalli di tempo regolari. La classe mette inoltre a disposizione un metodo statico usato per
 *  caricare lo stato del server da file in formato json (generato precedentemente dalla stessa ServerPersistence).
 */
public class ServerPersistence extends Thread {
    // Intervallo di salvataggio del server
    private final long updateRateMillis;
    // Nome del file di salvataggio
    private final String fileName;
    // Server da salvare
    private final WinsomeServer server;

    /** Semplice costruttore in cui si assegnano gli attributi necessari
     *
     * @param toSave Server da salvare
     * @param fileName Nome del file di salvataggio
     * @param updateRateMillis Intervallo di salvataggio
     */
    public ServerPersistence(WinsomeServer toSave, String fileName, long updateRateMillis) {
        this.updateRateMillis = updateRateMillis;
        this.server = toSave;
        this.fileName = fileName;
    }

    /** Carica il server passato come parametro
     *
     * @param fileName Nome del file conenente i dati del server
     * @param toLoad Nome del server i cui dati devono essere recuperati
     */
    public static void loadServer(String fileName, WinsomeServer toLoad) {
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
            type = new TypeToken<ConcurrentHashMap<Long, Post>>(){}.getType();
            if (json.has("posts"))
                toLoad.setPosts(gson.fromJson(json.getString("posts"), type));
            // Carica voti
            type = new TypeToken<ConcurrentHashMap<Long, List<Vote>>>(){}.getType();
            if (json.has("votes"))
                toLoad.setVotes(gson.fromJson(json.getString("votes"), type));
            // Carica commenti
            type = new TypeToken<ConcurrentHashMap<Long, List<Comment>>>(){}.getType();
            if (json.has("comments"))
                toLoad.setComments(gson.fromJson(json.getString("comments"), type));
            // Carica rewins
            type = new TypeToken<ConcurrentHashMap<Long, List<Long>>>(){}.getType();
            if (json.has("rewins"))
                toLoad.setRewins(gson.fromJson(json.getString("rewins"), type));
        }
        catch (IOException e) {
            System.err.println("Errore di lettura del file di persistenza, il server verrà caricato senza dati precedenti");
        } catch (JSONException e) {
            System.err.println("File di persistenza corrotto, il server verrà caricato con informazioni parziali.");
        }
    }

    /** Ciclo di salvataggio dei dati: il server, a ogni iterazione, salva il contenuto del server su file e aspetta
     *  un certo periodo di tempo specificato al momento della creazione del thread.
     *
     */
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
            json.put("votes", gson.toJson(server.getVotes()));
            json.put("comments", gson.toJson(server.getComments()));
            json.put("rewins", gson.toJson(server.getRewins()));

            // Salva su file
            try (FileWriter writer = new FileWriter(fileName)){
                writer.write(json.toString(4));
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

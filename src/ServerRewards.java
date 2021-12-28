import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ServerRewards extends Thread{
    // Server contenente i dati necessari per calcolare le ricompense
    private final WinsomeServer server;
    // Intervallo di tempo da aspettare per il prossimo calcolo delle ricompense
    private final long rewardRateMillis;
    // Percentuale della ricompensa assegnata all'autore del post
    private final double authorPercentage;
    // Timestamp che indica l'ultimo momento in cui sono state calcolate le ricompense
    private Timestamp lastComputing;


    /** Semplice costruttore in cui si assegnano i valori agli attributi
     *
     * @param server Server contenente i dati per il calcolo
     * @param rewardRateMillis Interavallo di calcolo delle ricompense
     * @param authorPercentage Percentuale di ricompensa che spetta all'autore di un post
     */
    public ServerRewards(WinsomeServer server, long rewardRateMillis, double authorPercentage) {
        this.authorPercentage = authorPercentage;
        this.server = server;
        this.rewardRateMillis = rewardRateMillis;
        this.lastComputing = new Timestamp(0);
    }

    /** Routine di calcolo: a ogni iterazione, il server aspetta un certo periodo di tempo e procede poi a calcolare
     *  le ricompense del social.
     *
     */
    public void run() {
        while (true) {
            try {
                sleep(rewardRateMillis);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            calculateRewards();
        }
    }

    /** Funzione che calcola le ricompense per ogni post presente all'interno del server.
     *  I rewin non contano nel calcolo, mentre i commenti e i like ai rewin vengono assegnati al post originale, dal
     *  momento che un rewin non è altro che un modo per dare più visibilità a un post. Inoltre, i voti negativi vengono
     *  ignorati.
     *
     *  Per ogni post, si calcolano le ricompense totali e si ripartiscono le percentuali. A questo punto, le ricompense
     *  vengono assegnate: nel caso dei curatori si distingue tra ricompense dovute a rating e ricompense causate dalla
     *  aggiunta di commenti.
     *
     */
    private synchronized void calculateRewards() {
        // Rating totale del post (comprende anche i voti negativi)
        int postRating = 0;
        // Numero totale di commenti per post
        int totComments = 0;
        int totPositiveRatings = 0;

        // Mappa che collega gli utenti al numero di commenti che hanno effettuato, utile per il calcolo delle
        // ricompense dovute ai commenti
        HashMap<String, Integer> comments = new HashMap<>();
        // Mappa che collega gli utenti al numero di voti positivi che hanno lasciato, utile per il calcolo delle
        // ricompense dovute ai voti positivi
        HashMap<String, Integer> raters = new HashMap<>();

        // Ciclo all'interno dei post originali (ovvero quelli creati esplicitamente dagli utenti)
        for (List<Post> postList : server.getAuthorPost().values()) {

            // Per ogni post calcolo la ricompensa
            for (Post p : postList) {

                // Azzero i parametri di calcolo
                postRating = 0;
                totComments = 0;
                totPositiveRatings = 0;
                comments = new HashMap<>();
                raters = new HashMap<>();

                // Ottengo l'id del post
                Long postId = p.getId();

                // Calcolo i nuovi voti (cioè i voti aggiunti dopo l'ultimo calcolo delle ricompense
                if (server.getVotes().get(postId) != null) {

                    // Per ogni voto del post corrente
                    for (Vote v : server.getVotes().get(postId)) {

                        // Se il voto è stato aggoiunto recentemente
                        if (v.getTimestamp().after(lastComputing)) {
                            // Tieni traccia degli utenti che hanno votato positivamente
                            if (v.getValue() > 0) {
                                totPositiveRatings++;
                                raters.putIfAbsent(v.getUser(), 0);
                                raters.replace(v.getUser(), raters.get(v.getUser()) + 1);
                            }

                            // Tieni traccia del rating totale del post
                            postRating += v.getValue();
                        }
                    }
                }

                // Nuovi commenti dall'ultimo calcolo
                if (server.getComments().get(postId) != null) {
                    // Per ogni commento
                    for (Comment c : server.getComments().get(postId)) {

                        // Tieni traccia del numero totale di commenti e degli utenti che hanno commentato
                        if (c.getTimestamp().after(lastComputing)) {
                            totComments++;
                            comments.putIfAbsent(c.getUser(), 0);
                            comments.replace(c.getUser(), comments.get(c.getUser()) + 1);
                        }
                    }
                }

                // Calcola la parte della formula relativa ai commenti
                int commentPart = 0;
                for (String user : comments.keySet()) {
                    commentPart += (2 / (1 + Math.exp(-(comments.get(user) - 1))));
                }

                // Calcolo della ricompensa
                double reward = (Math.log(Math.max(postRating, 0) + 1) + Math.log(commentPart + 1)) / p.getRewardAmount();
                p.increaseRewardAmount();

                // Se la ricompensa è significativa, distribuiscila tra curatori e autore
                if (reward > 0) {
                    // Divisione della ricompensa tra autore e curatori
                    double author = (reward / 100) * authorPercentage;
                    double curator = reward - author;
                    // Frazione di ricompensa dei curatori che spetta a ogni curatore per ogni commento o voto positivo
                    double curatorFraction = curator / (totComments + totPositiveRatings);

                    // Accredito della ricompensa all'autore
                    server.getUsers().get(p.getAuthor()).addReward(new Transaction("Ricompensa autore", author, p.getId()));

                    // Accredito della ricompensa ai curatori: i curatori si dividono i ricavi in parti uguali
                    // Accredito delle ricompense per i commenti
                    if (curatorFraction < Double.POSITIVE_INFINITY) {
                        for (String user : comments.keySet()) {
                            server.getUsers().get(user).addReward(new Transaction("Ricompensa autore (commenti)",
                                    curatorFraction * comments.get(user), p.getId()));
                        }
                    }

                    // Accredito delle ricompense per voti
                    for (String user : raters.keySet()) {
                        server.getUsers().get(user).addReward(new Transaction("Ricompensa autore (voti)",
                                curatorFraction * raters.get(user), p.getId()));
                    }
                }
            }
        }

        // Invio una notifica ai client connessi in multicast
        server.notifyReward();
        // Aggiorno il tempo dell'ultimo calcolo
        lastComputing = new Timestamp(System.currentTimeMillis());
    }
}

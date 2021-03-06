import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Vector;

public class ServerRewards extends Thread{
    // Server contenente i dati necessari per calcolare le ricompense
    private final WinsomeServerMain server;
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
    public ServerRewards(WinsomeServerMain server, long rewardRateMillis, double authorPercentage) {
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
    private void calculateRewards() {
        // Rating totale del post (comprende anche i voti negativi)
        int postRating;
        // Numero totale di commenti per post
        int totComments;
        int totPositiveRatings;

        // Mappa che collega gli utenti al numero di commenti che hanno effettuato, utile per il calcolo delle
        // ricompense dovute ai commenti
        HashMap<String, Integer> comments;
        // Mappa che collega gli utenti al numero di voti positivi che hanno lasciato, utile per il calcolo delle
        // ricompense dovute ai voti positivi
        HashMap<String, Integer> raters;

        synchronized (server) {
            // Ciclo all'interno dei post originali (ovvero quelli creati esplicitamente dagli utenti)
            for (Vector<Long> postList : server.getAuthorPost().values()) {
                // Per ogni post calcolo la ricompensa
                for (Long postId : postList) {

                    // Azzero i parametri di calcolo
                    postRating = 0;
                    totComments = 0;
                    totPositiveRatings = 0;
                    comments = new HashMap<>();
                    raters = new HashMap<>();

                    // Ottengo l'id del post
                    Post p = server.getPosts().get(postId);
                    // Aumento il numero di volte che quel post ha subito il calcolo
                    p.increaseRewardAmount();

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
                                server.getUsers().get(user).addReward(new Transaction("Ricompensa curatore (commenti)",
                                        curatorFraction * comments.get(user), p.getId()));
                            }
                        }

                        // Accredito delle ricompense per voti
                        for (String user : raters.keySet()) {
                            server.getUsers().get(user).addReward(new Transaction("Ricompensa curatore (voti)",
                                    curatorFraction * raters.get(user), p.getId()));
                        }
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

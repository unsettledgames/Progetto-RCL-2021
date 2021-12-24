import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ServerRewards extends Thread{
    private WinsomeServer server;
    private long rewardRateMillis;
    private Timestamp lastComputing;
    private double authorPercentage;

    public ServerRewards(WinsomeServer server, long rewardRateMillis, double authorPercentage) {
        this.authorPercentage = authorPercentage;
        this.server = server;
        this.rewardRateMillis = rewardRateMillis;
        this.lastComputing = new Timestamp(0);
    }

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

    private synchronized void calculateRewards() {
        int postRating = 0;
        int totComments = 0;
        int totPositiveRatings = 0;

        HashMap<String, Integer> comments = new HashMap<>();
        HashMap<String, Integer> raters = new HashMap<>();

        // Ciclo all'interno dei post originali così da gestire con più facilità i rewin
        for (List<Post> postList : server.getAuthorPost().values()) {
            for (Post p : postList) {
                if (!p.isRewin()) {
                    Long postId = p.getId();
                    // Nuovi voti dall'ultimo calcolo
                    if (server.getVotes().get(postId) != null) {
                        for (Vote v : server.getVotes().get(postId)) {
                            // Tieni traccia degli utenti che hanno votato positivamente
                            if (v.getTimestamp().after(lastComputing)) {
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

                    if (reward > 0) {
                        // Divisione della ricompensa tra autore e curatori
                        double author = (reward / 100) * authorPercentage;
                        double curator = reward - author;
                        double curatorFraction = curator / (totComments + totPositiveRatings);

                        System.out.println("Ricompensa: " + author + ", " + curatorFraction);

                        // Accredito della ricompensa all'autore
                        server.getUsers().get(p.getAuthor()).addReward(new Transaction("Ricompensa autore", author, p.getId()));

                        // Accredito della ricompensa ai curatori: i curatori si dividono i ricavi in parti uguali
                        for (String user : comments.keySet()) {
                            server.getUsers().get(user).addReward(new Transaction("Ricompensa autore",
                                    curatorFraction * comments.get(user), p.getId()));
                        }
                        for (String user : raters.keySet()) {
                            server.getUsers().get(user).addReward(new Transaction("Ricompensa autore",
                                    curatorFraction * raters.get(user), p.getId()));
                        }
                    }
                }
            }
        }

        // Aggiorno il tempo dell'ultimo calcolo
        lastComputing = new Timestamp(System.currentTimeMillis());

    }
}

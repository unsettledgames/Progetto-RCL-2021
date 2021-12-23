import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;

public class ServerRewards extends Thread{
    private WinsomeServer server;
    private long rewardRateMillis;
    private Timestamp lastComputing;

    public ServerRewards(WinsomeServer server, long rewardRateMillis) {
        this.server = server;
        this.rewardRateMillis = rewardRateMillis;
        this.lastComputing = new Timestamp(System.currentTimeMillis());
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
        int nComments = 0;
        HashMap<String, Integer> comments = new HashMap<String, Integer>();

        for (Post p : server.getPosts().values()) {
            // Nuovi voti dall'ultimo calcolo
            for (Vote v : server.getVotes().get(p.getId()))
                if (v.getTimestamp().after(lastComputing))
                    postRating += v.getValue();

            // Nuovi commenti dall'ultimo calcolo
            for (Comment c : server.getComments().get(p.getId())) {
                if (c.getTimestamp().after(lastComputing)) {
                    nComments++;
                    comments.putIfAbsent(c.getUser(), 0);
                    comments.replace(c.getUser(), comments.get(c.getUser()) + 1);
                }
            }

            // Numero di iterazioni dall'ultima volta
            // Attenzione alla gestione dei rewin
        }

        // Aggiorno il tempo dell'ultimo calcolo
        lastComputing = new Timestamp(System.currentTimeMillis());
    }
}

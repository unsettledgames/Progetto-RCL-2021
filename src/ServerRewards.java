import java.sql.Time;
import java.sql.Timestamp;

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
        for (Post p : server.getPosts().values()) {
            // Nuovi like dall'ultimo calcolo
            // Nuovi commenti dall'ultimo calcolo
            // Numero di iterazioni dall'ultima volta
            // Attenzione alla gestione dei rewin
        }

        // Aggiorno il tempo dell'ultimo calcolo
        lastComputing = new Timestamp(System.currentTimeMillis());
    }
}

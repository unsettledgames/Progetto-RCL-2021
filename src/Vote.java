import java.sql.Timestamp;

/** Classe che rappresenta il voto di un utente a un post
 *
 */
public class Vote {
    // Valore del voto: 1 (voto positivo o upvote) oppure -1 (voto negativo o downvote)
    private final int value;
    // Utente che ha eseguito il voto
    private final String user;
    // Data di creazione del voto
    private final Timestamp timestamp;

    /** Semplice costruttore che assegna i parametri agli attributi
     *
     * @param user Utente che ha posto il voto
     * @param value Valore del voto (1 o -1)
     */
    public Vote(String user, int value) {
        this.user = user;
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.value = value;
    }

    // Semplici getters
    public String getUser() { return user; }
    public Timestamp getTimestamp() { return timestamp; }
    public boolean isPositive() { return value > 0; }
    public int getValue() {return value;}
}

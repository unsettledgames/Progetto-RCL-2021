import java.sql.Timestamp;

/** Classe usata per rappresentare una transazione all'interno di Winsome, più specificamente l'accredito di una
 *  ricompensa da parte del server. Usata dagli utenti per tenere traccia dei movimenti, permette anche di distinguere
 *  tra diverse causali di accredito.
 *
 */
public class Transaction {
    // Data di esecuzione della transazione
    private final Timestamp timestamp;
    // Quantità di wincoin assegnati per questa transazione
    private final double amount;
    // Causale della transazione
    private final String causal;
    // Id del post a cui si riferisce questa transazione
    private final Long post;

    /** Semplice costruttore di assegnamento dei parametri. Tramite questo costruttore si genera inoltre il timestamp
     *  di esecuzione della transazione.
     *
     * @param causal Causale della transazione
     * @param amount Quantità di denaro accreditato
     * @param post Id del post per cui la transazione è stata eseguita
     */
    public Transaction(String causal, double amount, Long post) {
        this.amount = amount;
        this.causal = causal;
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.post = post;
    }

    // Getters degli attributi
    public Timestamp getTimestamp() {
        return timestamp;
    }
    public double getAmount() {
        return amount;
    }
    public String getCausal() {
        return causal;
    }
    public String getDate() {
        return timestamp.toString();
    }
}

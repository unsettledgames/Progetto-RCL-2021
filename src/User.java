import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Classe usata per rappresentare un utente all'interno del social. Oltre ai tipici attributi che lo caratterizzano,
 *  contiene anche il saldo totale dell'utente in Wincoins e lo storico delle transazioni a lui indirizzate.
 *
 */
public class User {
    // Nome utente
    private final String username;
    // Password, in realtà l'hash SHA256 della password
    private final String password;
    // Array di tag scelti dall'utente al momento della registrazione
    private final String[] tags;
    // Saldo totale dell'utente
    private double wallet;
    // Storico delle transazioni dell'utente
    private List<Transaction> transactions;

    /** Costruttore di base che assegna i parametri agli atributi
     *
     * @param username Nome utente
     * @param password Hash in SHA256 della password dell'utente
     * @param tags Lista di tag scelti dall'utente
     */
    public User(String username, String password, String[] tags) {
        this.username = username;
        this.password = password;
        this.tags = tags;
        this.wallet = 0;
        this.transactions = new ArrayList<>();

        // I tag vengono convertiti in lowercase
        for (int i=0; i<tags.length; i++)
            tags[i] = tags[i].toLowerCase(Locale.ROOT);
    }

    // Semplici getter di base
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
    public String[] getTags() {
        return tags;
    }
    public List<Transaction> getTransactions() {
        return transactions;
    }
    public double getWallet() {
        return wallet;
    }

    /** Aggiunge una transazione allo storico, aggiornando anche il saldo totale
     *
     * @param toAdd Transazione da aggiungere
     */
    public void addReward(Transaction toAdd) {
        if (this.transactions == null)
            this.transactions = new ArrayList<>();
        // Aggiunta del saldo
        this.wallet += toAdd.getAmount();
        // Aggiunta della transazione
        this.transactions.add(toAdd);
    }
}

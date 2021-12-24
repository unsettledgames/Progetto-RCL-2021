import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class User {
    private String username;
    private String password;
    private String[] tags;
    private double wallet;
    private List<Transaction> transactions;

    public User(String username, String password, String[] tags) {
        this.username = username;
        this.password = password;
        this.tags = tags;
        this.wallet = 0;
        this.transactions = new ArrayList<>();

        for (int i=0; i<tags.length; i++)
            tags[i] = tags[i].toLowerCase(Locale.ROOT);
    }

    public String getUsername() {
        return username;
    }
    public String getPassword() {return password;}
    public String[] getTags() {
        return tags;
    }
    public List<Transaction> getTransactions() {return transactions;}

    public void addReward(Transaction toAdd) {
        if (this.transactions == null)
            this.transactions = new ArrayList<>();
        this.wallet += toAdd.getAmount();
        this.transactions.add(toAdd);
    }
    public double getWallet() {return wallet;}
    public double getWalletBitcoin() {
        // TODO: Connessione a random.org e conversione del valore del wallet
        return 0;
    }
}

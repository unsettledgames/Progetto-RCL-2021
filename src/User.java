import java.util.Locale;

public class User {
    private String username;
    private String password;
    private String[] tags;
    private double wallet;

    public User(String username, String password, String[] tags) {
        this.username = username;
        this.password = password;
        this.tags = tags;
        this.wallet = 0;

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

    public void addReward(double toAdd) {
        this.wallet += toAdd;
    }
    public double getWallet() {return wallet;}
    public double getWalletBitcoin() {
        // TODO: Connessione a random.org e conversione del valore del wallet
        return 0;
    }
}

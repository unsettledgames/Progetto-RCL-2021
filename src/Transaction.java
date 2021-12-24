import java.sql.Timestamp;

public class Transaction {
    private Timestamp timestamp;
    private double amount;
    private String causal;
    private Long post;

    public Transaction(String causal, double amount, Long post) {
        this.amount = amount;
        this.causal = causal;
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.post = post;
    }

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

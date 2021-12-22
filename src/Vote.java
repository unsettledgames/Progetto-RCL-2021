import java.sql.Timestamp;

public class Vote {
    private int value;
    private String user;
    private Timestamp timestamp;

    public Vote(String user, int value) {
        this.user = user;
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.value = value;
    }

    public String getUser() { return user; }
    public Timestamp getTimestamp() { return timestamp; }
    public boolean isPositive() { return value > 0; }
    public int getValue() {return value;}
}

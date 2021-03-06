import java.sql.Timestamp;

public class Comment {
    private final String user;
    private final String content;
    private final Timestamp timestamp;

    public Comment(String user, String content) {
        this.user = user;
        this.content = content;
        this.timestamp = new Timestamp(System.currentTimeMillis());
    }

    public String getUser() {
        return user;
    }
    public String getContent() {
        return content;
    }
    public Timestamp getTimestamp() {
        return timestamp;
    }
}

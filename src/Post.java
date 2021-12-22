import java.sql.Timestamp;

public class Post {
    private long id;
    private String title;
    private String content;
    private Timestamp timestamp;
    private boolean rewin;
    private int rewardAmount;

    private static long postId;

    public Post(String title, String content) {
        init(title, content, false, 0);
    }

    public Post(Post other) {
        init(other.getTitle(), other.getContent(), true, other.getRewardAmount());
    }

    private void init(String title, String content, boolean rewin, int rewardAmount) {
        this.title = title;
        this.content = content;
        this.id = postId;
        this.rewin = rewin;
        this.rewardAmount = rewardAmount;
        this.timestamp = new Timestamp(System.currentTimeMillis());

        Post.postId++;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public int getRewardAmount() {
        return rewardAmount;
    }

    public boolean isRewin() {
        return rewin;
    }

    public long getId() { return id; }

    public static void setMinId(long toSet) { Post.postId = toSet;}
}

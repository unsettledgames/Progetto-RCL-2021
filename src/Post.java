import java.sql.Timestamp;

public class Post implements Comparable{
    private long id;
    private String title;
    private String content;
    private String author;
    private Timestamp timestamp;
    private boolean rewin;
    private int rewardAmount;

    private static long postId;

    public Post(String title, String content, String author) {
        init(title, content, author, false, 0);
    }

    public Post(Post other) {
        init(other.getTitle(), other.getContent(), other.getAuthor(), true, other.getRewardAmount());
    }

    private void init(String title, String content, String author, boolean rewin, int rewardAmount) {
        this.title = title;
        this.content = content;
        this.author = author;
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

    public String getAuthor() { return author; }

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

    @Override
    public int compareTo(Object o) {
        Post p = (Post) o;
        if (p.getTimestamp().equals(timestamp))
            return 0;
        else if (p.getTimestamp().before(timestamp))
            return -1;
        else
            return 1;
    }
}

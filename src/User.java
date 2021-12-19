public class User {
    private String username;
    private String password;
    private String[] tags;

    public User(String username, String password, String[] tags) {
        this.username = username;
        this.password = password;
        this.tags = tags;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String[] getTags() {
        return tags;
    }
}

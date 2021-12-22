import com.google.gson.Gson;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.sql.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomeWorker implements Runnable {
    private ClientRequest request;
    private WinsomeServer server;
    private SelectionKey key;

    public WinsomeWorker(WinsomeServer server, ClientRequest request) {
        this.request = request;
        this.server = server;
        this.key = request.getKey();
    }

    public void login(String user, String pass) throws IOException {
        User u = server.getUser(user);

        if (u != null) {
            if (!server.isInSession(user)) {
                if (u.getPassword().equals(pass)) {
                    server.addSession(user, request.getKey());
                    ComUtility.attachAck(key);
                }
                else
                    ComUtility.attachError(-2, "Password errata", key);
            }
            else
                ComUtility.attachError(-1, "Utente già loggato", key);
        }
        else
            ComUtility.attachError(-3, "Utente non esistente", key);
    }

    public void logout() throws IOException {
        String user = request.getJson().getString("user");

        if (server.isInSession(user)) {
            server.endSession(user);
            ComUtility.attachAck(key);
        }
        else
            ComUtility.attachError(-1, "Utente non loggato", key);
    }


    public void listUsers() throws IOException {
        String user = request.getJson().getString("user");
        User currUser = server.getUser(user);

        JSONObject json = new JSONObject();
        Gson gson = new Gson();

        Iterator<Map.Entry<String, User>> it = server.getUsers().entrySet().iterator();
        HashMap<String, String[]> ret = new HashMap<>();

        while (it.hasNext()) {
            Map.Entry<String, User> item = it.next();

            if (!item.getValue().equals(currUser)) {
                String[] commonTags = getCommonTags(user, item.getValue().getUsername());

                if (commonTags.length > 0)
                    ret.put(item.getKey(), commonTags);
            }
        }

        json.put("errCode", 0);
        json.put("errMsg", "OK");
        json.put("items", gson.toJson(ret));
        key.attach(json.toString());
    }


    public void listFollowing() throws IOException {
        String user = request.getJson().getString("user");

        JSONObject json = new JSONObject();
        Gson gson = new Gson();
        List<String> toConvert = server.getFollowing().get(user);

        if (toConvert == null)
            toConvert = new ArrayList<>();
        json.put("items", gson.toJson(toConvert));
        json.put("errCode", 0);
        json.put("errMsg", "OK");

        key.attach(json.toString());
    }


    public synchronized void follow() throws IOException {
        JSONObject reply = new JSONObject();
        String toFollow = request.getJson().getString("toFollow");
        String follower = request.getJson().getString("user");

        if (toFollow.equals(follower)) {
            ComUtility.attachError(-3, "Non puoi seguire te stess@", key);
            return;
        }

        if (!server.getUsers().containsKey(toFollow)) {
            ComUtility.attachError(-2, "L'utete da seguire non esiste", key);
            return;
        }

        String[] commonTags = getCommonTags(toFollow, follower);

        ConcurrentHashMap<String, List<String>> followers = server.getFollowers();
        if (!followers.containsKey(toFollow)) {
            followers.put(toFollow, new ArrayList<>());
        }
        // Se la condizione è verificata, il client sta già seguendo l'utente
        if (followers.get(toFollow).contains(follower)) {
            reply.put("errCode", -1);
            reply.put("errMsg", "Stai gia' seguendo questo utente");
            key.attach(reply.toString());
            return;
        }
        // Altrimenti posso continuare a impostare le relazioni di follower-following
        followers.get(toFollow).add(follower);

        // Notify clients
        server.notifyNewFollower(follower, toFollow, true);

        ConcurrentHashMap<String, List<String>> following = server.getFollowing();
        if (!following.containsKey(follower)) {
            following.put(follower, new ArrayList<>());
        }
        following.get(follower).add(toFollow);

        reply.put("errCode", 0);
        reply.put("errMsg", "OK");

        key.attach(reply.toString());
    }


    public synchronized void unfollow() throws IOException {
        JSONObject reply = new JSONObject();
        String toUnfollow = request.getJson().getString("toUnfollow");
        String follower = request.getJson().getString("user");

        if (!server.getUsers().containsKey(toUnfollow)) {
            ComUtility.attachError(-2, "L'utente da smettere di seguire non esiste", key);
            return;
        }
        ConcurrentHashMap<String, List<String>> followers = server.getFollowers();
        // Se l'utente non sta ancora seguendo l'utente da smettere di seguire
        if (followers.get(toUnfollow) == null || !followers.get(toUnfollow).contains(follower)) {
            // Ritorna un codice di errore
            ComUtility.attachError(-1, "Non stai ancora seguendo questo utente", key);
            return;
        }

        // Altrimenti posso continuare a impostare le relazioni di follower-following
        followers.get(toUnfollow).remove(follower);
        // Invia la notifica di unfollow ai client connessi
        server.notifyUnfollow(follower, toUnfollow);

        ConcurrentHashMap<String, List<String>> following = server.getFollowing();
        following.get(follower).remove(toUnfollow);

        reply.put("errCode", 0);
        reply.put("errMsg", "OK");

        key.attach(reply.toString());
    }


    public synchronized void createPost() throws IOException {
        JSONObject req = request.getJson();
        String user = req.getString("user");
        ConcurrentHashMap<String, List<Post>> posts = server.getAuthorPost();

        posts.computeIfAbsent(user, k -> new ArrayList<>());
        posts.get(user).add(new Post(req.getString("postTitle"), req.getString("postContent"), user));

        ComUtility.attachAck(request.getKey());
    }


    public void viewBlog() {
        JSONObject reply = new JSONObject();
        String user = request.getJson().getString("user");
        List<Post> userBlog = server.getAuthorPost().get(user);

        if (userBlog == null)
            userBlog = new ArrayList<>();
        Collections.sort(userBlog);

        reply.put("items", new Gson().toJson(userBlog));
        key.attach(reply.toString());
    }


    public void viewFeed() {
        JSONObject reply = new JSONObject();
        String user = request.getJson().getString("user");
        List<Post> userFeed = getFeed(user);

        reply.put("items", new Gson().toJson(userFeed));
        key.attach(reply.toString());
    }


    public synchronized void ratePost() throws IOException {
        JSONObject req = request.getJson();
        String author = req.getString("user");
        Long post = req.getLong("post");
        ConcurrentHashMap<Long, List<Vote>> votes = server.getVotes();
        List<Vote> currVotes = votes.get(post);

        if (currVotes != null) {
            for (Vote v : currVotes) {
                if (v.getUser().equals(author)) {
                    ComUtility.attachError(-1, "Errore di votazione: hai già votato questo post.", request.getKey());
                    return;
                }
            }
        }
        if (!getFeed(author).contains(server.getPosts().get(post))) {
            ComUtility.attachError(-2, "Errore di votazione: non puoi votare un post che non fa " +
                    "parte del tuo feed", request.getKey());
            return;
        }
        if (server.getPosts().get(post).getAuthor().equals(author)) {
            ComUtility.attachError(-3, "Errore di votazione: non puoi votare un tuo post.", request.getKey());
            return;
        }

        if (currVotes == null) {
            votes.put(post, new ArrayList<>());
            currVotes = votes.get(post);
        }
        Vote toAdd = new Vote(author, req.getInt("value"));
        currVotes.add(toAdd);

        ComUtility.attachAck(request.getKey());
    }


    public synchronized void addComment() throws IOException {
        JSONObject req = request.getJson();
        String user = req.getString("user");
        Long post = req.getLong("post");
        ConcurrentHashMap<String, List<Post>> userPosts = server.getAuthorPost();
        ConcurrentHashMap<Long, Post> posts = server.getPosts();
        ConcurrentHashMap<Long, List<Comment>> comments = server.getComments();
        List<Post> userFeed = getFeed(user);

        if (userPosts.get(user).contains(posts.get(post))) {
            ComUtility.attachError(-1, "Errore nell'aggiunta del commento: non puoi commentare i tuoi " +
                    "stessi post", request.getKey());
            return;
        }
        if (!userFeed.contains(posts.get(post))) {
            ComUtility.attachError(-2, "Errore nell'aggiunta del commento: impossibile commentare un" +
                    " post non presente all'interno del feed", request.getKey());
            return;
        }

        if (comments.get(post) == null) {
            comments.put(post, new ArrayList<>());
        }
        comments.get(post).add(new Comment(user, req.getString("comment")));
        ComUtility.attachAck(request.getKey());
    }


    public synchronized void showPost() throws IOException {
        JSONObject req = request.getJson();
        JSONObject reply = new JSONObject();
        Long post = req.getLong("post");
        String user = req.getString("user");
        List<Post> feed = getFeed(user);
        Post toShow = server.getPosts().get(post);

        if (toShow != null && (feed.contains(toShow) || toShow.getAuthor().equals(user))) {
            int nNegative = 0;
            int nPositive = 0;
            List<Comment> comments = server.getComments().get(post);
            List<Vote> votes = server.getVotes().get(post);

            for (Vote v : votes) {
                if (v.isPositive())
                    nPositive++;
                else
                    nNegative++;
            }

            reply.put("errCode", 0);
            reply.put("errMsg", "OK");
            reply.put("title", toShow.getTitle());
            reply.put("comments", new Gson().toJson(comments));
            reply.put("nUpvotes", nPositive);
            reply.put("nDownvotes", nNegative);
            reply.put("content", toShow.getContent());

            request.getKey().attach(reply.toString());
        }
        else {
            ComUtility.attachError(-1, "Errore di visualizzazione: non sei autorizzato a vedere questo post",
                    request.getKey());
        }
    }


    private List<Post> getFeed(String user) {
        List<Post> ret = new ArrayList<>();

        for (String following : server.getFollowing().get(user)) {
            if (server.getAuthorPost().get(following) != null) {
                ret.addAll(server.getAuthorPost().get(following));
            }
        }

        Collections.sort(ret);

        return ret;
    }


    private String[] getCommonTags(String userA, String userB) {
        User a = server.getUser(userA);
        User b = server.getUser(userB);

        String[] aTags = a.getTags();
        List<String> bTags = Arrays.asList(b.getTags());
        List<String> ret = new ArrayList<>();

        for (String aTag : aTags)
            if (bTags.contains(aTag))
                ret.add(aTag);

        return Arrays.copyOf(ret.toArray(), ret.size(), String[].class);
    }


    @Override
    public void run() {
        // Ottieni richiesta e client (attraverso la selection key)
        JSONObject currRequest = request.getJson();

        try {
            // Esegui le diverse operazioni
            switch (currRequest.getInt("op")) {
                case OpCodes.LOGIN:
                    if (currRequest.has("username") && currRequest.has("password")) {
                        login(currRequest.getString("username"), currRequest.getString("password"));
                    } else {
                        ComUtility.attachError(-1, "Transmission error", key);
                    }
                    break;
                case OpCodes.LOGOUT:
                    logout();
                    break;
                case OpCodes.LIST_USERS:
                    listUsers();
                    break;
                case OpCodes.LIST_FOLLOWING:
                    listFollowing();
                    break;
                case OpCodes.FOLLOW:
                    follow();
                    break;
                case OpCodes.UNFOLLOW:
                    unfollow();
                    break;
                case OpCodes.CREATE_POST:
                    createPost();
                    break;
                case OpCodes.SHOW_BLOG:
                    viewBlog();
                    break;
                case OpCodes.SHOW_FEED:
                    viewFeed();
                    break;
                case OpCodes.RATE_POST:
                    ratePost();
                    break;
                case OpCodes.COMMENT_POST:
                    addComment();
                    break;
                case OpCodes.SHOW_POST:
                    showPost();
                    break;
                default:
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

import java.sql.Timestamp;

public class Post implements Comparable<Post> {
    // Id del post
    private long id;
    // Titolo del post (lungo al masimo 20 caratteri)
    private String title;
    // Contenuto del post (lungo al massimo 500 caratteri)
    private String content;
    // Nome utente dell'autore del post
    private String author;
    // Data di creazione del post
    private Timestamp timestamp;
    // Flag che indica se il post è un rewin o meno
    private boolean rewin;
    // Nome dell'utente che ha rewinnato questo post
    private String rewinner;
    // Numero di volte che l'algoritmo di ricompensa è stato eseguito su questo post. Per evitare numeri infiniti
    // o valori NaN, viene impostato a 1 per la prima volta
    private int rewardAmount;
    // Prossimo id utilizzabile dal prossimo post che verrà creato
    private static long postId;

    /** Costruttore di un nuovo Post (cioè di un Post che non è un rewin). Assegna i parametri ai rispettivi
     *  attributi e assegna un rewinner vuoto (non può esistere un nome utente vuoto, quindi è possibile distinguere
     *  tra rewin e post).
     *
     * @param title Titolo del post (lungo al massimo 20 caratteri)
     * @param content Contenuto del post (lungo al massimo 500 caratteri)
     * @param author Nome utente dell'autore del post
     */
    public Post(String title, String content, String author) {
        rewinner = "";
        init(title, content, author, false, 1);
    }

    /** Costruttore di un post di rewin. Il post originale viene passato come parametro e viene anche specificato il
     *  nome dell'utente che ha rewinnato questo post.
     *
     * @param other Post originale che è stato rewinnato da rewinner
     * @param rewinner Utente che ha rewinnato other
     */
    public Post(Post other, String rewinner) {
        this.rewinner = rewinner;
        init(other.getTitle(), other.getContent(), other.getAuthor(), true, other.getRewardAmount());
    }

    /** Funzione di utilità che assegna i parametri comuni ai due costruttori
     *
     * @param title Titolo del post
     * @param content Contenuto del post
     * @param author Autore del post
     * @param rewin Il post è un rewin?
     * @param rewardAmount Numero di volte che l'algoritmo di reward è stato eseguito + 1
     */
    private void init(String title, String content, String author, boolean rewin, int rewardAmount) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.id = postId;
        this.rewin = rewin;
        this.rewardAmount = rewardAmount;
        this.timestamp = new Timestamp(System.currentTimeMillis());

        // Importante aggiornare il prossimo id disponibile in modo che sia univoco
        Post.postId++;
    }

    /** Impostazione del prossimo id per un post: questo metodo viene invocato al momento del caricamento del server,
     *  in modo che il prossimo id sia coerente con il contenuto precedente del server.
     *
     * @param toSet Nuovo prossimo id per la classe Post
     */
    public static void setMinId(long toSet) {
        Post.postId = toSet;
    }

    /** Aumenta di 1 il numero di volte che l'algoritmo di reward è stato eseguito per questo post
     *
     */
    public void increaseRewardAmount(){
        this.rewardAmount++;
    }

    // Semplici getter degli attributi
    public String getTitle() {
        return title;
    }
    public String getContent() {
        return content;
    }
    public String getAuthor() {
        return author;
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
    public long getId() {
        return id;
    }
    public String getRewinner() {
        return rewinner;
    }

    @Override
    public int compareTo(Post p) {
        if (p.getTimestamp().equals(timestamp))
            return 0;
        else if (p.getTimestamp().before(timestamp))
            return -1;
        else
            return 1;
    }
}

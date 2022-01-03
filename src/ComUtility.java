import org.json.JSONObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class ComUtility {
    /** Allega una stringa in formato JSON che rappresenta un errore avvenuto nel corso dell'operazione richiesta
     *  dal client identificato dalla SelectionKey passata come parametro
     *
     * @param code Codice di errore
     * @param message Rappresentazione leggibile del codice di errore
     * @param key SelectionKey del client che ha spedito la richiesta che ha fallito
     */
    public static void attachError(int code, String message, SelectionKey key) {
        JSONObject response = new JSONObject();
        response.put("errCode", code);
        response.put("errMsg", message);
        key.attach(response.length() + response.toString());
    }

    /** Allega una stringa in formato JSON che rappresenta un acknowledgement, ovvero una coppia (codice di errore, messaggio)
     *  con valore (0, "OK")
     *
     * @param key La SelectionKey del client che deve ricevere l'ack
     */
    public static void attachAck(SelectionKey key) {
        JSONObject response = new JSONObject();
        response.put("errCode", 0);
        response.put("errMsg", "OK");
        key.attach(response.length() + response.toString());
    }

    /** Invia i dati allegati alla selectionkey passata come paramtetro lungo il canale non bloccante contenuto
     *  nella chiave stessa
     *
     * @param key SelectionKey che rappresenta l'endpoint che deve ricevere l'allegato
     * @throws IOException In caso di errore di comunicazione
     */
    public static void sendAsync(SelectionKey key) throws IOException {
        String toSend = (String)key.attachment();
        int toSendLen = toSend.getBytes(StandardCharsets.UTF_8).length;
        ByteBuffer buffer = ByteBuffer.allocate(toSendLen + 4);

        // Invia la dimensione del buffer
        buffer.putInt(toSendLen);
        buffer.put(toSend.getBytes());
        buffer.flip();

        while (buffer.hasRemaining())
            ((SocketChannel)key.channel()).write(buffer);

        // Resetta l'attachment in modo che non venga spedito due volte dal worker
        key.attach(null);
    }

    /** Invia la stringa toSend lungo il canale bloccante channel
     *
     * @param toSend La stringa da inviare (in formato JSON)
     * @param channel Il canale da usare per l'invio
     * @throws IOException In caso di errore nella comunicazione
     */
    public static void sendSync(String toSend, SocketChannel channel) throws IOException {
        int toSendLen = toSend.getBytes(StandardCharsets.UTF_8).length;
        ByteBuffer buffer = ByteBuffer.allocate(toSendLen + 4);

        // Invia la dimensione del buffer
        buffer.putInt(toSendLen);
        buffer.put(toSend.getBytes());
        buffer.flip();

        while (buffer.hasRemaining())
            channel.write(buffer);
    }

    /** Permette di ricevere una stringa in formato JSON dal canale specificato come parametro
     *
     * @param channel Il canale da cui ricevere la stringa
     * @return La stringa ricevuta in formato JSON
     * @throws IOException In caso di errore di comunicazione
     */
    public static String receive(SocketChannel channel) throws IOException {
        // Alloca 4 byte per leggere la dimensione del contenuto
        ByteBuffer reader = ByteBuffer.allocate(4);
        // Controllo EOF
        if (channel.read(reader) == -1) {
            return "";
        }
        reader.flip();
        reader.clear();
        // Leggi la dimensione del contenuto
        int size = reader.getInt();
        reader = ByteBuffer.allocate(size);

        // Leggi il contenuto
        while (reader.hasRemaining())
            channel.read(reader);
        reader.position(0);

        // Convertilo da byte a stringa
        StringBuilder builder = new StringBuilder();
        for (int i=0; i<size; i++) {
            builder.append((char)reader.get());
        }

        // Restituiscilo
        return builder.toString();
    }
}

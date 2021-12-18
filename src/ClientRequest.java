import org.json.JSONObject;

import java.nio.channels.SelectionKey;

public class ClientRequest {
    private SelectionKey key;
    private JSONObject json;

    public ClientRequest(SelectionKey key, JSONObject json) {
        this.key = key;
        this.json = json;
    }

    public SelectionKey getKey() {
        return key;
    }

    public JSONObject getJson() {
        return json;
    }
}

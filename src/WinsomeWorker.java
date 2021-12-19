import org.json.JSONObject;

import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class WinsomeWorker implements Runnable {
    private ClientRequest request;
    private WinsomeServer server;

    public WinsomeWorker(WinsomeServer server, ClientRequest request) {
        this.request = request;
        this.server = server;
    }

    @Override
    public void run() {
        JSONObject currRequest = request.getJson();
        System.out.println("Thread: " + Thread.currentThread().getName() + ", req: " + currRequest.toString());

        switch (currRequest.getInt("type")) {
            case 0:
                System.out.println("Signup");
                break;
            case 1:
                System.out.println("Signin");
                break;
        }
    }
}

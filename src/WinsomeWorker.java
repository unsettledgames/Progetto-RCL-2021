import org.json.JSONObject;

import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class WinsomeWorker implements Runnable {
    private HashMap<SelectionKey, LinkedBlockingQueue<JSONObject>> clientResponses;
    private LinkedBlockingQueue<JSONObject> requests;

    public WinsomeWorker(HashMap<SelectionKey, LinkedBlockingQueue<JSONObject>> clientResponses,
                         LinkedBlockingQueue<JSONObject> requests) {
        this.clientResponses = clientResponses;
        this.requests = requests;
    }

    @Override
    public void run() {
        while (true) {
            JSONObject currRequest = requests.poll();

            if (currRequest != null) {
                System.out.println("Received " + currRequest.toString());
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
    }
}

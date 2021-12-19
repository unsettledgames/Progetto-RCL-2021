import org.json.JSONObject;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRemoteServer extends Remote {
    int signup(String username, String password, String[] tags) throws RemoteException;
}

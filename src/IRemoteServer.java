import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRemoteServer extends Remote {
    String signup(String username, String password, String[] tags) throws RemoteException;

    void registerNotifications(String username, IRemoteClient client) throws RemoteException;

    void unregisterNotifications(String username) throws RemoteException;
}

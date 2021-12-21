import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRemoteClient extends Remote {
    void newFollower(String follower, boolean isNew) throws RemoteException;
}

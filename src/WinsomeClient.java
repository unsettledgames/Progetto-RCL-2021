import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.channels.*;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;

class WinsomeClient {
    private IRemoteServer signupObject;

    public WinsomeClient() throws RemoteException, NotBoundException {
        Registry r = LocateRegistry.getRegistry(6667);
        Remote ro = r.lookup("WINSOME_SIGNUP");
        signupObject = (IRemoteServer) ro;
    }

    public void signup(String comm) {
        String[] args = getStringArgs(comm, 3);
        if (args != null) {
            if (args.length > 7) {
                System.err.println("Errore di registrazione: troppi argomenti (massimo 5 tag)");
            }
            else {
                try {
                    String[] tags = Arrays.copyOfRange(args, 3, args.length);
                    int err = signupObject.signup(args[1], args[2], tags);

                    ClientError.handleError("Registrazione avvenuta con successo. Riepilogo:",
                            new TableList(3, "Username", "Password", "Tags").addRow(args[1],
                            args[2], Arrays.toString(tags)).withUnicode(true), err, 0);
                }
                catch (RemoteException e) {
                    System.err.println("Errore di rete: impossibile completare la registrazione");
                }
            }
        }
        else {
            System.err.println("Errore di registrazione: troppi pochi parametri.");
        }
    }

    public void login(String comm) {

    }

    private String[] getStringArgs(String comm, int minExpected) {
        String[] ret = comm.split(" ");
        if (ret.length-1 >= minExpected)
            return ret;
        return null;
    }

    public static void main(String[] args) throws IOException, NotBoundException {
        // Crea il client
        WinsomeClient client = new WinsomeClient();
        // Lettore dei comandi dell'utente
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        // Ultimo comando inserito dall'utente
        String currCommand = "";

        /*TableList ta = new TableList(4, "sas", "sus", "sos", "sis").withUnicode(true);
        ta.addRow("WOW", "NICE", "69420", "POGGERS");
        ta.print();*/

        // Connect to the server, prepare the buffer
        SocketAddress address = new InetSocketAddress("localhost", 6666);
        SocketChannel clientChannel = SocketChannel.open(address);

        while (!clientChannel.finishConnect()) {}

        while (!currCommand.equals("quit")) {
            currCommand = reader.readLine();

            switch (currCommand.split(" ")[0]) {
                case "signup":
                    client.signup(currCommand);
                    break;
                case "login":
                    client.login(currCommand);
                    break;
            }
        }

        /*
        ComUtility.send(toSend, clientChannel);
        System.out.println(client.signupObject.signup("Fintaman", "Franco", new String[5]));
        */
        clientChannel.close();
    }
}
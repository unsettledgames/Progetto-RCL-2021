import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class RewardNotifier extends Thread{
    private MulticastSocket mcSocket;
    private InetAddress address;

    public RewardNotifier(String addressString, int port) {
        try {
            // Registrazione al servizio di notifiche per il calcolo delle ricompense
            address = InetAddress.getByName(addressString);
            this.mcSocket = new MulticastSocket(port);
            mcSocket.joinGroup(address);
        }
        catch (IOException e) {
            System.err.println("Errore di connessione, impossibile unirsi al gruppo multicast per la ricezione delle" +
                    " notifiche riguardo il calcolo delle ricompense");
            e.printStackTrace();
        }
    }

    public void run() {
        while (true) {
            DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);

            try {
                mcSocket.receive(packet);
                StringBuilder sb = new StringBuilder();
                int i = 0;

                while (i < packet.getData().length && packet.getData()[i] != 0) {
                    sb.append((char)packet.getData()[i]);
                    i++;
                }
                System.out.println(sb.toString());

            } catch (IOException e) {
                System.err.println("Errore di ricezione della notifica di calcolo della ricompensa");
            }
        }
    }

    public void close() {
        try {
            this.mcSocket.leaveGroup(address);
            this.interrupt();
        } catch (IOException e) {
            System.err.println("Impossibile abbandonare il gruppo di multicast");
            e.printStackTrace();
        }
    }
}

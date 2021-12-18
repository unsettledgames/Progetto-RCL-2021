package requests;

/**
 * requests.Request: rappresenta una richiesta del client verso il server.
 *
 */
public class Request {
    /**
     * type: Indica il tipo di richiesta del server. Di seguito si elencano i possibili significati dell'attributo:
     *      0:  Richiesta di signup
     *      1:  Richiesta di login
     */
    private short type;

    public Request() {
    }

    public short getType() {
        return type;
    }
}

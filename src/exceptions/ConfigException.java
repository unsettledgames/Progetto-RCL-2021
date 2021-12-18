package exceptions;

public class ConfigException extends RuntimeException{
    public ConfigException(String msg) {
        super();
        System.err.println("Errore di configurazione del server: " + msg);
    }
}

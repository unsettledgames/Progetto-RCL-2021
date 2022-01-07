package exceptions;

public class ConfigException extends RuntimeException{
    private String msg;

    public ConfigException(String msg) {
        super();
        this.msg = msg;
    }

    public void printErr() {
        System.err.println("Errore di configurazione del server: " + msg);
    }
}

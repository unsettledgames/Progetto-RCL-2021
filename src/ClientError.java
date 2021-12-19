public class ClientError {

    public static void handleError(String successPhrase, int error, int op) {
        if (ClientError.printError(error, op) == 0) {
            System.out.println(successPhrase);
        }
    }
    public static void handleError(TableList toPrint, int error, int op) {
        if (ClientError.printError(error, op) == 0) {
            toPrint.print();
        }
    }
    public static void handleError(String successPhrase, TableList toPrint, int error, int op) {
        if (ClientError.printError(error, op) == 0) {
            System.out.println(successPhrase);
            toPrint.print();
        }
    }

    /** Stampa una frase di errore in caso di fallimento dell'operazione, altrimenti stampa un messaggio di feedback
     *  I codici di errore dipendono dall'operazione. Di seguito, per ogni tipo di operazione, si elencano i possibili
     *  codici di errore.
     *
     *  OP = 0 (SIGNUP):
     *      0:  Successo
     *      -1: Username già esistente
     *
     * @param error
     * @param op
     */
    private static int printError(int error, int op) {
        switch (op) {
            case 0:
                switch (error) {
                    case 0:
                        return 0;
                    case -1:
                        System.err.println("Errore di registrazione: nome utente non disponibile");
                        break;
                    default:
                        System.err.println("Errore di registrazione sconosciuto");
                        break;
                }
                break;
            default:
                System.err.println("Codice operazione sconosciuto");
                break;
        }

        return -1;
    }
}

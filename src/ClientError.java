/** Utility per la stampa dei codici e dei messaggi di errore avvenuti nel corso dell'esecuzione.
 *  I codici di errore dipendono dall'operazione. Di seguito, per ogni tipo di operazione, si elencano i possibili
 *  codici di errore (per ogni operazione, 0 indica il successo).
 *
 *  OP = 0 (SIGNUP):
 *      -1: Username già esistente
 *  OP = 1 (LOGIN):
 *      -1: Utente già loggato
 *      -2: Password errata
 *      -3: Nome utente errato
 *  OP = 2 (LOGOUT):
 *      -1: Utente non loggato
 *  OP = 4 (FOLLOW):
 *      -1: Utente già seguito
 *      -2: Utente non esistente
 */

public class ClientError {

    public static int handleError(String successPhrase, int error, String message) {
        if (ClientError.printError(error, message) == 0) {
            System.out.println(successPhrase);
            return 0;
        }
        return -1;
    }
    public static int handleError(TableList toPrint, int error, String message) {
        if (ClientError.printError(error, message) == 0) {
            toPrint.print();
            return 0;
        }
        return -1;
    }
    public static int handleError(String successPhrase, TableList toPrint, int error, String message) {
        if (ClientError.printError(error, message) == 0) {
            System.out.println(successPhrase);
            toPrint.print();
            return 0;
        }
        return -1;
    }


    private static int printError(int errCode, String message) {
        if (errCode == 0)
            return 0;
        System.out.println("Errore " + errCode + ": " + message);
        return -1;
    }
}

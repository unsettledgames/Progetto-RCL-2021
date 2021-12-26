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
 *      -3: Utente da seguire uguale all'utente che vuole seguire
 *      -4: Utente da seguire non condivide interessi
 *  OP = 5 (UNFOLOW):
 *      -1: Utente non ancora seguito
 *      -2: Utente non esistente
 *  OP = 10 (SHOW_POST):
 *      -1: Il post non è né visibile dall'utente né nel suo blog
 *  OP = 11 (RATE POST):
 *      -1: Post già votato dall'utente
 *      -2: Post non visibile dall'utente
 *      -3: Autore del voto è lo stesso del post
 *  OP = 12 (COMMENT POST):
 *      -1: Post creato dall'autore del commento
 *      -2: Post non presente nel feed dell'autore
 *  OP = 13 (DELETE POST):
 *      -1: Il post da cancellare non esiste
 *      -2: L'utente che intende cancellare il post non ne è l'autore
 *  OP = 14 (REWIN POST):
 *      -1: Il post non è visibile nel feed dell'utente
 *      -2: L'utente ha già rewinnato il post
 */

public class ClientError {

    /** In caso di successo stampa successPhrase, altrimenti stampa il messaggio di errore corrispondente al codice
     *  passato come parametro
     *
     * @param successPhrase Frase di feedback che indica il successo dell'operazione
     * @param error Codice di errore dell'operazione che si intende gestire
     * @param message Messaggio di errore corrispondente al codice appena descritto
     *
     * @return 0 se non ci sono stati errori, -1 altrimenti
     */
    public static int handleError(String successPhrase, int error, String message) {
        // Stampa la frase di successo solamente se non ci sono stati errori
        if (ClientError.printError(error, message) == 0) {
            System.out.println(successPhrase);
            return 0;
        }
        // Altrimenti notifica il chiamante del fallimento
        return -1;
    }

    /** In caso di successo stampa una tabella contenente dei dati di resoconto, altrimenti stampa il messaggio
     *  di errore corrispondente al codice passato come parametro
     *
     * @param toPrint La tabella di resoconto da stampare
     * @param error Codice di errore dell'ultima operazione eseguita
     * @param message Messaggio di errore corrispondente al codice
     * @return 0 in caso di successo, -1 altrimenti
     */
    public static int handleError(TableList toPrint, int error, String message) {
        if (ClientError.printError(error, message) == 0) {
            toPrint.print();
            return 0;
        }
        return -1;
    }

    /** In caso di successo stampa una tabella contenente dei dati di resoconto e una frase di successo, altrimenti
     *  stampa il messaggio di errore corrispondente al codice passato come parametro
     *
     * @param successPhrase Frase di feedback che indica il successo dell'operazione
     * @param toPrint La tabella di resoconto da stampare
     * @param error Codice di errore dell'ultima operazione eseguita
     * @param message Messaggio di errore corrispondente al codice
     * @return 0 in caso di successo, -1 altrimenti
     */
    public static int handleError(String successPhrase, TableList toPrint, int error, String message) {
        if (ClientError.printError(error, message) == 0) {
            System.out.println(successPhrase);
            toPrint.print();
            return 0;
        }
        return -1;
    }

    /** Funzione di utility della classe che stampa un messaggio e un codice di errore
     *
     * @param errCode Codice dell'errore
     * @param message Messaggio di errore corrispondente al codice
     * @return 0 in caso di successo, -1 altrimenti
     */
    private static int printError(int errCode, String message) {
        if (errCode == 0)
            return 0;
        System.err.println("Errore " + errCode + ": " + message);
        return -1;
    }
}

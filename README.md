# Progetto-RCL-2021

## POLISH
- Rendi la parte di notifica dei follower indipendente tramite oggetti dedicati
- Magari uniforma le strutture dati in modo che contengano solo id invece che oggetti (tranne quelle che usano come chiavi gli id e come valori gli oggetti)
- Rimuovi più synchronized possibili
- Aggiungi handleError che accetta solo un JSON e una frase di successo: dal json si estraggono codice e messaggio di errore e li si passa alle funzioni già esistenti
- In generale, cercare parti di codice ripetute e provare a isolarle in funzioni o classi
- Isolare il più possibile le eccezioni (eliminare throws nelle funzioni che usano ComUtility per esempio)
- Impedire registrazione quando si è loggati
- Rimuovi accenti
- Commenti
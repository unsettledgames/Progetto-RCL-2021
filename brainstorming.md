# Brainstorming

## Server
- ~~Client si connette~~
- ~~Si instaura una connessione TCP~~
  - ~~Nio~~
- I client inviano delle richieste
- Ogni richiesta è assegnata a un thread di un pool
  - Il thread elabora la risposta e la invia al client
  - Eventuale coda di risposte che il server invia appena può

## Client
- Mantiene una coda di risposte e notifiche da parte del server così da evitare sovrapposizioni con le notifiche

## Dati
- Utenti registrati: Hashmap<String, User>
- Post: Hashmap<User, List<Post>>
- Rewins: Hashmap<Post, List<User>>
- Likes: Hashmap<Post, List<User>>
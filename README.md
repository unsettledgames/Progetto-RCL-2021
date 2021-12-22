# Progetto-RCL-2021

## TODO

- User operations
  - See feed:
    - Only show posts from followers
  - See blog:
    - Show posts created by the user
    - Show Resteems
  - Upvote / downvote / rewin / comments (can't comment comments, but it'd be nice if you could rate them)
  - View post comments
  - View wallet
    - Convert wincoin to bitcoin
    - Convert wincoin to flat currency


- Rewards
  - Fixed reward ratio (creator / curator eg 70/30)
  - Handled by the server
  - Every once in a while, determine rewards depending on upvotes on posts / rewins


- Server
  - viewBlog() ritorna la lista di post creati dall'utente: si mostrano solo id, autore e titolo
  - showFeed(): id, autore e titolo dei post sul feed
  - showPost(id): fornisce dettagli maggiori su un post 
    - Titolo, contenuto
    - Numero di upvote e downvote
    - Commenti
    - In questo stato si può votare un post o commentarlo
  - deletePost(id): se un utente è autore di un post e lo sta visualizzando con showPost, può cancellarlo
  - rewinPost(id)
  - ratePost(id, voto): valuta un post
    - Errore se il post non è nel feed, se è l'autore del post o se ha già votato (magari a meno che non voglia cambiare il voto?)
    - "+1" o "-1"
  - addComment(id, commento)
    - Errore se l'utente è autore del post oppure se il post non è nel feed
  - getWallet(): restituisce il totale corrente e i dettagli sulle transazioni
  - getWalletBitcoin(): si attacca a random.org per generare il tasso di cambio e convertire la valuta

- Client
  - blog
  - feed
  - show post <id>
  - delete <id>
  - rewin <id>
  - rate <id> <voto>
  - comment <id> <commento>
  - wallet

## Roadmap
- Visualizzazione blog e feed
- Like, commenti, rewin
- Ricompense

## POLISH
- Rendi la parte di notifica dei follower indipendente tramite oggetti dedicati

## DEBUGGING
- Rintraccia interazioni parziali (errori che avvengono ma viene parzialmente alterato lo stato del server)
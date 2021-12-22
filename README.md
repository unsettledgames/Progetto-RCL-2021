# Progetto-RCL-2021

## TODO

- User operations
  - rewin
  - View wallet
    - Convert wincoin to bitcoin
    - Convert wincoin to flat currency


- Rewards
  - Fixed reward ratio (creator / curator eg 70/30)
  - Handled by the server
  - Every once in a while, determine rewards depending on upvotes on posts / rewins


- Server
  - deletePost(id): se un utente è autore di un post e lo sta visualizzando con showPost, può cancellarlo
  - rewinPost(id)
  - getWallet(): restituisce il totale corrente e i dettagli sulle transazioni
  - getWalletBitcoin(): si attacca a random.org per generare il tasso di cambio e convertire la valuta

- Client
  - delete <id>
  - rewin <id>
  - wallet

## Roadmap
- Rewin
- Ricompense

## POLISH
- Rendi la parte di notifica dei follower indipendente tramite oggetti dedicati

## DEBUGGING
- Rintraccia interazioni parziali (errori che avvengono ma viene parzialmente alterato lo stato del server)
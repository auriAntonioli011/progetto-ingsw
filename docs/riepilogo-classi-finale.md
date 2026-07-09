# Riepilogo finale delle classi (V1–V5)

Documento di supporto per disegnare i diagrammi UML globali (classi e casi d'uso)
dell'intero sistema, senza dover aprire i sorgenti. Per ogni classe: ruolo, metodi
rilevanti alla logica di business (esclusi getter/setter banali e helper privati)
e relazioni utili al diagramma delle classi.

Legenda relazioni: **eredita** (generalizzazione), **implementa** (realizzazione di
interfaccia), **compone** (composizione, il tutto possiede le parti), **aggrega**
(aggregazione/riferimento condiviso), **usa** (dipendenza significativa).

---

## Package `model`

### Utente (astratta)
Ruolo: base comune degli utenti autenticabili; gestisce credenziali e il flusso "credenziali provvisorie → personali".
- `Utente(username, password)` (protetto)
- `verificaPassword(tentativo)`
- `cambiaPassword(nuovaPassword)`
- `confermaCredenzialiPersonali(nuovoUsername, nuovaPassword)`

Relazioni: superclasse di `Configuratore` e `Fruitore`. Identità (equals/hashCode) per username.

### Configuratore
Ruolo: utente che amministra configurazione, categorie e proposte.
- `Configuratore(username, password)`

Relazioni: eredita da `Utente`.

### Fruitore
Ruolo: utente che consulta la bacheca, aderisce/disdice e riceve notifiche.
- `Fruitore(username, password)`
- `aggiorna(proposta, notifica)` — riceve la notifica e la deposita nel proprio spazio personale

Relazioni: eredita da `Utente`; **implementa** `OsservatoreProposta`; **compone** `SpazioPersonale` (1 → 1).

### OsservatoreProposta (interfaccia)
Ruolo: contratto Observer per i cambi di stato di una Proposta.
- `aggiorna(proposta, notifica)`

Relazioni: implementata da `Fruitore`; usata da `Proposta` (lista di osservatori).

### SpazioPersonale
Ruolo: contenitore delle notifiche ricevute da un fruitore.
- `aggiungiNotifica(notifica)`
- `rimuoviNotifica(indice)`

Relazioni: **compone** `Notifica` (1 → 0..*); parte di `Fruitore`.

### Notifica
Ruolo: valore immutabile che descrive una transizione di stato di una proposta (con snapshot dei valori come promemoria).
- `Notifica(idProposta, nomeCategoria, nuovoStato, data, snapshotValori)`

Relazioni: **usa** `StatoProposta`; riferisce la `Proposta` per id (nessuna reference diretta, per evitare cicli di serializzazione).

### Campo
Ruolo: descrittore di un campo di categoria: nome, tipo, obbligatorietà. Immutabile.
- `Campo(nome, tipo, obbligatorio)`

Relazioni: **usa** `TipoCampo`; contenuto in `ConfigurazioneGlobale` e `Categoria`. Identità per nome.

### TipoCampo (enum)
Ruolo: tipi di dato ammessi per un campo: STRINGA, INTERO, DECIMALE, DATA, ORA, BOOLEANO.

### ConfigurazioneGlobale
Ruolo: definizione dei campi base (fissabili una sola volta) e dei campi comuni a tutte le categorie.
- `fissaCampiBase(campi)`
- `impostaCampiComuni(campi)` / `aggiungiCampoComune(campo)` / `rimuoviCampoComune(nome)`

Relazioni: **compone** `Campo` (1 → 0..* base, 1 → 0..* comuni).

### Categoria
Ruolo: categoria di proposte; aggrega i campi specifici che si sommano a base e comuni.
- `Categoria(nome)`
- `aggiungiCampoSpecifico(campo, configurazioneGlobale)` / `rimuoviCampoSpecifico(nome)`
- `getTuttiICampi(configurazioneGlobale)` — base + comuni + specifici

Relazioni: **compone** `Campo` (specifici, 1 → 0..*); **usa** `ConfigurazioneGlobale` (parametro, mai campo interno). Identità per nome.

### StatoProposta (enum)
Ruolo: ciclo di vita della proposta: VALIDA, APERTA, CONFERMATA, ANNULLATA, CONCLUSA, RITIRATA (le ultime quattro terminali o quasi: da RITIRATA/ANNULLATA/CONCLUSA nessuna uscita).

### Proposta
Ruolo: cuore del dominio; proposta di iniziativa con valori dei campi, stato, aderenti, storico e osservatori.
- `Proposta(categoria, valori)`
- `valida(fornitoreTempo, configurazioneGlobale)` — vincoli su obbligatori e date → stato VALIDA
- `vincoliViolati(fornitoreTempo, configurazioneGlobale)` — elenco leggibile dei vincoli falliti (V5)
- `marcaPubblicata(dataPubblicazione)` — VALIDA → APERTA
- `aderisci(fruitore, fornitoreTempo)` — iscrizione (entro termine, se c'è posto) + registrazione osservatore
- `disdici(fruitore, fornitoreTempo)` — revoca dell'iscrizione (solo APERTA, entro termine)
- `ritira(fornitoreTempo)` — {APERTA, CONFERMATA} → RITIRATA, prima del giorno evento
- `elaboraTransizione(fornitoreTempo)` — motore: APERTA → CONFERMATA/ANNULLATA, CONFERMATA → CONCLUSA
- `registraOsservatore(osservatore)`
- record annidato `VoceStorico(stato, data)` — voce dello storico append-only

Relazioni: **aggrega** `Categoria` (1 → 1); **compone** `VoceStorico` (storico, 1 → 0..*); **usa** `StatoProposta`, `FornitoreTempo`, `ConfigurazioneGlobale`; notifica gli `OsservatoreProposta` registrati creando `Notifica`. Gli aderenti sono username (String), non oggetti `Fruitore`.

### Bacheca
Ruolo: vista di dominio delle sole proposte APERTE, raggruppabili per categoria; non persiste nulla.
- `Bacheca(proposteAperte)`
- `tutte()` / `raggruppatePerCategoria()` / `isVuota()`

Relazioni: **aggrega** `Proposta` (0..*).

---

## Package `util`

### FornitoreTempo (interfaccia)
Ruolo: astrazione del tempo; il dominio non chiama mai direttamente l'orologio di sistema.
- `oggi()` / `adesso()`

Relazioni: implementata da `TempoReale` e `TempoSimulato`; usata da `Proposta`, dai controller e dal motore transizioni.

### TempoReale
Ruolo: implementazione di produzione (orologio di sistema).

Relazioni: **implementa** `FornitoreTempo`.

### TempoSimulato
Ruolo: implementazione con data configurabile, per test e modalità dimostrativa.
- `TempoSimulato(dataIniziale)`
- `avanzaGiorni(n)` / `avanzaOre(n)` / `impostaData(nuovaData)`

Relazioni: **implementa** `FornitoreTempo`.

---

## Package `persistence`

### RepositoryJson\<T\>
Ruolo: repository generico lista-di-T ↔ file JSON (Gson, pretty printing, adapter per LocalDate).
- `RepositoryJson(percorsoFile, tipoElemento)`
- `salva(elementi)` / `carica()`

Relazioni: **usa** `AdapterLocalDate`; usato da tutti gli archivi "a lista".

### RepositoryJsonSingolo\<T\>
Ruolo: come `RepositoryJson`, ma per un singolo oggetto (configurazione, data simulata).
- `salva(elemento)` / `carica()` → Optional

Relazioni: **usa** `AdapterLocalDate`; usato da `ArchivioConfigurazione` e `ArchivioTempoSimulato`.

### AdapterLocalDate
Ruolo: TypeAdapter Gson per LocalDate in formato ISO-8601.

Relazioni: estende `TypeAdapter<LocalDate>` (Gson).

### ArchivioConfigurazione
Ruolo: persistenza della `ConfigurazioneGlobale` su `data/configurazione.json`.
- `carica()` / `salva(configurazione)`

Relazioni: **compone** `RepositoryJsonSingolo<ConfigurazioneGlobale>`.

### ArchivioCategorie
Ruolo: persistenza delle categorie su `data/categorie.json`.
- `caricaTutte()` / `salvaTutte(categorie)`

Relazioni: **compone** `RepositoryJson<Categoria>`.

### ArchivioConfiguratori
Ruolo: persistenza dei configuratori su `data/configuratori.json`.
- `caricaTutti()` / `salvaTutti(configuratori)`

Relazioni: **compone** `RepositoryJson<Configuratore>`.

### ArchivioFruitori
Ruolo: persistenza dei fruitori (con spazio personale e notifiche) su `data/fruitori.json`.
- `caricaTutti()` / `salvaTutti(fruitori)`

Relazioni: **compone** `RepositoryJson<Fruitore>`.

### ArchivioProposte
Ruolo: persistenza delle proposte pubblicate su `data/proposte.json` (filtra le bozze VALIDA/non validate) e sede del motore transizioni globale.
- `caricaTutte()` / `salvaTutte(proposte)`
- `elaboraTransizioni(proposte, fornitoreTempo)` — applica `elaboraTransizione` a ogni proposta e persiste

Relazioni: **compone** `RepositoryJson<Proposta>`; **usa** `FornitoreTempo`, `StatoProposta`.

### ArchivioTempoSimulato
Ruolo: persistenza della data simulata su `data/tempo.json` (file presente = tempo simulato; assente = tempo reale).
- `caricaDataSimulata()` / `salvaDataSimulata(data)` / `rimuoviDataSimulata()`

Relazioni: **compone** `RepositoryJsonSingolo<LocalDate>`.

---

## Package `controller`

### ControllerConfiguratore
Ruolo: orchestratore delle operazioni del configuratore; coordina model e persistence, nessun I/O console.
- `ControllerConfiguratore(archivi..., fornitoreTempo)` — carica tutto e ricollega gli osservatori
- `primoAccesso(username, password)` / `confermaCredenzialiPersonali(...)` / `login(username, password)`
- `fissaCampiBase(campi)` / `impostaCampiComuni(campi)`
- `creaCategoria(nome)` / `rimuoviCategoria(nome)` / `aggiungiCampoSpecifico(nomeCategoria, campo)` / `rimuoviCampoSpecifico(nomeCategoria, nomeCampo)`
- `creaProposta(categoria, valori)` / `richiediPubblicazione(proposta)` / `getBacheca()` / `getArchivioProposte()`
- `getProposteRitirabili()` / `ritiraProposta(proposta)` — persiste stato e notifiche
- `impostaDataSimulata(data)` / `rimuoviDataSimulata()`
- `descriviVincoliViolati(proposta)` — diagnostica per l'import batch (V5)

Relazioni: **aggrega** tutti gli archivi di `persistence` e `FornitoreTempo`; **usa** `ConfigurazioneGlobale`, `Categoria`, `Proposta`, `Bacheca`, `Configuratore`, `Fruitore`.

### ControllerFruitore
Ruolo: orchestratore delle operazioni del fruitore; all'avvio ricollega gli osservatori e fa girare il motore transizioni.
- `ControllerFruitore(archivi..., fornitoreTempo)`
- `inizializzaSessione()` — ricollega osservatori + motore transizioni + persistenza notifiche
- `registra(username, password)` / `login(username, password)`
- `getBacheca()` / `aderisci(proposta)` / `disdici(proposta)`
- `getSpazioPersonale()` / `cancellaNotifica(indice)` / `getProposteAderite()`

Relazioni: **aggrega** `ArchivioFruitori`, `ArchivioConfiguratori`, `ArchivioProposte`, `FornitoreTempo`; **usa** `Fruitore`, `Proposta`, `Bacheca`, `SpazioPersonale`.

### ImportatoreBatch
Ruolo: import batch V5 di categorie, campi e proposte da file JSON; politica continua-e-riporta, riusa integralmente i metodi del controller (nessun accesso diretto ad archivi o regole di dominio).
- `ImportatoreBatch(controller)`
- `importa(percorsoFile)` → `EsitoImport`
- record annidato `EsitoImport(categorieImportate, categorieScartate, proposteImportate, proposteScartate, avvisi)`

Relazioni: **aggrega** `ControllerConfiguratore`; **usa** `Campo`, `TipoCampo`, `Categoria`, `Proposta`, `StatoProposta`, Gson (parsing ad albero).

---

## Package `view`

### ViewConfiguratore
Ruolo: unico punto di I/O console del ruolo configuratore; menu a 15 voci + autenticazione.
- `ViewConfiguratore(controller, scanner)`
- `avvia()` — primo accesso/login + ciclo del menu

Relazioni: **aggrega** `ControllerConfiguratore`; **usa** `ImportatoreBatch` (voce 15).

### ViewFruitore
Ruolo: unico punto di I/O console del ruolo fruitore; registrazione/login + menu a 5 voci.
- `ViewFruitore(controller, scanner)`
- `avvia()` — inizializza la sessione, gestisce accesso e ciclo del menu

Relazioni: **aggrega** `ControllerFruitore`.

---

## Root

### Main
Ruolo: entry point; istanzia archivi, sceglie `TempoReale`/`TempoSimulato` in base a `data/tempo.json`, fa girare il motore transizioni, chiede il ruolo e avvia la view corrispondente.
- `main(args)`

Relazioni: **usa** tutti gli archivi, i due controller, le due view, `FornitoreTempo`.

---

# Casi d'uso del sistema completo (V1–V5)

Elenco piatto per attore, per il diagramma dei casi d'uso globale.

## Attore: Configuratore

- Effettuare il primo accesso (credenziali predefinite → credenziali personali)
- Autenticarsi (login)
- Fissare i campi base (una sola volta)
- Fissare/modificare i campi comuni
- Creare una categoria
- Rimuovere una categoria
- Aggiungere un campo specifico a una categoria
- Rimuovere un campo specifico da una categoria
- Visualizzare le categorie e i loro campi
- Creare una proposta (con validazione dei vincoli)
- Pubblicare una proposta valida in bacheca
- Visualizzare la bacheca
- Visualizzare l'archivio delle proposte (storico degli stati e aderenti)
- Ritirare una proposta (APERTA o CONFERMATA, prima del giorno dell'evento)
- Impostare/modificare la data simulata
- Rimuovere la data simulata (ritorno al tempo reale)
- Importare categorie, campi e proposte da file in modalità batch

## Attore: Fruitore

- Registrarsi (username unico nel sistema)
- Autenticarsi (login)
- Visualizzare la bacheca delle proposte aperte
- Aderire a una proposta aperta
- Disdire l'iscrizione a una proposta (entro il termine di iscrizione)
- Consultare lo spazio personale (notifiche e proposte a cui si è iscritti)
- Ricevere le notifiche di cambio stato delle proposte a cui si è iscritti (con promemoria per le confermate)
- Cancellare una notifica dallo spazio personale

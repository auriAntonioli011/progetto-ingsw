# Registro delle decisioni di progetto

Questo file documenta le scelte implementative del progetto.

## Stato del progetto

### V1 — completata e taggata (`git tag V1`)

**Classi esistenti:**

| Package | Classi |
|---|---|
| `model` | `Campo`, `TipoCampo` (enum), `ConfigurazioneGlobale`, `Categoria`, `Utente` (astratta), `Configuratore` |
| `persistence` | `RepositoryJson<T>` (liste), `RepositoryJsonSingolo<T>` (oggetto singolo), `AdapterLocalDate`, `ArchivioConfigurazione`, `ArchivioCategorie`, `ArchivioConfiguratori` |
| `controller` | `ControllerConfiguratore` (con `isPrimoAccesso()`) |
| `view` | `ViewConfiguratore` (unica classe autorizzata a I/O console) |
| root | `Main` — istanzia archivi, controller, view e chiama `avvia()` |

**Testato manualmente end-to-end (sessione del 2026-07-03):**
- Primo accesso → scelta credenziali personali → login successivo → persistenza confermata tra riavvii
- Fissa campi base; tentativo di rifissarli → `[AVVISO]` pulito, nessuno stack trace
- Campi comuni con conflitto di nome verso un campo base → `[ERRORE]` leggibile
- Crea categoria; tentativo di creare categoria con nome duplicato → `[ERRORE]` pulito
- Aggiungi campo specifico a categoria
- Rimuovi campo specifico da categoria
- Rimuovi categoria
- Visualizza categorie e campi (base + comuni + specifici in ordine)

**Non ancora iniziato:**
- Casi d'uso testuali / UML V1
- Diagramma delle classi V1
- V2: creazione proposte, validazione, bacheca, pubblicazione

### Registro delle scelte implementative (da NON rimettere in discussione senza motivo)

- **Credenziali predefinite libere** — non cablate nel codice; l'utente le sceglie al primo avvio.
- **Gson bypassa i costruttori di validazione** in deserializzazione (accettato per V1: i file JSON sono sempre scritti dall'app stessa).
- **`ConfigurazioneGlobale` passata come parametro** ai metodi di `Categoria`, mai salvata come campo interno (basso accoppiamento).
- **`RepositoryJsonSingolo<T>`** creato appositamente per oggetti singoli (`ConfigurazioneGlobale`) invece di riusare `RepositoryJson<T>` con liste-wrapper artificiose.

---

## Project overview

Java 21 Maven project for a Software Engineering course at the University of Brescia (`it.unibs.ingsw`). The entry point is `it.unibs.ingsw.Main`. Gson 2.11.0 is available as a dependency for JSON serialization/deserialization.

## Build and run

```bash
# Compile
mvn compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Package fat JAR (via maven-shade-plugin)
mvn package

# Run the packaged JAR
java -jar target/progetto-ingsw-1.0-SNAPSHOT.jar
```

## Package structure (MVC)

Root: `it.unibs.ingsw`, with sub-packages:

| Package | Responsabilità |
|---|---|
| `model` | Dominio puro: entità, regole di business, stato. **Mai** `System.out`/`System.in` né formattazione di stringhe per l'utente. |
| `persistence` | Repository JSON tramite Gson, un file per aggregato (es. `categorie.json`, `proposte.json`). |
| `controller` | Coordina model e view; gestisce il flusso applicativo. |
| `view` | **Unico** punto di I/O console (`System.out`/`Scanner`). Nessuna logica di dominio. |
| `util` | Classi di supporto trasversali (es. validatori, formattatori). |

## Naming conventions

- Nomi di classi, metodi e variabili **in italiano**: `Campo`, `Categoria`, `Proposta`, `Fruitore`, `Configuratore`, ecc.
- Interfacce con nome descrittivo del ruolo: `OsservatoreProposta`, `FornitoreTempo`.
- Costanti in `UPPER_SNAKE_CASE` italiano dove applicabile.

## Pattern architetturali

### Observer — notifiche sullo stato di Proposta
`Fruitore` implementa `OsservatoreProposta`. `Proposta` mantiene la lista degli osservatori e la notifica ad ogni cambio di stato. Definire l'interfaccia in `model`.

### Astrazione del tempo — FornitoreTempo
**Non usare mai `LocalDate.now()` direttamente nel dominio.** Iniettare sempre `FornitoreTempo`:
- `TempoReale` — delega a `LocalDate.now()`, usata in produzione.
- `TempoSimulato` — data configurabile, usata nei test e nella modalità configuratore.

## Contratti sui metodi del model

Ogni costruttore e metodo pubblico del `model` deve documentare i contratti con commenti inline:

```java
// pre:  categoria != null && !campi.isEmpty()
// post: this.stato == StatoProposta.APERTA
// inv:  campi.size() >= categoria.getCampiObbligatori().size()
```

Non usare Javadoc esteso; i tre tag `pre`/`post`/`inv` sono sufficienti.

## Sviluppo incrementale per versioni

Il progetto evolve in **5 versioni** (V1…V5), ognuna estende la precedente **senza modificare** ciò che era già funzionante:

- Al completamento di ogni versione creare un tag Git: `git tag V1`, `git tag V2`, …
- Ogni versione deve essere compilabile e testabile autonomamente al proprio tag.
- Non rimuovere né alterare comportamenti già taggati; aggiungere solo nuove classi/metodi.

## Key dependencies

- **Gson 2.11.0** — serializzazione/deserializzazione JSON nei repository di `persistence`.
- Il shade plugin produce un fat JAR eseguibile in `target/progetto-ingsw-1.0-SNAPSHOT.jar`.

### V2 — completata e taggata (`git tag v2`)

**Classi aggiunte:**

| Package | Classi |
|---|---|
| `model` | `StatoProposta` (enum: VALIDA, APERTA), `Proposta`, `Bacheca` |
| `persistence` | `ArchivioProposte` (su `data/proposte.json`, persiste solo proposte APERTA) |

**Classi estese (solo aggiunte, nessuna modifica a comportamento V1):**
- `ControllerConfiguratore`: costruttore esteso con `ArchivioProposte` + `FornitoreTempo`; nuovi metodi `creaProposta`, `richiediPubblicazione`, `getProposteValide`, `getBacheca`
- `ViewConfiguratore`: nuove voci menu 8 (crea proposta), 9 (pubblica), 10 (visualizza bacheca)
- `Main`: istanzia `ArchivioProposte` e `new TempoReale()`, li passa al controller

**Testato manualmente end-to-end (sessione del 2026-07-04):**
- Proposta valida creata e pubblicata → stato APERTA, compare in bacheca per categoria, persiste tra riavvii (`data/proposte.json`)
- Proposta con campo obbligatorio mancante → resta non valida, non pubblicabile
- Proposta con termine iscrizione a meno di 2 giorni dalla data evento → resta non valida (vincolo date rispettato)

**Non ancora iniziato:**
- Casi d'uso testuali / UML V2
- Diagramma delle classi V2
- V3: accesso fruitore, iscrizioni, motore transizioni temporali, Observer/notifiche

### Registro delle scelte implementative — aggiunte V2

- **`Proposta.valida()` identifica i campi data per nome esatto**: le costanti `CAMPO_TERMINE_ISCRIZIONE`, `CAMPO_DATA_EVENTO`, `CAMPO_DATA_CONCLUSIVA` devono coincidere case-sensitive con i nomi ufficiali dello spec ("Termine ultimo di iscrizione", "Data", "Data conclusiva"). **Bug reale riscontrato e corretto**: la prima implementazione usava nomi diversi ("termine iscrizione", "data evento", "dataConclusiva"), causando proposte sempre non valide senza errore esplicito. Corretto il 2026-07-04.
- **Stato iniziale di `Proposta` = `null`** (non un terzo valore enum): rappresenta "non ancora validata". Scelta minimale per non anticipare astrazioni non richieste in V1/V2; l'invariante è documentata nel Javadoc di `Proposta`. Da rivalutare se in V3/V4 la gestione stati diventa più complessa.
- **`ArchivioProposte.salvaTutte` filtra internamente per stato == APERTA**: garantisce che una proposta VALIDA ma non pubblicata non venga mai persistita, anche se il controller passasse l'intera lista per errore (requisito esplicito V2, difesa a livello di persistenza oltre che di controller).
- **`Proposta.valida()` riceve `ConfigurazioneGlobale` come parametro**, mai salvata come campo — stessa convenzione già adottata da `Categoria.getTuttiICampi`.

### V3 — completata e taggata (`git tag v3`)

**Classi aggiunte:**

| Package | Classi |
|---|---|
| `model` | `OsservatoreProposta` (interface), `Notifica`, `SpazioPersonale`, `Fruitore`, `Proposta.VoceStorico` (record annidato) |
| `persistence` | `ArchivioFruitori` (su `data/fruitori.json`), `ArchivioTempoSimulato` (su `data/tempo.json`) |
| `controller` | `ControllerFruitore` |
| `view` | `ViewFruitore` |

**Classi estese (solo aggiunte, nessuna modifica a comportamento V1/V2):**
- `StatoProposta`: aggiunti `CONFERMATA`, `ANNULLATA`, `CONCLUSA` (V2: solo `VALIDA`, `APERTA`)
- `Proposta`: `id` (UUID), `aderenti` (List<String> usernames), `storico` (List<VoceStorico>), `osservatori` (transient), `aderisci(Fruitore, FornitoreTempo)`, `elaboraTransizione(FornitoreTempo)`, `registraOsservatore(...)`, `getId()`, `getAderenti()`, `getStorico()`, costante `CAMPO_NUMERO_PARTECIPANTI`; `marcaPubblicata` ora appende alla storia
- `ArchivioProposte`: filtro persistenza esteso da solo `APERTA` a `{APERTA, CONFERMATA, ANNULLATA, CONCLUSA}`; nuovo `elaboraTransizioni(proposte, tempo)` (motore transizioni globale)
- `ControllerConfiguratore`: costruttore + `ArchivioTempoSimulato`; nuovi `getArchivioProposte`, `getDataCorrente`, `isTempoSimulato`, `impostaDataSimulata`, `rimuoviDataSimulata`
- `ViewConfiguratore`: voci menu 11 (visualizza archivio), 12 (imposta data simulata), 13 (rimuovi data simulata)
- `Main`: istanzia `ArchivioFruitori` + `ArchivioTempoSimulato`; sceglie `TempoSimulato`/`TempoReale` in base al file; crea `ControllerFruitore` e ne invoca `inizializzaSessione()` PRIMA del prompt scelta ruolo; nuovo menu iniziale Configuratore/Fruitore
- Tutti gli archivi (`ArchivioConfigurazione`, `ArchivioCategorie`, `ArchivioConfiguratori`, `ArchivioProposte`, `ArchivioFruitori`, `ArchivioTempoSimulato`): overload di costruttore che accetta un `Path` alternativo, per permettere l'isolamento dei test via `@TempDir` senza toccare `data/` reale

**Testato con suite JUnit 5 (sessione del 2026-07-06, 20 test verdi):**
- `ChecklistV3Test` (14 test) copre i punti #1–#12 della checklist V3
- `RegressioneV1V2Test` (6 test) copre il punto #13 (regressione V1/V2)

Dettaglio esiti (tutti PASS):
- #1 registrazione fruitore con username in uso da configuratore → rifiutata
- #2 fruitore aderisce → compare tra aderenti
- #3 doppia iscrizione stesso fruitore → rifiutata
- #4 iscrizione oltre termine iscrizione → rifiutata
- #5 proposta piena → nuova iscrizione rifiutata
- #6 APERTA → CONFERMATA (piena) con snapshot promemoria completo (Data, Ora, Luogo, Quota) verificato non-vuoto/non-null; APERTA → ANNULLATA (non piena)
- #7 CONFERMATA → CONCLUSA oltre data conclusiva
- #8 storico append-only con date coincidenti con `TempoSimulato` (APERTA, CONFERMATA, CONCLUSA nell'ordine)
- #9 notifiche persistite roundtrip su `ArchivioFruitori`, snapshot preservato dopo deserializzazione
- #10 `SpazioPersonale.rimuoviNotifica(idx)` rimuove solo la notifica in quella posizione; indice invalido → `IndexOutOfBoundsException` e lista intatta
- #11 archivio proposte post-transizione mostra aderenti "congelati" (stessa lista di quando la proposta era APERTA) + storico corretto
- #12 `inizializzaSessione()` persiste notifiche per TUTTI gli aderenti, non solo per chi si logga; sessione successiva di altro fruitore trova la propria notifica già in `fruitori.json`
- #13 regressione V1/V2: primo accesso configuratore + login, campi base/comuni, categorie e campi specifici, ciclo proposta valida→APERTA→bacheca→riavvio, proposta con termine iscrizione troppo vicino alla data evento → non valida, campo obbligatorio mancante → non valida

**Non ancora iniziato:**
- Casi d'uso testuali / UML V3
- Diagramma delle classi V3
- V4: gestione fattori di conversione tra categorie, calcolo compatibilità scambio

### Registro delle scelte implementative — aggiunte V3

- **`Proposta.aderenti` è `List<String>` (usernames), non `List<Fruitore>`.** Scelta motivata dalla necessità di evitare (i) cicli di serializzazione JSON (`Fruitore.spazioPersonale.notifiche` non contiene ref a Proposta ma se la persistenza fosse `List<Fruitore>` in Proposta il grafo diventerebbe circolare via osservatori) e (ii) duplicazione degli oggetti Fruitore su disco (una volta in `fruitori.json`, un'altra annidati in `proposte.json` — problema di identità/hashCode dopo reload). Il collegamento object→object necessario alla notifica Observer è mantenuto tramite una lista `transient List<OsservatoreProposta> osservatori`, che `ControllerFruitore.inizializzaSessione()` ripopola all'avvio matchando gli usernames contro `ArchivioFruitori`.
- **`Notifica` include `idProposta` (String/UUID) + `snapshotValori` (Map immutabile).** Gap identificato in review: la Notifica originale conteneva solo `(nomeCategoria, nuovoStato, data)`, insufficiente per identificare univocamente la Proposta se un fruitore è iscritto a più proposte della stessa categoria, e priva del "promemoria" (data-evento, ora, luogo, quota) richiesto dalla spec per CONFERMATA. Fix: id stabile per l'identità + `Map.copyOf` dei valori al momento della transizione. Snapshot, non reference: modifiche successive alla Proposta non alterano notifiche già recapitate.
- **`Proposta.id` generato lazy** dal costruttore e da `assicuraStrutture()`. Il costruttore inizializza sempre l'id con `UUID.randomUUID().toString()`; Gson bypassa il costruttore in deserializzazione, quindi per proposte V2 legacy (senza il campo `id` nel JSON) l'id resta `null` al load. `assicuraStrutture()` — chiamato all'inizio di ogni metodo mutante inclusi `elaboraTransizione` e `aderisci` — ripara: il primo giro del motore transizioni post-upgrade genera l'id e `salvaTutte` lo persiste. Non serve migrazione esplicita.
- **Semantica del tempo simulato: cambio immediato in sessione, transizioni solo al riavvio.** Il `FornitoreTempo` è un'unica istanza condivisa tra `ControllerConfiguratore` e `ControllerFruitore` (creata in `Main` leggendo `data/tempo.json` all'avvio). `ControllerConfiguratore.impostaDataSimulata(...)` persiste sul file E, se il fornitore corrente è `TempoSimulato`, chiama `impostaData(...)` in-place (effetto immediato sulle successive letture di `fornitoreTempo.oggi()`). MA il motore transizioni (`ArchivioProposte.elaboraTransizioni`) gira una sola volta per processo, dentro `ControllerFruitore.inizializzaSessione()` invocato da `Main` prima del prompt ruolo. Quindi i cambi di stato di Proposta (APERTA→CONFERMATA/ANNULLATA, CONFERMATA→CONCLUSA) diventano visibili solo al RIAVVIO successivo, non a metà sessione. È un vincolo di design, non un bug: rispecchia il fatto che il motore è un'operazione batch di allineamento all'avvio.
- **Motore transizioni prima della scelta ruolo.** `Main` istanzia `ControllerFruitore` (con caricamento fruitori) prima del prompt Configuratore/Fruitore e invoca `inizializzaSessione()` a monte. Effetto: le notifiche per transizioni di stato vengono depositate nei `SpazioPersonale` dei fruitori e persistite in `fruitori.json` indipendentemente da chi (o se qualcuno) si logga. Se poi l'utente sceglie Configuratore, `ControllerConfiguratore` viene istanziato DOPO — legge quindi da `data/proposte.json` già aggiornato. Test #12 verifica esplicitamente questo invariante.
- **Overload di costruttore su tutti gli archivi che accetta un `Path` alternativo.** Aggiunto in V3 esclusivamente per abilitare test isolati con `@TempDir`; il costruttore no-arg legacy (che punta a `data/`) resta il default per la produzione. Modifica non invasiva, retro-compatibile con V1/V2.
- **`Fruitore` bypassa il flusso "credenziali provvisorie".** Il configuratore ha un primo accesso a due fasi (credenziali predefinite → credenziali personali); il fruitore invece si registra scegliendo direttamente le credenziali definitive. `ControllerFruitore.registra(...)` invoca `Fruitore.confermaCredenzialiPersonali(...)` con gli stessi valori appena dopo la costruzione per portare `credenzialiProvvisorie` a `false`, cosicché downstream (equals/hashCode by username) sia stabile.

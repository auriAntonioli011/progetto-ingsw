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
- V4: disdetta iscrizione (fruitore) + ritiro proposta (configuratore)

### Registro delle scelte implementative — aggiunte V3

- **`Proposta.aderenti` è `List<String>` (usernames), non `List<Fruitore>`.** Scelta motivata dalla necessità di evitare (i) cicli di serializzazione JSON (`Fruitore.spazioPersonale.notifiche` non contiene ref a Proposta ma se la persistenza fosse `List<Fruitore>` in Proposta il grafo diventerebbe circolare via osservatori) e (ii) duplicazione degli oggetti Fruitore su disco (una volta in `fruitori.json`, un'altra annidati in `proposte.json` — problema di identità/hashCode dopo reload). Il collegamento object→object necessario alla notifica Observer è mantenuto tramite una lista `transient List<OsservatoreProposta> osservatori`, che `ControllerFruitore.inizializzaSessione()` ripopola all'avvio matchando gli usernames contro `ArchivioFruitori`.
- **`Notifica` include `idProposta` (String/UUID) + `snapshotValori` (Map immutabile).** Gap identificato in review: la Notifica originale conteneva solo `(nomeCategoria, nuovoStato, data)`, insufficiente per identificare univocamente la Proposta se un fruitore è iscritto a più proposte della stessa categoria, e priva del "promemoria" (data-evento, ora, luogo, quota) richiesto dalla spec per CONFERMATA. Fix: id stabile per l'identità + `Map.copyOf` dei valori al momento della transizione. Snapshot, non reference: modifiche successive alla Proposta non alterano notifiche già recapitate.
- **`Proposta.id` generato lazy** dal costruttore e da `assicuraStrutture()`. Il costruttore inizializza sempre l'id con `UUID.randomUUID().toString()`; Gson bypassa il costruttore in deserializzazione, quindi per proposte V2 legacy (senza il campo `id` nel JSON) l'id resta `null` al load. `assicuraStrutture()` — chiamato all'inizio di ogni metodo mutante inclusi `elaboraTransizione` e `aderisci` — ripara: il primo giro del motore transizioni post-upgrade genera l'id e `salvaTutte` lo persiste. Non serve migrazione esplicita.
- **Semantica del tempo simulato: cambio immediato in sessione, transizioni solo al riavvio.** Il `FornitoreTempo` è un'unica istanza condivisa tra `ControllerConfiguratore` e `ControllerFruitore` (creata in `Main` leggendo `data/tempo.json` all'avvio). `ControllerConfiguratore.impostaDataSimulata(...)` persiste sul file E, se il fornitore corrente è `TempoSimulato`, chiama `impostaData(...)` in-place (effetto immediato sulle successive letture di `fornitoreTempo.oggi()`). MA il motore transizioni (`ArchivioProposte.elaboraTransizioni`) gira una sola volta per processo, dentro `ControllerFruitore.inizializzaSessione()` invocato da `Main` prima del prompt ruolo. Quindi i cambi di stato di Proposta (APERTA→CONFERMATA/ANNULLATA, CONFERMATA→CONCLUSA) diventano visibili solo al RIAVVIO successivo, non a metà sessione. È un vincolo di design, non un bug: rispecchia il fatto che il motore è un'operazione batch di allineamento all'avvio.
- **Motore transizioni prima della scelta ruolo.** `Main` istanzia `ControllerFruitore` (con caricamento fruitori) prima del prompt Configuratore/Fruitore e invoca `inizializzaSessione()` a monte. Effetto: le notifiche per transizioni di stato vengono depositate nei `SpazioPersonale` dei fruitori e persistite in `fruitori.json` indipendentemente da chi (o se qualcuno) si logga. Se poi l'utente sceglie Configuratore, `ControllerConfiguratore` viene istanziato DOPO — legge quindi da `data/proposte.json` già aggiornato. Test #12 verifica esplicitamente questo invariante.
- **Overload di costruttore su tutti gli archivi che accetta un `Path` alternativo.** Aggiunto in V3 esclusivamente per abilitare test isolati con `@TempDir`; il costruttore no-arg legacy (che punta a `data/`) resta il default per la produzione. Modifica non invasiva, retro-compatibile con V1/V2.
- **`Fruitore` bypassa il flusso "credenziali provvisorie".** Il configuratore ha un primo accesso a due fasi (credenziali predefinite → credenziali personali); il fruitore invece si registra scegliendo direttamente le credenziali definitive. `ControllerFruitore.registra(...)` invoca `Fruitore.confermaCredenzialiPersonali(...)` con gli stessi valori appena dopo la costruzione per portare `credenzialiProvvisorie` a `false`, cosicché downstream (equals/hashCode by username) sia stabile.

### V4 — completata e taggata (`git tag v4`)

**Classi aggiunte:** nessuna nuova classe — V4 è deliberatamente non invasiva, si limita a estendere `StatoProposta`, `Proposta` e i controller.

**Classi estese (solo aggiunte, nessuna modifica a comportamento V1/V2/V3):**
- `StatoProposta`: aggiunto `RITIRATA` (V3: VALIDA, APERTA, CONFERMATA, ANNULLATA, CONCLUSA)
- `Proposta`: nuovi `disdici(Fruitore, FornitoreTempo)` e `ritira(FornitoreTempo)`; il metodo privato `applicaTransizione(StatoProposta, LocalDate)` già introdotto in V3 viene riusato anche dal ritiro (medesimo protocollo "cambia stato + appende storico + snapshot + notifica Observer")
- `ControllerFruitore`: nuovo `disdici(Proposta)` con persistenza di `proposte.json` (aderenti aggiornati)
- `ControllerConfiguratore`: nuovi `getProposteRitirabili()` e `ritiraProposta(Proposta)`; costruttore esteso con `ArchivioFruitori` (necessario per persistere le notifiche di ritiro nei `SpazioPersonale` dei fruitori aderenti); ricollegamento osservatori accodato al costruttore
- `ViewFruitore`: nuova voce menu 5 ("Disdici iscrizione a una proposta")
- `ViewConfiguratore`: nuova voce menu 14 ("Ritira una proposta (APERTA o CONFERMATA)")
- `Main`: costruzione `ControllerConfiguratore` aggiornata al nuovo costruttore (`ArchivioFruitori` passato in ingresso)

**Testato con suite JUnit 5 (sessione del 2026-07-06, 33 test verdi totali):**
- `ChecklistV4Test` (13 test) copre disdetta + ritiro + integrazione persistenza
- `ChecklistV3Test` (14) + `RegressioneV1V2Test` (6) restano verdi — regressione confermata

Dettaglio esiti V4 (tutti PASS):

*Disdetta:*
- disdetta entro il termine → aderenti torna vuoto; lo storico NON registra l'evento (la disdetta non è una transizione di `StatoProposta`)
- disdetta oltre il termine → `IllegalStateException`, aderenti invariati
- ri-iscrizione dopo disdetta consentita entro il termine
- `disdici` su proposta non più APERTA (es. dopo `ritira`) → rifiutata (interpretazione Nota 10)
- `disdici` di fruitore non iscritto → `IllegalStateException` "non risulta iscritto"

*Ritiro:*
- ritiro APERTA prima del giorno evento → stato `RITIRATA`, aderenti congelati, notifica recapitata a tutti gli aderenti, snapshot valori completo (Data, Ora, Luogo, Quota, ...)
- ritiro CONFERMATA prima del giorno evento → stato `RITIRATA`, aderente riceve in sequenza notifica CONFERMATA seguita da notifica RITIRATA
- ritiro il giorno stesso dell'evento (`oggi == Data`) → rifiutato
- ritiro su stato non ammesso (ANNULLATA, CONCLUSA) → rifiutato con motivazione esplicita
- storico dopo ritiro append-only: `[APERTA@t₀, CONFERMATA@t₁, RITIRATA@t₂]`
- `RITIRATA` è terminale: `elaboraTransizione` invocato successivamente è no-op (stato, storico, notifiche invariati)

*Integrazione persistenza:*
- `ControllerConfiguratore.ritiraProposta` persiste `proposte.json` (stato RITIRATA) E `fruitori.json` (notifiche depositate nei `SpazioPersonale`); rilettura dal disco conferma entrambe le scritture
- `ControllerFruitore.disdici` aggiorna `proposte.json` (aderenti vuoto dopo roundtrip)

**Non ancora iniziato:**
- Casi d'uso testuali / UML V4
- Diagramma delle classi V4
- V5: import batch di categorie, campi e proposte da file (formato JSON, riuso di quello già usato dalla persistenza)

### Registro delle scelte implementative — aggiunte V4

- **Semantica della disdetta: solo su APERTA + entro il "Termine ultimo di iscrizione".** Il testo del progetto parla di "iscrizione a una proposta aperta"; interpretiamo (Nota 10) che l'operazione simmetrica di disdetta si applichi solo quando `stato == APERTA` — dopo un ritiro o una conferma non ha senso rinunciare a un evento che non c'è più o che ha già raggiunto una forma vincolante. Il vincolo temporale è lo stesso di `aderisci`: `oggi <= termine`. La ri-iscrizione successiva è consentita finché entrambi i vincoli restano soddisfatti. Test `disdici_su_stato_non_aperta_rifiutata` e `ri_iscrizione_dopo_disdetta` verificano i due invarianti.

- **La disdetta NON è una transizione di stato: non appare nello storico.** `Proposta.storico` è alimentato solo dai cambi effettivi di `StatoProposta`. Rimuovere un fruitore dalla lista aderenti è una modifica di popolazione, non di stato — coerente con il fatto che la Proposta non "riparte" dopo una disdetta. Verificato in `disdetta_valida_entro_termine`.

- **Ritiro consentito fino alle 23:59 del giorno precedente la "Data" (`oggi.isBefore(dataEvento)`).** Traduzione operativa del requisito "fino al giorno prima dell'evento": a livello di `LocalDate` `isBefore` significa esattamente "giorno strettamente precedente". Il test `ritiro_giorno_evento_rifiutato` verifica il boundary: `oggi == dataEvento` → rifiuto. Applicabile solo a proposte APERTA o CONFERMATA (test `ritiro_stato_non_ammesso_rifiutato` copre ANNULLATA).

- **`RITIRATA` è terminale: aderenti congelati, storico chiuso, motore transizioni no-op.** Dopo il ritiro l'elenco aderenti resta invariato (stesso principio di CONFERMATA/ANNULLATA/CONCLUSA in V3), lo storico si arresta al `VoceStorico(RITIRATA, dataRitiro)` e `elaboraTransizione` non produce alcuna transizione in uscita. Verificato in `ritirata_terminale_per_motore_transizioni`.

- **`ritira` riusa il helper privato `applicaTransizione`.** Il ritiro non è una transizione automatica del motore, ma il protocollo "cambia stato + appende storico + costruisce `Notifica` con snapshot + notifica osservatori" è identico. `applicaTransizione(nuovoStato, oggi)` — già estratto in V3 per `elaboraTransizione` — viene invocato anche da `ritira`. Zero duplicazione del meccanismo Observer/Notifica.

- **`ControllerConfiguratore` ora dipende da `ArchivioFruitori` e ricollega gli osservatori nel costruttore.** In V3 solo `ControllerFruitore` faceva il ricollegamento — sufficiente perché lì avvenivano tutte le transizioni automatiche. In V4 il ritiro è azione del CONFIGURATORE: `ritiraProposta` deve depositare notifiche nei `SpazioPersonale` E persistere `fruitori.json`. Senza ricollegamento osservatori nel costruttore di `ControllerConfiguratore` le Proposta lì caricate avrebbero `osservatori` (transient) vuoto e le notifiche di ritiro si perderebbero silenziosamente. Fix motivato dal test di integrazione `ritira_via_controller_configuratore_persiste_notifiche`. Effetto collaterale: la firma del costruttore è cambiata (nuovo parametro `archivioFruitori`), `Main` è stato aggiornato di conseguenza. Nessun test V3 impattato.

- **`getProposteRitirabili()` filtra a livello di controller.** La view mostra all'utente solo scelte legittime (stato ∈ {APERTA, CONFERMATA} e `oggi < dataEvento`). `Proposta.ritira` fa comunque il double-check e lancia `IllegalStateException` — difesa a due livelli. Se il campo "Data" è assente o malformato la proposta viene omessa silenziosamente (fail-safe, coerente con la scelta V3 su `elaboraTransizione`).

### V5 — import batch, completata

**Classi aggiunte:**

| Package | Classi |
|---|---|
| `controller` | `ImportatoreBatch` (con record annidato `EsitoImport`) |

**Classi estese (solo aggiunte, nessuna modifica a comportamento V1–V4):**
- `Proposta`: nuovo metodo pubblico `vincoliViolati(FornitoreTempo, ConfigurazioneGlobale)` che restituisce l'elenco leggibile dei vincoli di validità violati; `valida()` ora vi delega (stesse regole per costruzione, esito invariato — verificato dalla regressione)
- `ControllerConfiguratore`: nuovo `descriviVincoliViolati(Proposta)` (diagnostica per l'import, delega a `Proposta.vincoliViolati` con il fornitoreTempo e la configurazione di sessione)
- `ViewConfiguratore`: voce menu 15 ("Importa da file") con stampa del riepilogo `EsitoImport`; la modalità interattiva resta identica

**Formato del file di import** (JSON, blocchi entrambi opzionali; stessi nomi campo, carattere per carattere, dello schema Gson già usato da `categorie.json` e dai valori di `proposte.json`):

```json
{
  "categorie": [ { "nome": "...", "campiSpecifici": [ { "nome": "...", "tipo": "...", "obbligatorio": true } ] } ],
  "proposte":  [ { "categoria": "...", "valori": { "Titolo": "...", "Termine ultimo di iscrizione": "...", ... } } ]
}
```

**Testato con suite JUnit 5 (sessione del 2026-07-09, 45 test verdi totali):**
- `ChecklistV5Test` (12 test): import pulito; proposta che referenzia una categoria dello stesso file; categoria duplicata skippata (nessun merge su quella esistente); campo specifico duplicato skippato con categoria comunque creata; tipo campo non riconosciuto skippato; proposta con categoria inesistente scartata; proposta con vincolo date violato scartata (avviso indica il vincolo); proposta con obbligatorio mancante scartata (avviso nomina il campo); file malformato gestito senza scritture; file inesistente gestito; elemento malformato isolato senza abortire l'import; persistenza roundtrip dopo "riavvio"
- `ChecklistV4Test` (13) + `ChecklistV3Test` (14) + `RegressioneV1V2Test` (6) restano verdi — regressione confermata, modalità interattiva invariata

### Registro delle scelte implementative — aggiunte V5

- **Le proposte importate valide vengono pubblicate automaticamente (stato APERTA).** Nel flusso interattivo V2 una proposta VALIDA resta bozza di sessione finché l'utente non chiede la pubblicazione (voce 9): ha senso perché esiste una sessione in cui l'utente può ancora decidere. In batch quel concetto non esiste — il file È la decisione del configuratore, e una VALIDA non pubblicata andrebbe persa alla chiusura del programma (le bozze non si persistono, invariante V2). Quindi: proposta valida → `richiediPubblicazione` immediata → APERTA in bacheca, persistita. Le proposte non valide vengono scartate con l'elenco dei vincoli falliti.
- **Ordine di elaborazione: prima tutte le categorie del file, poi tutte le proposte.** Così una proposta può referenziare una categoria importata nello stesso file (test `proposta_referenzia_categoria_stesso_file`).
- **Politica continua-e-riporta.** Ogni elemento (categoria, campo, proposta) è isolato in un proprio blocco try: un elemento malformato o non valido produce un avviso nel riepilogo e non aborta gli altri. Fa eccezione il file nel suo complesso: se è inesistente o sintatticamente malformato l'import termina con errore gestito e NESSUNA scrittura (il parsing ad albero avviene per intero prima di toccare qualunque archivio).
- **Politiche sui duplicati:** categoria con nome già esistente → intero elemento skippato con messaggio, nessun merge dei campi su quella presente; campo specifico duplicato (o in conflitto con base/comuni) → solo il campo è skippato, la categoria viene comunque creata; tipo campo non riconosciuto → campo skippato con messaggio che riporta il valore non valido.
- **Zero duplicazione di logica: l'importatore passa solo dai metodi del controller.** `creaCategoria`, `aggiungiCampoSpecifico`, `creaProposta` (che invoca `valida()` di V2) e `richiediPubblicazione`: vincoli, persistenza e messaggi d'errore sono per costruzione gli stessi della modalità interattiva. `ImportatoreBatch` non tocca mai archivi o regole di dominio direttamente; l'unico parsing che fa è la lettura difensiva dell'albero JSON (Gson `JsonParser`), necessaria per isolare gli elementi malformati — cosa impossibile deserializzando l'intero file in un colpo solo.
- **`Proposta.vincoliViolati` come unico punto dei vincoli (a)–(d).** Per motivare gli scarti ("quale vincolo ha fallito") serviva una diagnostica che `valida():boolean` non offre. Invece di duplicare i controlli nell'importatore, i vincoli sono stati estratti in `vincoliViolati(...)` (lista di violazioni leggibili, vuota ⇔ valida) e `valida()` è stato riscritto come delega: comportamento identico, un'unica codifica delle regole.
- **Import batch collocato nel package `controller`.** L'importatore coordina model e persistence attraverso il controller: è responsabilità da controller, non da persistence (che resta il solo strato a fare Gson↔file degli aggregati) né da view (che si limita a chiedere il percorso e stampare l'esito).
- **Le proposte scartate restano innocue in memoria.** `creaProposta` le aggiunge alla lista di sessione con stato `null`/non-VALIDA: non vengono mai persistite (filtro di `ArchivioProposte.salvaTutte`), non compaiono in bacheca né in archivio. Stesso comportamento del flusso interattivo quando l'utente crea una proposta non valida.

**Dataset demo (`data-demo/`, tracciato da Git):** generato con `GeneratoreDatasetDemo` (in `src/test/java`, strumento riutilizzabile, non un test JUnit) usando TempoSimulato + motore transizioni esistente; contiene 2 configuratori, 3 fruitori, 2 categorie e 8 proposte (3 APERTA, 2 CONFERMATA, 1 CONCLUSA, 1 ANNULLATA, 1 RITIRATA via `ritira()`), più `tempo.json` che congela la data simulata al 2026-09-01 per rendere la demo stabile nel tempo, e `import-esempio.json` per dimostrare la voce 15 (contiene un elemento volutamente non valido per mostrare il continua-e-riporta). Procedura di caricamento in `docs/manuale/manuale.md`, verificata end-to-end.

**Documentazione:** `docs/manuale/manuale.md` (installazione, avvio, dati demo, guida ai menu, note sul tempo simulato) e `docs/riepilogo-classi-finale.md` (riepilogo classi + casi d'uso unificati V1–V5 per i diagrammi UML globali).

**Non ancora iniziato:**
- Diagrammi UML globali (classi e casi d'uso) — a cura di Inder, sulla base di `docs/riepilogo-classi-finale.md`

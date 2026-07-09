# Manuale di installazione e uso

Guida operativa per installare, avviare e dimostrare l'applicazione (versioni V1–V5).

## 1. Prerequisiti

- **JDK 21** (verifica con `java -version`)
- **Apache Maven** (verifica con `mvn -version`)

Nessun database o servizio esterno: tutti i dati sono file JSON nella cartella `data/`
(creata automaticamente al primo salvataggio, nella directory da cui si avvia il programma).

## 2. Come compilare

Dalla root del progetto:

```bash
mvn clean package
```

Il comando compila, esegue l'intera suite di test (45 test JUnit) e produce il jar
eseguibile `target/progetto-ingsw-1.0-SNAPSHOT.jar` (fat JAR con dipendenze incluse,
via maven-shade-plugin).

## 3. Come avviare

Sempre dalla root del progetto (importante: i percorsi `data/` e `data-demo/` sono
relativi alla directory corrente):

```bash
java -jar target/progetto-ingsw-1.0-SNAPSHOT.jar
```

All'avvio il programma chiede il ruolo: `1) Configuratore` oppure `2) Fruitore`.

- **Primo avvio assoluto (archivio vuoto)**: scegliendo Configuratore parte il flusso di
  "primo accesso" (credenziali predefinite → scelta credenziali personali).
- **Fruitore**: si registra scegliendo direttamente le proprie credenziali definitive.

## 4. Caricare i dati demo prima di una dimostrazione

La cartella `data-demo/` (tracciata da Git) contiene un dataset completo e già verificato:
2 configuratori, 3 fruitori, 2 categorie e 8 proposte in tutti gli stati
(3 APERTA, 2 CONFERMATA, 1 CONCLUSA, 1 ANNULLATA, 1 RITIRATA).

**Procedura (testata)** — dalla root del progetto, prima dell'avvio:

```bash
mkdir -p data
cp data-demo/categorie.json data-demo/configuratori.json data-demo/configurazione.json \
   data-demo/fruitori.json data-demo/proposte.json data-demo/tempo.json data/
java -jar target/progetto-ingsw-1.0-SNAPSHOT.jar
```

(Il file `data-demo/import-esempio.json` non va copiato: serve alla dimostrazione
dell'import batch, vedi sotto.)

**Credenziali del dataset demo** (password in chiaro, uguale allo username):

| Ruolo | Username | Password |
|---|---|---|
| Configuratore | `configuratore1` | `configuratore1` |
| Configuratore | `configuratore2` | `configuratore2` |
| Fruitore | `fruitore1` | `fruitore1` |
| Fruitore | `fruitore2` | `fruitore2` |
| Fruitore | `fruitore3` | `fruitore3` |

Il dataset include `tempo.json` con **data simulata 2026-09-01**: l'applicazione partirà
sempre "a quella data", quindi gli stati delle proposte restano stabili qualunque sia il
giorno reale della demo (le 3 proposte APERTE hanno termine di iscrizione 2026-09-20,
ancora futuro rispetto alla data simulata).

**Demo dell'import batch (V5)**: entrare come `configuratore1`, scegliere la voce
`15) Importa da file` e indicare il percorso `data-demo/import-esempio.json`.
Il file contiene 1 categoria nuova e 3 proposte, di cui una volutamente incompleta:
il riepilogo mostrerà 2 proposte importate e 1 scartata con il vincolo violato.

**Rigenerare il dataset da zero** (se serve): il generatore è
`src/test/java/it/unibs/ingsw/GeneratoreDatasetDemo.java`:

```bash
mvn -q test-compile
java -cp "target/classes:target/test-classes:$HOME/.m2/repository/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar" \
     it.unibs.ingsw.GeneratoreDatasetDemo
```

## 5. Guida ai menu

### Menu Configuratore

| Voce | Descrizione |
|---|---|
| 1) Fissa campi base | Definisce una sola volta i campi presenti in ogni categoria (visibile solo finché non fissati). |
| 2) Fissa/modifica campi comuni | Sovrascrive l'elenco dei campi comuni a tutte le categorie. |
| 3) Crea categoria | Crea una nuova categoria (nome unico nel sistema). |
| 4) Rimuovi categoria | Elimina una categoria esistente. |
| 5) Aggiungi campo specifico a categoria | Aggiunge un campo proprio di una categoria (nome non in conflitto con base/comuni). |
| 6) Rimuovi campo specifico da categoria | Elimina un campo specifico. |
| 7) Visualizza categorie e i loro campi | Elenco completo: campi base + comuni + specifici. |
| 8) Crea proposta | Compila i valori dei campi; la proposta viene validata (vincoli su date e obbligatori). |
| 9) Pubblica proposta valida | Pubblica in bacheca una proposta VALIDA della sessione (stato APERTA, persistita). |
| 10) Visualizza bacheca | Proposte APERTE raggruppate per categoria. |
| 11) Visualizza archivio proposte | Tutte le proposte pubblicate, con storico degli stati e lista aderenti. |
| 12) Imposta/modifica data simulata | Scrive `data/tempo.json`; le transizioni di stato si applicano al riavvio. |
| 13) Rimuovi data simulata | Elimina `data/tempo.json`; si torna al tempo reale al riavvio. |
| 14) Ritira una proposta | Ritira una proposta APERTA o CONFERMATA (fino al giorno prima dell'evento); notifica gli aderenti. |
| 15) Importa da file | Import batch (V5): categorie, campi e proposte da file JSON; le proposte valide vengono pubblicate automaticamente. |
| 0) Esci | Chiude la sessione. |

### Menu Fruitore

Prima del menu: registrazione (username unico in tutto il sistema) oppure login.

| Voce | Descrizione |
|---|---|
| 1) Visualizza bacheca proposte aperte | Proposte APERTE con tutti i valori, raggruppate per categoria. |
| 2) Aderisci a una proposta | Iscrizione a una proposta APERTA (entro il termine, finché ci sono posti). |
| 3) Spazio personale | Notifiche ricevute (con promemoria completo per le CONFERMATE) e proposte a cui si è iscritti. |
| 4) Cancella una notifica | Rimuove una notifica dallo spazio personale. |
| 5) Disdici iscrizione a una proposta | Revoca l'iscrizione a una proposta APERTA, entro il termine di iscrizione. |
| 0) Esci | Chiude la sessione. |

## 6. Note sul tempo simulato

Il dominio non legge mai l'orologio di sistema direttamente: tutte le date passano
dall'interfaccia `FornitoreTempo`, con due implementazioni:

- `TempoReale` — usa la data di sistema (comportamento di default);
- `TempoSimulato` — usa la data contenuta in `data/tempo.json`, se il file esiste.

Questo serve a dimostrare le **transizioni di stato temporali** senza aspettare giorni
reali: il configuratore imposta una data futura (voce 12), riavvia l'applicazione, e al
riavvio il motore delle transizioni porta le proposte nello stato corretto
(APERTA → CONFERMATA se piena / ANNULLATA se non piena al superamento del termine;
CONFERMATA → CONCLUSA oltre la data conclusiva), depositando le notifiche negli spazi
personali dei fruitori aderenti.

Punti da ricordare durante la demo:

1. **Le transizioni si applicano all'avvio del programma**, non a metà sessione: dopo
   aver cambiato la data simulata, uscire e riavviare per vederne gli effetti.
2. Il dataset demo è già "congelato" al 2026-09-01 tramite `tempo.json`: non serve
   toccare nulla per mostrare gli stati richiesti.
3. Per mostrare una transizione dal vivo: far aderire i fruitori a una proposta APERTA
   fino a riempirla (es. "Camminata al lago di Iseo" richiede 2 partecipanti), poi
   impostare la data simulata al 2026-09-21 (oltre il termine 2026-09-20), riavviare e
   osservare che la proposta piena diventa CONFERMATA e le altre APERTE diventano
   ANNULLATA, con le relative notifiche negli spazi personali.
4. Voce 13 + riavvio per tornare al tempo reale.

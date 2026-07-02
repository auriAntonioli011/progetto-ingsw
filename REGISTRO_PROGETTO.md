# Registro delle decisioni di progetto

Questo file documenta le scelte implementative del progetto.

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

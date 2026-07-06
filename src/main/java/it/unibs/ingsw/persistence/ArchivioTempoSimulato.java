package it.unibs.ingsw.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Archivio della data simulata per il configuratore.
 *
 * Semantica: se il file esiste, l'app deve funzionare in tempo simulato con la
 * data contenuta; se il file NON esiste, l'app deve usare TempoReale. La scelta
 * viene fatta una sola volta da Main all'avvio: cambiare la data via menu
 * configuratore persiste sul disco ma l'effetto pieno (incluso il "downgrade"
 * simulato → reale) si vede al riavvio successivo.
 *
 * Rispecchia il pattern di ArchivioConfigurazione: RepositoryJsonSingolo su un
 * unico oggetto, in questo caso direttamente un LocalDate serializzato tramite
 * AdapterLocalDate (produce un file JSON contenente una singola stringa ISO).
 */
public class ArchivioTempoSimulato {

    private static final Path PERCORSO_DEFAULT = Path.of("data", "tempo.json");

    private final Path percorsoFile;
    private final RepositoryJsonSingolo<LocalDate> repository;

    // post: repository puntato su data/tempo.json (percorso di produzione)
    public ArchivioTempoSimulato() {
        this(PERCORSO_DEFAULT);
    }

    // V3: overload per test/isolamento — permette di scrivere il file altrove
    // (es. una directory temporanea @TempDir) senza toccare data/ reale.
    // post: repository puntato su percorsoFile
    public ArchivioTempoSimulato(Path percorsoFile) {
        if (percorsoFile == null)
            throw new IllegalArgumentException("percorsoFile non può essere null");
        this.percorsoFile = percorsoFile;
        this.repository = new RepositoryJsonSingolo<>(percorsoFile, LocalDate.class);
    }

    // post: restituisce Optional.of(data) se il file esiste,
    //       Optional.empty() se il file non esiste (tempo reale)
    public Optional<LocalDate> caricaDataSimulata() throws IOException {
        return repository.carica();
    }

    // pre:  data != null
    // post: il file esiste e contiene data serializzata in ISO-8601
    public void salvaDataSimulata(LocalDate data) throws IOException {
        if (data == null)
            throw new IllegalArgumentException("data non può essere null");
        repository.salva(data);
    }

    // post: il file non esiste più (nessuna eccezione se già assente)
    public void rimuoviDataSimulata() throws IOException {
        Files.deleteIfExists(percorsoFile);
    }
}

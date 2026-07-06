package it.unibs.ingsw.persistence;

import it.unibs.ingsw.model.Configuratore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Archivio dei Configuratori: persiste e carica la lista di configuratori
 * del sistema da "data/configuratori.json".
 */
public class ArchivioConfiguratori {

    private static final Path PERCORSO_DEFAULT = Path.of("data", "configuratori.json");

    private final RepositoryJson<Configuratore> repository;

    // post: repository puntato su data/configuratori.json (percorso di produzione)
    public ArchivioConfiguratori() {
        this(PERCORSO_DEFAULT);
    }

    // V3: overload per test/isolamento — permette di scrivere il file altrove
    // (es. una directory temporanea @TempDir) senza toccare data/ reale.
    // post: repository puntato su percorsoFile
    public ArchivioConfiguratori(Path percorsoFile) {
        if (percorsoFile == null)
            throw new IllegalArgumentException("percorsoFile non può essere null");
        this.repository = new RepositoryJson<>(percorsoFile, Configuratore.class);
    }

    // post: restituisce la lista di Configuratore letta dal file,
    //       oppure una lista vuota mutabile se il file non esiste ancora
    public List<Configuratore> caricaTutti() throws IOException {
        return repository.carica();
    }

    // pre:  configuratori != null
    // post: l'intera lista configuratori è persistita su data/configuratori.json
    public void salvaTutti(List<Configuratore> configuratori) throws IOException {
        if (configuratori == null)
            throw new IllegalArgumentException("configuratori non può essere null");
        repository.salva(configuratori);
    }
}

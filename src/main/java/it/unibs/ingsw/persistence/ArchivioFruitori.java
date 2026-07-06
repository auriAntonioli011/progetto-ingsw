package it.unibs.ingsw.persistence;

import it.unibs.ingsw.model.Fruitore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Archivio dei Fruitori: persiste e carica la lista di fruitori
 * del sistema da "data/fruitori.json".
 *
 * Ricalca ArchivioConfiguratori per struttura; il controllo di unicità dello
 * username tra Fruitori e Configuratori (requisito V3) è responsabilità del
 * ControllerFruitore in registrazione, non di questo archivio.
 */
public class ArchivioFruitori {

    private static final Path PERCORSO_DEFAULT = Path.of("data", "fruitori.json");

    private final RepositoryJson<Fruitore> repository;

    // post: repository puntato su data/fruitori.json (percorso di produzione)
    public ArchivioFruitori() {
        this(PERCORSO_DEFAULT);
    }

    // V3: overload per test/isolamento — permette di scrivere il file altrove
    // (es. una directory temporanea @TempDir) senza toccare data/ reale.
    // post: repository puntato su percorsoFile
    public ArchivioFruitori(Path percorsoFile) {
        if (percorsoFile == null)
            throw new IllegalArgumentException("percorsoFile non può essere null");
        this.repository = new RepositoryJson<>(percorsoFile, Fruitore.class);
    }

    // post: restituisce la lista di Fruitore letta dal file,
    //       oppure una lista vuota mutabile se il file non esiste ancora
    public List<Fruitore> caricaTutti() throws IOException {
        return repository.carica();
    }

    // pre:  fruitori != null
    // post: l'intera lista fruitori è persistita su data/fruitori.json
    public void salvaTutti(List<Fruitore> fruitori) throws IOException {
        if (fruitori == null)
            throw new IllegalArgumentException("fruitori non può essere null");
        repository.salva(fruitori);
    }
}

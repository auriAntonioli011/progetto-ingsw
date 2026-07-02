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

    private static final Path PERCORSO = Path.of("data", "configuratori.json");

    private final RepositoryJson<Configuratore> repository;

    // post: repository puntato su data/configuratori.json
    public ArchivioConfiguratori() {
        this.repository = new RepositoryJson<>(PERCORSO, Configuratore.class);
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

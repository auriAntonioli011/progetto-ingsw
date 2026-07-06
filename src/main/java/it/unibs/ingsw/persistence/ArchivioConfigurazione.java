package it.unibs.ingsw.persistence;

import it.unibs.ingsw.model.ConfigurazioneGlobale;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Archivio della ConfigurazioneGlobale: persiste e carica l'unica istanza
 * di configurazione del sistema da "data/configurazione.json".
 */
public class ArchivioConfigurazione {

    private static final Path PERCORSO_DEFAULT = Path.of("data", "configurazione.json");

    private final RepositoryJsonSingolo<ConfigurazioneGlobale> repository;

    // post: repository puntato su data/configurazione.json (percorso di produzione)
    public ArchivioConfigurazione() {
        this(PERCORSO_DEFAULT);
    }

    // V3: overload per test/isolamento — permette di scrivere il file altrove
    // (es. una directory temporanea @TempDir) senza toccare data/ reale.
    // post: repository puntato su percorsoFile
    public ArchivioConfigurazione(Path percorsoFile) {
        if (percorsoFile == null)
            throw new IllegalArgumentException("percorsoFile non può essere null");
        this.repository = new RepositoryJsonSingolo<>(percorsoFile, ConfigurazioneGlobale.class);
    }

    // post: restituisce la ConfigurazioneGlobale letta dal file,
    //       oppure una nuova ConfigurazioneGlobale() vuota se il file non esiste ancora
    public ConfigurazioneGlobale carica() throws IOException {
        return repository.carica().orElseGet(ConfigurazioneGlobale::new);
    }

    // pre:  configurazione != null
    // post: lo stato corrente di configurazione è persistito su data/configurazione.json
    public void salva(ConfigurazioneGlobale configurazione) throws IOException {
        if (configurazione == null)
            throw new IllegalArgumentException("configurazione non può essere null");
        repository.salva(configurazione);
    }
}

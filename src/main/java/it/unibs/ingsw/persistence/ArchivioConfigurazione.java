package it.unibs.ingsw.persistence;

import it.unibs.ingsw.model.ConfigurazioneGlobale;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Archivio della ConfigurazioneGlobale: persiste e carica l'unica istanza
 * di configurazione del sistema da "data/configurazione.json".
 */
public class ArchivioConfigurazione {

    private static final Path PERCORSO = Path.of("data", "configurazione.json");

    private final RepositoryJsonSingolo<ConfigurazioneGlobale> repository;

    // post: repository puntato su data/configurazione.json
    public ArchivioConfigurazione() {
        this.repository = new RepositoryJsonSingolo<>(PERCORSO, ConfigurazioneGlobale.class);
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

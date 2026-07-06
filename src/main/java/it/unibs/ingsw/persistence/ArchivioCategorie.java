package it.unibs.ingsw.persistence;

import it.unibs.ingsw.model.Categoria;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Archivio delle Categorie: persiste e carica l'intera lista di categorie
 * del sistema da "data/categorie.json".
 */
public class ArchivioCategorie {

    private static final Path PERCORSO_DEFAULT = Path.of("data", "categorie.json");

    private final RepositoryJson<Categoria> repository;

    // post: repository puntato su data/categorie.json (percorso di produzione)
    public ArchivioCategorie() {
        this(PERCORSO_DEFAULT);
    }

    // V3: overload per test/isolamento — permette di scrivere il file altrove
    // (es. una directory temporanea @TempDir) senza toccare data/ reale.
    // post: repository puntato su percorsoFile
    public ArchivioCategorie(Path percorsoFile) {
        if (percorsoFile == null)
            throw new IllegalArgumentException("percorsoFile non può essere null");
        this.repository = new RepositoryJson<>(percorsoFile, Categoria.class);
    }

    // post: restituisce la lista di Categoria letta dal file,
    //       oppure una lista vuota mutabile se il file non esiste ancora
    public List<Categoria> caricaTutte() throws IOException {
        return repository.carica();
    }

    // pre:  categorie != null
    // post: l'intera lista categorie è persistita su data/categorie.json
    public void salvaTutte(List<Categoria> categorie) throws IOException {
        if (categorie == null)
            throw new IllegalArgumentException("categorie non può essere null");
        repository.salva(categorie);
    }
}

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

    private static final Path PERCORSO = Path.of("data", "categorie.json");

    private final RepositoryJson<Categoria> repository;

    // post: repository puntato su data/categorie.json
    public ArchivioCategorie() {
        this.repository = new RepositoryJson<>(PERCORSO, Categoria.class);
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

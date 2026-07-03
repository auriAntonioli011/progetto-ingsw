package it.unibs.ingsw.persistence;

import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.StatoProposta;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Archivio delle Proposte: persiste e carica le proposte da "data/proposte.json".
 *
 * Requisito V2: sono persistite ESCLUSIVAMENTE le proposte con stato APERTA. Una proposta
 * VALIDA ma non ancora pubblicata a fine sessione non viene mai scritta su disco (è una
 * bozza in memoria che l'utente sceglie di pubblicare o meno). Il filtro è applicato in
 * salvaTutte() così anche se il controller passa l'intera lista non c'è rischio di leak.
 */
public class ArchivioProposte {

    private static final Path PERCORSO = Path.of("data", "proposte.json");

    private final RepositoryJson<Proposta> repository;

    // post: repository puntato su data/proposte.json
    public ArchivioProposte() {
        this.repository = new RepositoryJson<>(PERCORSO, Proposta.class);
    }

    // post: restituisce la lista di Proposta letta dal file (tutte APERTA per costruzione),
    //       oppure una lista vuota mutabile se il file non esiste ancora
    public List<Proposta> caricaTutte() throws IOException {
        return repository.carica();
    }

    // pre:  proposte != null
    // post: le sole proposte con stato == APERTA sono persistite su data/proposte.json;
    //       le proposte VALIDA non pubblicate vengono ignorate (non serializzate) — requisito V2
    public void salvaTutte(List<Proposta> proposte) throws IOException {
        if (proposte == null)
            throw new IllegalArgumentException("proposte non può essere null");
        List<Proposta> soloAperte = new ArrayList<>();
        for (Proposta p : proposte) {
            if (p != null && p.getStato() == StatoProposta.APERTA) {
                soloAperte.add(p);
            }
        }
        repository.salva(soloAperte);
    }
}

package it.unibs.ingsw.persistence;

import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.StatoProposta;
import it.unibs.ingsw.util.FornitoreTempo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Archivio delle Proposte: persiste e carica le proposte da "data/proposte.json".
 *
 * Requisito di persistenza (V2 esteso in V3): sono persistite tutte le proposte tranne
 * quelle in stato VALIDA (che restano bozze di sessione, non pubblicate) e quelle con
 * stato == null (non ancora validate). L'invariante V2 (non persistere VALIDA) è
 * preservata: si aggiungono solo nuovi stati ammessi (CONFERMATA, ANNULLATA, CONCLUSA).
 *
 * V3 — Motore transizioni: elaboraTransizioni(...) è il punto unico da cui il sistema
 * fa avanzare gli stati temporali (§3.2). È chiamato all'avvio da entrambi i ruoli
 * prima di mostrare la bacheca o lo spazio personale, così l'utente vede sempre lo
 * stato aggiornato al fornitoreTempo corrente.
 */
public class ArchivioProposte {

    private static final Path PERCORSO_DEFAULT = Path.of("data", "proposte.json");

    // V3+V4: stati persistiti. VALIDA (bozza di sessione) e null (non ancora validata)
    // restano intenzionalmente esclusi — vedi javadoc di classe.
    private static final Set<StatoProposta> STATI_PERSISTITI = EnumSet.of(
            StatoProposta.APERTA,
            StatoProposta.CONFERMATA,
            StatoProposta.ANNULLATA,
            StatoProposta.CONCLUSA,
            StatoProposta.RITIRATA
    );

    private final RepositoryJson<Proposta> repository;

    // post: repository puntato su data/proposte.json (percorso di produzione)
    public ArchivioProposte() {
        this(PERCORSO_DEFAULT);
    }

    // V3: overload per test/isolamento — permette di scrivere il file altrove
    // (es. una directory temporanea @TempDir) senza toccare data/ reale.
    // post: repository puntato su percorsoFile
    public ArchivioProposte(Path percorsoFile) {
        if (percorsoFile == null)
            throw new IllegalArgumentException("percorsoFile non può essere null");
        this.repository = new RepositoryJson<>(percorsoFile, Proposta.class);
    }

    // post: restituisce la lista di Proposta letta dal file (tutte con stato ∈ STATI_PERSISTITI
    //       per costruzione), oppure una lista vuota mutabile se il file non esiste ancora
    public List<Proposta> caricaTutte() throws IOException {
        return repository.carica();
    }

    // pre:  proposte != null
    // post: le sole proposte con stato ∈ STATI_PERSISTITI sono persistite su
    //       data/proposte.json; le proposte VALIDA (bozza) o con stato null vengono
    //       ignorate (non serializzate)
    public void salvaTutte(List<Proposta> proposte) throws IOException {
        if (proposte == null)
            throw new IllegalArgumentException("proposte non può essere null");
        List<Proposta> persistibili = new ArrayList<>();
        for (Proposta p : proposte) {
            if (p != null && STATI_PERSISTITI.contains(p.getStato())) {
                persistibili.add(p);
            }
        }
        repository.salva(persistibili);
    }

    // pre:  proposte != null && fornitoreTempo != null
    // post: per ciascuna proposta in lista viene invocato elaboraTransizione(fornitoreTempo);
    //       la lista aggiornata viene poi persistita chiamando salvaTutte.
    //       Chi si iscrive come osservatore delle proposte (via registraOsservatore) DEVE
    //       averlo fatto prima di questo metodo per ricevere eventuali notifiche.
    public void elaboraTransizioni(List<Proposta> proposte, FornitoreTempo fornitoreTempo) throws IOException {
        if (proposte == null)
            throw new IllegalArgumentException("proposte non può essere null");
        if (fornitoreTempo == null)
            throw new IllegalArgumentException("fornitoreTempo non può essere null");
        for (Proposta p : proposte) {
            if (p != null) p.elaboraTransizione(fornitoreTempo);
        }
        salvaTutte(proposte);
    }
}

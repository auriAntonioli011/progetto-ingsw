package it.unibs.ingsw.controller;

import it.unibs.ingsw.model.Bacheca;
import it.unibs.ingsw.model.Fruitore;
import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.SpazioPersonale;
import it.unibs.ingsw.model.StatoProposta;
import it.unibs.ingsw.persistence.ArchivioConfiguratori;
import it.unibs.ingsw.persistence.ArchivioFruitori;
import it.unibs.ingsw.persistence.ArchivioProposte;
import it.unibs.ingsw.util.FornitoreTempo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestratore delle operazioni del Fruitore: coordina model e persistence per
 * registrazione, login, adesione alle proposte e accesso allo spazio personale.
 * Nessun System.out/in: la comunicazione con l'utente resta responsabilità della view.
 *
 * All'avvio (avvia()) esegue due passi che non si vedono nel menu ma sono essenziali:
 *  1. Ricollega gli osservatori: per ogni proposta APERTA/CONFERMATA/CONCLUSA/ANNULLATA
 *     e per ogni username presente in aderenti, ritrova il Fruitore corrispondente e
 *     lo registra come osservatore (il field è transient, va rifatto ad ogni avvio).
 *  2. Fa girare il motore transizioni prima di mostrare qualunque cosa all'utente,
 *     così eventuali cambi di stato (§3.2) sono immediatamente visibili e le
 *     notifiche vengono depositate nello spazio personale già in questa sessione.
 */
public class ControllerFruitore {

    private final ArchivioFruitori archivioFruitori;
    private final ArchivioConfiguratori archivioConfiguratori;
    private final ArchivioProposte archivioProposte;
    private final FornitoreTempo fornitoreTempo;

    private List<Fruitore> fruitori;
    private List<Proposta> proposte;
    private Fruitore fruitoreLoggato;

    // pre:  tutti i parametri != null
    // post: fruitori e proposte caricati dagli archivi; fruitoreLoggato == null
    public ControllerFruitore(ArchivioFruitori archivioFruitori,
                              ArchivioConfiguratori archivioConfiguratori,
                              ArchivioProposte archivioProposte,
                              FornitoreTempo fornitoreTempo) throws IOException {
        if (archivioFruitori == null)
            throw new IllegalArgumentException("archivioFruitori non può essere null");
        if (archivioConfiguratori == null)
            throw new IllegalArgumentException("archivioConfiguratori non può essere null");
        if (archivioProposte == null)
            throw new IllegalArgumentException("archivioProposte non può essere null");
        if (fornitoreTempo == null)
            throw new IllegalArgumentException("fornitoreTempo non può essere null");

        this.archivioFruitori = archivioFruitori;
        this.archivioConfiguratori = archivioConfiguratori;
        this.archivioProposte = archivioProposte;
        this.fornitoreTempo = fornitoreTempo;

        this.fruitori = archivioFruitori.caricaTutti();
        this.proposte = archivioProposte.caricaTutte();
        this.fruitoreLoggato = null;
    }

    // post: (i) tutti i Fruitore presenti in aderenti sono rilegati come osservatori
    //           delle rispettive Proposta; (ii) il motore transizioni è stato invocato
    //           una volta sull'archivio proposte, gli stati aggiornati sono persistiti.
    //       Da chiamare una sola volta all'avvio del flusso Fruitore, prima del login.
    public void inizializzaSessione() throws IOException {
        ricollegaOsservatori();
        archivioProposte.elaboraTransizioni(proposte, fornitoreTempo);
        // Alcune notifiche appena depositate riguardano i fruitori: salviamo il loro
        // stato (spazio personale) per non perderle allo spegnimento.
        archivioFruitori.salvaTutti(fruitori);
    }

    // pre:  username != null && !username.isBlank()
    //       && password != null && !password.isBlank()
    //       && nessun Utente (Fruitore o Configuratore) ha già quell'username
    // post: un nuovo Fruitore con credenziali definitive (credenzialiProvvisorie == false)
    //       è aggiunto alla lista in memoria e persistito su ArchivioFruitori;
    //       fruitoreLoggato == fruitore appena creato
    public Fruitore registra(String username, String password) throws IOException {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("username non può essere null o vuoto");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("password non può essere null o vuota");
        if (usernameGiaInUso(username))
            throw new IllegalArgumentException(
                    "username '" + username + "' già in uso");

        Fruitore fruitore = new Fruitore(username, password);
        // Il Fruitore non passa per il flusso a due fasi: gli confermiamo subito
        // le stesse credenziali per portare credenzialiProvvisorie a false.
        fruitore.confermaCredenzialiPersonali(username, password);

        fruitori.add(fruitore);
        archivioFruitori.salvaTutti(fruitori);
        this.fruitoreLoggato = fruitore;
        return fruitore;
    }

    // pre:  username != null && !username.isBlank()
    //       && password != null && !password.isBlank()
    //       && esiste un fruitore con quell'username e quella password
    // post: fruitoreLoggato == fruitore trovato && restituisce il fruitore loggato
    public Fruitore login(String username, String password) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("username non può essere null o vuoto");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("password non può essere null o vuota");

        Fruitore trovato = trovaFruitorePerUsername(username)
                .filter(f -> f.verificaPassword(password))
                .orElseThrow(() -> new IllegalArgumentException("credenziali non valide"));

        this.fruitoreLoggato = trovato;
        return trovato;
    }

    // post: restituisce il fruitore attualmente loggato, o null se nessuno
    public Fruitore getFruitoreLoggato() {
        return fruitoreLoggato;
    }

    // post: restituisce una Bacheca costruita a partire dalle proposte APERTA in memoria
    public Bacheca getBacheca() {
        List<Proposta> aperte = new ArrayList<>();
        for (Proposta p : proposte) {
            if (p.getStato() == StatoProposta.APERTA) aperte.add(p);
        }
        return new Bacheca(aperte);
    }

    // pre:  fruitoreLoggato != null && proposta != null && proposta ∈ proposte
    // post: (i) proposta.aderisci(fruitoreLoggato, fornitoreTempo) è invocato con successo,
    //       (ii) la lista proposte è persistita su ArchivioProposte,
    //       (iii) il fruitore è registrato come osservatore (già fatto da aderisci).
    //       Se aderisci lancia IllegalStateException per vincoli non rispettati, l'eccezione
    //       viene propagata alla view.
    // inv:  vincolo "solo se stesso": il controller iscrive esclusivamente il fruitoreLoggato,
    //       non permette scelta arbitraria di un altro fruitore.
    public void aderisci(Proposta proposta) throws IOException {
        if (fruitoreLoggato == null)
            throw new IllegalStateException("nessun fruitore loggato");
        if (proposta == null)
            throw new IllegalArgumentException("proposta non può essere null");
        if (!proposte.contains(proposta))
            throw new IllegalArgumentException("proposta non presente tra quelle in memoria");

        proposta.aderisci(fruitoreLoggato, fornitoreTempo);
        archivioProposte.salvaTutte(proposte);
    }

    // pre:  fruitoreLoggato != null && proposta != null && proposta ∈ proposte
    // post: proposta.disdici(fruitoreLoggato, fornitoreTempo) è invocato con successo,
    //       la lista proposte è persistita su ArchivioProposte (aderenti aggiornato).
    //       Se disdici lancia IllegalStateException (stato non APERTA, termine superato,
    //       fruitore non iscritto) l'eccezione viene propagata alla view.
    // inv:  come per aderisci, "solo se stesso" è enforced qui: si disdice esclusivamente
    //       il fruitoreLoggato.
    public void disdici(Proposta proposta) throws IOException {
        if (fruitoreLoggato == null)
            throw new IllegalStateException("nessun fruitore loggato");
        if (proposta == null)
            throw new IllegalArgumentException("proposta non può essere null");
        if (!proposte.contains(proposta))
            throw new IllegalArgumentException("proposta non presente tra quelle in memoria");

        proposta.disdici(fruitoreLoggato, fornitoreTempo);
        archivioProposte.salvaTutte(proposte);
    }

    // post: restituisce lo SpazioPersonale del fruitore loggato
    //       (contiene le Notifica ricevute in questa e nelle sessioni precedenti)
    public SpazioPersonale getSpazioPersonale() {
        if (fruitoreLoggato == null)
            throw new IllegalStateException("nessun fruitore loggato");
        return fruitoreLoggato.getSpazioPersonale();
    }

    // pre:  fruitoreLoggato != null && indice valido rispetto a getSpazioPersonale().getNotifiche()
    // post: la notifica in posizione indice è rimossa dallo spazio personale;
    //       la lista fruitori aggiornata è persistita su ArchivioFruitori
    public void cancellaNotifica(int indice) throws IOException {
        if (fruitoreLoggato == null)
            throw new IllegalStateException("nessun fruitore loggato");
        fruitoreLoggato.getSpazioPersonale().rimuoviNotifica(indice);
        archivioFruitori.salvaTutti(fruitori);
    }

    // post: restituisce l'elenco delle proposte a cui il fruitore loggato risulta iscritto,
    //       ricavato scorrendo le proposte in memoria e filtrando su aderenti
    public List<Proposta> getProposteAderite() {
        if (fruitoreLoggato == null)
            throw new IllegalStateException("nessun fruitore loggato");
        String username = fruitoreLoggato.getUsername();
        List<Proposta> risultato = new ArrayList<>();
        for (Proposta p : proposte) {
            if (p.getAderenti().contains(username)) risultato.add(p);
        }
        return List.copyOf(risultato);
    }

    // --- helper privati ---

    private void ricollegaOsservatori() {
        for (Proposta p : proposte) {
            for (String username : p.getAderenti()) {
                trovaFruitorePerUsername(username)
                        .ifPresent(p::registraOsservatore);
            }
        }
    }

    private Optional<Fruitore> trovaFruitorePerUsername(String username) {
        return fruitori.stream()
                .filter(f -> f.getUsername().equals(username))
                .findFirst();
    }

    private boolean usernameGiaInUso(String username) throws IOException {
        boolean traFruitori = fruitori.stream()
                .anyMatch(f -> f.getUsername().equals(username));
        if (traFruitori) return true;
        return archivioConfiguratori.caricaTutti().stream()
                .anyMatch(c -> c.getUsername().equals(username));
    }
}

package it.unibs.ingsw.controller;

import it.unibs.ingsw.model.Bacheca;
import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.ConfigurazioneGlobale;
import it.unibs.ingsw.model.Configuratore;
import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.StatoProposta;
import it.unibs.ingsw.model.Fruitore;
import it.unibs.ingsw.persistence.ArchivioCategorie;
import it.unibs.ingsw.persistence.ArchivioConfigurazione;
import it.unibs.ingsw.persistence.ArchivioConfiguratori;
import it.unibs.ingsw.persistence.ArchivioFruitori;
import it.unibs.ingsw.persistence.ArchivioProposte;
import it.unibs.ingsw.persistence.ArchivioTempoSimulato;
import it.unibs.ingsw.util.FornitoreTempo;
import it.unibs.ingsw.util.TempoSimulato;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestratore delle operazioni del Configuratore: coordina model e persistence,
 * non produce mai output su console (nessun System.out/in).
 * Ogni metodo che modifica lo stato persistente dichiara throws IOException.
 */
public class ControllerConfiguratore {

    private final ArchivioConfigurazione archivioConfigurazione;
    private final ArchivioCategorie archivioCategorie;
    private final ArchivioConfiguratori archivioConfiguratori;
    // V2: nuovo archivio dedicato alle proposte APERTE (le VALIDA non si persistono).
    private final ArchivioProposte archivioProposte;
    // V2: iniettato per evitare LocalDate.now() diretto nella logica di dominio
    // (creazione, validazione, pubblicazione di proposte).
    private final FornitoreTempo fornitoreTempo;
    // V3: archivio della data simulata per la "modalità configuratore" (§ REGISTRO_PROGETTO.md).
    // Serve a persistere/rimuovere data/tempo.json che Main legge all'avvio.
    private final ArchivioTempoSimulato archivioTempoSimulato;
    // V4: serve per (a) rilegare gli osservatori sulle Proposta di questo controller
    // così le notifiche prodotte da ritiraProposta raggiungono i SpazioPersonale
    // e (b) persistere fruitori.json quando una notifica viene depositata.
    // Vedi javadoc di ritiraProposta per il motivo architetturale.
    private final ArchivioFruitori archivioFruitori;
    private List<Fruitore> fruitori;

    private ConfigurazioneGlobale configurazioneGlobale;
    private List<Categoria> categorie;
    private List<Configuratore> configuratori;
    // V2: contiene sia proposte VALIDA (non persistite) sia APERTE (persistite).
    // Le APERTE vengono ricaricate dal disco all'avvio; le VALIDA sono solo di sessione.
    private List<Proposta> proposte;
    private Configuratore configuratoreLoggato;

    // pre:  tutti i parametri != null
    // post: configurazioneGlobale, categorie, configuratori e proposte APERTE sono caricati dagli archivi;
    //       configuratoreLoggato == null
    // motivazione: rispetto alla firma V1, l'aggiunta di archivioProposte e fornitoreTempo è
    // necessaria per abilitare V2 (creazione/pubblicazione proposte) mantenendo lo stesso pattern
    // di iniezione già usato per gli altri archivi. Il comportamento V1 è invariato.
    public ControllerConfiguratore(ArchivioConfigurazione archivioConfigurazione,
                                    ArchivioCategorie archivioCategorie,
                                    ArchivioConfiguratori archivioConfiguratori,
                                    ArchivioProposte archivioProposte,
                                    ArchivioFruitori archivioFruitori,
                                    ArchivioTempoSimulato archivioTempoSimulato,
                                    FornitoreTempo fornitoreTempo) throws IOException {
        if (archivioConfigurazione == null)
            throw new IllegalArgumentException("archivioConfigurazione non può essere null");
        if (archivioCategorie == null)
            throw new IllegalArgumentException("archivioCategorie non può essere null");
        if (archivioConfiguratori == null)
            throw new IllegalArgumentException("archivioConfiguratori non può essere null");
        if (archivioProposte == null)
            throw new IllegalArgumentException("archivioProposte non può essere null");
        if (archivioFruitori == null)
            throw new IllegalArgumentException("archivioFruitori non può essere null");
        if (archivioTempoSimulato == null)
            throw new IllegalArgumentException("archivioTempoSimulato non può essere null");
        if (fornitoreTempo == null)
            throw new IllegalArgumentException("fornitoreTempo non può essere null");

        this.archivioConfigurazione = archivioConfigurazione;
        this.archivioCategorie = archivioCategorie;
        this.archivioConfiguratori = archivioConfiguratori;
        this.archivioProposte = archivioProposte;
        this.archivioFruitori = archivioFruitori;
        this.archivioTempoSimulato = archivioTempoSimulato;
        this.fornitoreTempo = fornitoreTempo;

        this.configurazioneGlobale = archivioConfigurazione.carica();
        this.categorie = archivioCategorie.caricaTutte();
        this.configuratori = archivioConfiguratori.caricaTutti();
        this.proposte = archivioProposte.caricaTutte();
        this.fruitori = archivioFruitori.caricaTutti();
        this.configuratoreLoggato = null;

        // V4: rilega gli osservatori sulle nostre Proposta ora. Motivo: quando questo
        // controller viene istanziato da Main dopo ControllerFruitore, le Proposta qui
        // caricate sono ISTANZE DIVERSE da quelle di ControllerFruitore (caricamento
        // separato da disco), con osservatori transient a null/vuoto. Senza questo
        // giro, ritiraProposta chiamerebbe applicaTransizione con osservatori vuoti e
        // le notifiche di ritiro non arriverebbero mai nei SpazioPersonale.
        for (Proposta p : proposte) {
            for (String username : p.getAderenti()) {
                fruitori.stream()
                        .filter(f -> f.getUsername().equals(username))
                        .findFirst()
                        .ifPresent(p::registraOsservatore);
            }
        }
    }

    // pre:  usernamePredefinito != null && !usernamePredefinito.isBlank()
    //       && passwordPredefinita != null && !passwordPredefinita.isBlank()
    //       && configuratori.isEmpty()  (il primo accesso è consentito una sola volta)
    // post: restituisce un nuovo Configuratore con credenzialiProvvisorie == true,
    //       non ancora aggiunto alla lista né salvato su file
    public Configuratore primoAccesso(String usernamePredefinito, String passwordPredefinita) {
        if (usernamePredefinito == null || usernamePredefinito.isBlank())
            throw new IllegalArgumentException("usernamePredefinito non può essere null o vuoto");
        if (passwordPredefinita == null || passwordPredefinita.isBlank())
            throw new IllegalArgumentException("passwordPredefinita non può essere null o vuota");
        if (!configuratori.isEmpty())
            throw new IllegalStateException("il primo accesso è già stato effettuato");
        return new Configuratore(usernamePredefinito, passwordPredefinita);
    }

    // pre:  configuratore != null && configuratore.isCredenzialiProvvisorie() == true
    //       && nuovoUsername != null && !nuovoUsername.isBlank()
    //       && nuovaPassword != null && !nuovaPassword.isBlank()
    //       && nessun configuratore in lista ha già username == nuovoUsername
    // post: configuratore.getUsername() == nuovoUsername
    //       && configuratore.isCredenzialiProvvisorie() == false
    //       && configuratore è aggiunto alla lista in memoria
    //       && la lista aggiornata è persistita su ArchivioConfiguratori
    //       && configuratoreLoggato == configuratore
    public void confermaCredenzialiPersonali(Configuratore configuratore,
                                             String nuovoUsername,
                                             String nuovaPassword) throws IOException {
        if (configuratore == null)
            throw new IllegalArgumentException("configuratore non può essere null");
        if (nuovoUsername == null || nuovoUsername.isBlank())
            throw new IllegalArgumentException("nuovoUsername non può essere null o vuoto");
        if (nuovaPassword == null || nuovaPassword.isBlank())
            throw new IllegalArgumentException("nuovaPassword non può essere null o vuota");
        if (trovaPerUsername(nuovoUsername).isPresent())
            throw new IllegalArgumentException(
                    "username '" + nuovoUsername + "' già in uso da un altro configuratore");

        configuratore.confermaCredenzialiPersonali(nuovoUsername, nuovaPassword);
        configuratori.add(configuratore);
        archivioConfiguratori.salvaTutti(configuratori);
        this.configuratoreLoggato = configuratore;
    }

    // pre:  username != null && !username.isBlank()
    //       && password != null && !password.isBlank()
    //       && esiste un configuratore con quell'username e quella password
    // post: configuratoreLoggato == configuratore trovato
    //       && restituisce il configuratore loggato
    public Configuratore login(String username, String password) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("username non può essere null o vuoto");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("password non può essere null o vuota");

        Configuratore trovato = trovaPerUsername(username)
                .filter(c -> c.verificaPassword(password))
                .orElseThrow(() -> new IllegalArgumentException("credenziali non valide"));

        this.configuratoreLoggato = trovato;
        return trovato;
    }

    // post: restituisce il configuratore attualmente loggato, o null se nessuno
    public Configuratore getConfiguratoreLoggato() {
        return configuratoreLoggato;
    }

    // pre:  campiBase != null && !campiBase.isEmpty()
    //       && configurazioneGlobale.campiBaseFissati() == false
    // post: configurazioneGlobale.campiBaseFissati() == true
    //       && lo stato aggiornato è persistito su ArchivioConfigurazione
    public void fissaCampiBase(List<Campo> campiBase) throws IOException {
        configurazioneGlobale.fissaCampiBase(campiBase);
        archivioConfigurazione.salva(configurazioneGlobale);
    }

    // pre:  campiComuni != null
    //       && nessun elemento di campiComuni ha lo stesso nome di un campo base fissato
    // post: configurazioneGlobale.getCampiComuni() rispecchia campiComuni
    //       && lo stato aggiornato è persistito su ArchivioConfigurazione
    public void impostaCampiComuni(List<Campo> campiComuni) throws IOException {
        configurazioneGlobale.impostaCampiComuni(campiComuni);
        archivioConfigurazione.salva(configurazioneGlobale);
    }

    // pre:  nome != null && !nome.isBlank()
    //       && nessuna categoria in lista ha già quel nome
    // post: una nuova Categoria(nome) è aggiunta alla lista in memoria
    //       && la lista aggiornata è persistita su ArchivioCategorie
    //       && restituisce la categoria creata
    public Categoria creaCategoria(String nome) throws IOException {
        if (nome == null || nome.isBlank())
            throw new IllegalArgumentException("nome non può essere null o vuoto");
        if (trovaCategoriaPer(nome).isPresent())
            throw new IllegalArgumentException(
                    "esiste già una categoria con nome '" + nome + "'");

        Categoria nuova = new Categoria(nome);
        categorie.add(nuova);
        archivioCategorie.salvaTutte(categorie);
        return nuova;
    }

    // pre:  nome != null && !nome.isBlank()
    //       && esiste una categoria con quel nome
    // post: la categoria con quel nome è rimossa dalla lista in memoria
    //       && la lista aggiornata è persistita su ArchivioCategorie
    public void rimuoviCategoria(String nome) throws IOException {
        if (nome == null || nome.isBlank())
            throw new IllegalArgumentException("nome non può essere null o vuoto");
        Categoria da_rimuovere = trovaCategoriaPer(nome)
                .orElseThrow(() -> new IllegalArgumentException(
                        "nessuna categoria con nome '" + nome + "'"));

        categorie.remove(da_rimuovere);
        archivioCategorie.salvaTutte(categorie);
    }

    // pre:  nomeCategoria != null && !nomeCategoria.isBlank()
    //       && esiste una categoria con quel nome
    //       && campo != null
    //       && campo.getNome() non coincide con nessun campo base/comune/specifico già presente
    // post: campo è aggiunto ai campi specifici della categoria trovata
    //       && la lista categorie aggiornata è persistita su ArchivioCategorie
    public void aggiungiCampoSpecifico(String nomeCategoria, Campo campo) throws IOException {
        if (nomeCategoria == null || nomeCategoria.isBlank())
            throw new IllegalArgumentException("nomeCategoria non può essere null o vuoto");
        Categoria categoria = trovaCategoriaPer(nomeCategoria)
                .orElseThrow(() -> new IllegalArgumentException(
                        "nessuna categoria con nome '" + nomeCategoria + "'"));

        categoria.aggiungiCampoSpecifico(campo, configurazioneGlobale);
        archivioCategorie.salvaTutte(categorie);
    }

    // pre:  nomeCategoria != null && !nomeCategoria.isBlank()
    //       && esiste una categoria con quel nome
    //       && nomeCampo != null && !nomeCampo.isBlank()
    //       && la categoria contiene un campo specifico con quel nome
    // post: il campo specifico è rimosso dalla categoria
    //       && la lista categorie aggiornata è persistita su ArchivioCategorie
    public void rimuoviCampoSpecifico(String nomeCategoria, String nomeCampo) throws IOException {
        if (nomeCategoria == null || nomeCategoria.isBlank())
            throw new IllegalArgumentException("nomeCategoria non può essere null o vuoto");
        Categoria categoria = trovaCategoriaPer(nomeCategoria)
                .orElseThrow(() -> new IllegalArgumentException(
                        "nessuna categoria con nome '" + nomeCategoria + "'"));

        categoria.rimuoviCampoSpecifico(nomeCampo);
        archivioCategorie.salvaTutte(categorie);
    }

    // post: restituisce true se non esiste ancora nessun configuratore registrato nel sistema,
    //       ovvero se primoAccesso() può essere chiamato senza eccezione
    public boolean isPrimoAccesso() {
        return configuratori.isEmpty();
    }

    // post: restituisce una vista immutabile della lista categorie in memoria
    public List<Categoria> getCategorie() {
        return List.copyOf(categorie);
    }

    /**
     * ATTENZIONE: l'oggetto restituito è mutabile. Chiamare direttamente metodi come
     * fissaCampiBase() o impostaCampiComuni() su questo riferimento modifica lo stato
     * in memoria ma bypassa la persistenza (nessun salvataggio su file). La view deve
     * sempre passare per i metodi dedicati del controller (fissaCampiBase,
     * impostaCampiComuni) per garantire la coerenza tra stato in memoria e stato salvato.
     */
    // post: restituisce l'istanza corrente di ConfigurazioneGlobale
    public ConfigurazioneGlobale getConfigurazioneGlobale() {
        return configurazioneGlobale;
    }

    // -------------------------------------------------------------------------
    // V2 — Proposte
    // -------------------------------------------------------------------------

    // pre:  categoria != null && la categoria appartiene a getCategorie()
    //       && valori != null (può essere vuota o parziale — sarà valida() a giudicare)
    // post: viene creata una nuova Proposta con quei valori, è invocata valida() (che può
    //       promuoverla a StatoProposta.VALIDA), la proposta è aggiunta alla lista in memoria
    //       ma NON viene persistita (requisito V2: solo APERTA su disco);
    //       restituisce la proposta creata (l'utente può leggerne lo stato via getStato()).
    public Proposta creaProposta(Categoria categoria, Map<String, String> valori) {
        if (categoria == null)
            throw new IllegalArgumentException("categoria non può essere null");
        if (valori == null)
            throw new IllegalArgumentException("valori non può essere null");
        if (!categorie.contains(categoria))
            throw new IllegalArgumentException(
                    "la categoria '" + categoria.getNome() + "' non è tra quelle registrate");

        Proposta p = new Proposta(categoria, valori);
        p.valida(fornitoreTempo, configurazioneGlobale);
        proposte.add(p);
        return p;
    }

    // pre:  proposta != null && proposta ∈ proposte
    // post: se proposta.getStato() != VALIDA lancia IllegalStateException,
    //       altrimenti proposta.getStato() == APERTA
    //       && proposta.getDataPubblicazione() == fornitoreTempo.oggi()
    //       && la lista aggiornata (solo APERTE) è persistita su ArchivioProposte
    public void richiediPubblicazione(Proposta proposta) throws IOException {
        if (proposta == null)
            throw new IllegalArgumentException("proposta non può essere null");
        if (!proposte.contains(proposta))
            throw new IllegalArgumentException("proposta non presente tra quelle in memoria");
        if (proposta.getStato() != StatoProposta.VALIDA)
            throw new IllegalStateException(
                    "solo una proposta VALIDA può essere pubblicata");

        proposta.marcaPubblicata(fornitoreTempo.oggi());
        archivioProposte.salvaTutte(proposte);
    }

    // post: restituisce una vista immutabile delle proposte con stato VALIDA
    //       (create nella sessione corrente, non ancora pubblicate)
    public List<Proposta> getProposteValide() {
        List<Proposta> risultato = new ArrayList<>();
        for (Proposta p : proposte) {
            if (p.getStato() == StatoProposta.VALIDA) risultato.add(p);
        }
        return List.copyOf(risultato);
    }

    // post: restituisce una Bacheca costruita a partire dalle proposte APERTE in memoria
    public Bacheca getBacheca() {
        List<Proposta> aperte = new ArrayList<>();
        for (Proposta p : proposte) {
            if (p.getStato() == StatoProposta.APERTA) aperte.add(p);
        }
        return new Bacheca(aperte);
    }

    // -------------------------------------------------------------------------
    // V3 — Archivio proposte (storico stati + aderenti)
    // -------------------------------------------------------------------------

    // post: restituisce una vista immutabile di tutte le proposte in stato "post-validazione"
    //       (APERTA, CONFERMATA, ANNULLATA, CONCLUSA), utilizzata dalla view per mostrare
    //       lo storico degli stati e la lista aderenti a fini di consultazione.
    //       Sono escluse le bozze VALIDA (di sessione) e le proposte non ancora validate.
    public List<Proposta> getArchivioProposte() {
        List<Proposta> risultato = new ArrayList<>();
        for (Proposta p : proposte) {
            StatoProposta s = p.getStato();
            if (s == StatoProposta.APERTA
                    || s == StatoProposta.CONFERMATA
                    || s == StatoProposta.ANNULLATA
                    || s == StatoProposta.CONCLUSA) {
                risultato.add(p);
            }
        }
        return List.copyOf(risultato);
    }

    // -------------------------------------------------------------------------
    // V4 — Ritiro proposta (azione esplicita del configuratore)
    // -------------------------------------------------------------------------

    // post: restituisce le proposte che, in base a stato e data corrente, il configuratore
    //       può ritirare in questo momento (APERTA o CONFERMATA, e oggi < "Data" evento).
    //       La view usa questo elenco per mostrare solo scelte legittime.
    public List<Proposta> getProposteRitirabili() {
        List<Proposta> risultato = new ArrayList<>();
        LocalDate oggi = fornitoreTempo.oggi();
        for (Proposta p : proposte) {
            if (p.getStato() != StatoProposta.APERTA && p.getStato() != StatoProposta.CONFERMATA)
                continue;
            String s = p.getValori().get(Proposta.CAMPO_DATA_EVENTO);
            if (s == null) continue;
            try {
                LocalDate dataEvento = LocalDate.parse(s.trim());
                if (oggi.isBefore(dataEvento)) risultato.add(p);
            } catch (java.time.format.DateTimeParseException e) {
                // campo data evento malformato: salta silenziosamente.
            }
        }
        return List.copyOf(risultato);
    }

    // pre:  proposta != null && proposta ∈ getProposteRitirabili() (o comunque
    //       ritirabile secondo Proposta.ritira)
    // post: (i) proposta.ritira(fornitoreTempo) viene invocato con successo — imposta
    //           stato = RITIRATA, appende storico, notifica gli osservatori registrati
    //           (i Fruitore aderenti a questa proposta, rilegati alla costruzione del
    //           controller);
    //       (ii) proposte.json viene aggiornato (stato RITIRATA persistito);
    //       (iii) fruitori.json viene aggiornato (le notifiche appena depositate nei
    //             SpazioPersonale dei fruitori vengono persistite).
    //       Se ritira lancia IllegalStateException per vincoli non rispettati (stato,
    //       data evento superata), l'eccezione viene propagata alla view.
    public void ritiraProposta(Proposta proposta) throws IOException {
        if (proposta == null)
            throw new IllegalArgumentException("proposta non può essere null");
        if (!proposte.contains(proposta))
            throw new IllegalArgumentException("proposta non presente tra quelle in memoria");

        proposta.ritira(fornitoreTempo);
        archivioProposte.salvaTutte(proposte);
        archivioFruitori.salvaTutti(fruitori);
    }

    // -------------------------------------------------------------------------
    // V3 — Data simulata (modalità configuratore)
    // -------------------------------------------------------------------------

    // post: restituisce la data "corrente" secondo il fornitoreTempo attivo in
    //       questa sessione; utile alla view per mostrare all'utente quale data
    //       stiamo usando prima di offrirgli di modificarla.
    public LocalDate getDataCorrente() {
        return fornitoreTempo.oggi();
    }

    // post: restituisce true se il fornitoreTempo di questa sessione è
    //       TempoSimulato (i.e., data/tempo.json esisteva all'avvio),
    //       false altrimenti (TempoReale)
    public boolean isTempoSimulato() {
        return fornitoreTempo instanceof TempoSimulato;
    }

    // pre:  nuovaData != null
    // post: data/tempo.json contiene nuovaData. Se il fornitoreTempo di questa
    //       sessione è già TempoSimulato, viene aggiornato in-place così
    //       l'effetto è immediato; altrimenti la modifica si applicherà solo al
    //       prossimo riavvio (Main leggerà il file e istanzierà TempoSimulato).
    //       Restituisce true se il cambio è già attivo in questa sessione.
    public boolean impostaDataSimulata(LocalDate nuovaData) throws IOException {
        if (nuovaData == null)
            throw new IllegalArgumentException("nuovaData non può essere null");
        archivioTempoSimulato.salvaDataSimulata(nuovaData);
        if (fornitoreTempo instanceof TempoSimulato ts) {
            ts.impostaData(nuovaData.atTime(LocalTime.MIDNIGHT));
            return true;
        }
        return false;
    }

    // post: data/tempo.json rimosso. Il "downgrade" a TempoReale ha effetto solo
    //       al prossimo riavvio (in questa sessione fornitoreTempo resta quello
    //       istanziato da Main).
    public void rimuoviDataSimulata() throws IOException {
        archivioTempoSimulato.rimuoviDataSimulata();
    }

    // --- helper privati ---

    private Optional<Configuratore> trovaPerUsername(String username) {
        return configuratori.stream()
                .filter(c -> c.getUsername().equals(username))
                .findFirst();
    }

    private Optional<Categoria> trovaCategoriaPer(String nome) {
        return categorie.stream()
                .filter(c -> c.getNome().equals(nome))
                .findFirst();
    }
}

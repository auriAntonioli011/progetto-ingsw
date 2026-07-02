package it.unibs.ingsw.controller;

import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.ConfigurazioneGlobale;
import it.unibs.ingsw.model.Configuratore;
import it.unibs.ingsw.persistence.ArchivioCategorie;
import it.unibs.ingsw.persistence.ArchivioConfigurazione;
import it.unibs.ingsw.persistence.ArchivioConfiguratori;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    private ConfigurazioneGlobale configurazioneGlobale;
    private List<Categoria> categorie;
    private List<Configuratore> configuratori;
    private Configuratore configuratoreLoggato;

    // pre:  archivioConfigurazione != null && archivioCategorie != null
    //       && archivioConfiguratori != null
    // post: configurazioneGlobale, categorie e configuratori sono caricati dagli archivi;
    //       configuratoreLoggato == null
    public ControllerConfiguratore(ArchivioConfigurazione archivioConfigurazione,
                                    ArchivioCategorie archivioCategorie,
                                    ArchivioConfiguratori archivioConfiguratori) throws IOException {
        if (archivioConfigurazione == null)
            throw new IllegalArgumentException("archivioConfigurazione non può essere null");
        if (archivioCategorie == null)
            throw new IllegalArgumentException("archivioCategorie non può essere null");
        if (archivioConfiguratori == null)
            throw new IllegalArgumentException("archivioConfiguratori non può essere null");

        this.archivioConfigurazione = archivioConfigurazione;
        this.archivioCategorie = archivioCategorie;
        this.archivioConfiguratori = archivioConfiguratori;

        this.configurazioneGlobale = archivioConfigurazione.carica();
        this.categorie = archivioCategorie.caricaTutte();
        this.configuratori = archivioConfiguratori.caricaTutti();
        this.configuratoreLoggato = null;
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

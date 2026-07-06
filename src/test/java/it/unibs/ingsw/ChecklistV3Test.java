package it.unibs.ingsw;

import it.unibs.ingsw.controller.ControllerConfiguratore;
import it.unibs.ingsw.controller.ControllerFruitore;
import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.ConfigurazioneGlobale;
import it.unibs.ingsw.model.Configuratore;
import it.unibs.ingsw.model.Fruitore;
import it.unibs.ingsw.model.Notifica;
import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.StatoProposta;
import it.unibs.ingsw.model.TipoCampo;
import it.unibs.ingsw.persistence.ArchivioCategorie;
import it.unibs.ingsw.persistence.ArchivioConfigurazione;
import it.unibs.ingsw.persistence.ArchivioConfiguratori;
import it.unibs.ingsw.persistence.ArchivioFruitori;
import it.unibs.ingsw.persistence.ArchivioProposte;
import it.unibs.ingsw.persistence.ArchivioTempoSimulato;
import it.unibs.ingsw.util.FornitoreTempo;
import it.unibs.ingsw.util.TempoSimulato;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite E2E che copre i 13 punti della checklist V3.
 * Pilota Controller e Model direttamente (bypassando la View console) usando
 * TempoSimulato e archivi puntati su @TempDir per isolarsi dal data/ reale.
 */
class ChecklistV3Test {

    // Fixture temporale coerente in tutti i test.
    private static final LocalDate OGGI                = LocalDate.of(2026, 7, 1);
    private static final LocalDate TERMINE_ISCRIZIONE  = LocalDate.of(2026, 7, 31);
    private static final LocalDate DATA_EVENTO         = LocalDate.of(2026, 8, 5);
    private static final LocalDate DATA_CONCLUSIVA     = LocalDate.of(2026, 8, 6);
    private static final LocalDate POST_TERMINE        = LocalDate.of(2026, 8, 1);
    private static final LocalDate POST_CONCLUSIVA     = LocalDate.of(2026, 8, 7);

    @TempDir Path tempDir;

    private ConfigurazioneGlobale cfg;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        cfg = new ConfigurazioneGlobale();
        cfg.fissaCampiBase(List.of(
                new Campo("Titolo", TipoCampo.STRINGA, true),
                new Campo(Proposta.CAMPO_NUMERO_PARTECIPANTI, TipoCampo.INTERO, true),
                new Campo(Proposta.CAMPO_TERMINE_ISCRIZIONE, TipoCampo.DATA, true),
                new Campo(Proposta.CAMPO_DATA_EVENTO, TipoCampo.DATA, true),
                new Campo("Ora", TipoCampo.ORA, true),
                new Campo("Luogo", TipoCampo.STRINGA, true),
                new Campo("Quota individuale", TipoCampo.DECIMALE, true),
                new Campo(Proposta.CAMPO_DATA_CONCLUSIVA, TipoCampo.DATA, true)
        ));
        categoria = new Categoria("Sport");
    }

    // -------------------------------------------------------------------------
    // Helper di fixture
    // -------------------------------------------------------------------------

    private Map<String, String> valoriBaseValidi(int numeroPartecipanti) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Titolo", "Torneo di scacchi");
        m.put(Proposta.CAMPO_NUMERO_PARTECIPANTI, String.valueOf(numeroPartecipanti));
        m.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, TERMINE_ISCRIZIONE.toString());
        m.put(Proposta.CAMPO_DATA_EVENTO, DATA_EVENTO.toString());
        m.put("Ora", "18:00");
        m.put("Luogo", "Sala civica");
        m.put("Quota individuale", "10.50");
        m.put(Proposta.CAMPO_DATA_CONCLUSIVA, DATA_CONCLUSIVA.toString());
        return m;
    }

    private Proposta creaPropostaAperta(int numeroPartecipanti) {
        Proposta p = new Proposta(categoria, valoriBaseValidi(numeroPartecipanti));
        FornitoreTempo tempoValidazione = new TempoSimulato(OGGI);
        assertTrue(p.valida(tempoValidazione, cfg), "la proposta deve risultare VALIDA");
        p.marcaPubblicata(OGGI);
        assertEquals(StatoProposta.APERTA, p.getStato());
        return p;
    }

    private Fruitore fruitoreDefinitivo(String username) {
        Fruitore f = new Fruitore(username, "provv-" + username);
        f.confermaCredenzialiPersonali(username, "definitiva-" + username);
        return f;
    }

    private ControllerFruitore controllerFruitoreVuoto() throws IOException {
        return new ControllerFruitore(
                new ArchivioFruitori(tempDir.resolve("fruitori.json")),
                new ArchivioConfiguratori(tempDir.resolve("configuratori.json")),
                new ArchivioProposte(tempDir.resolve("proposte.json")),
                new TempoSimulato(OGGI)
        );
    }

    // -------------------------------------------------------------------------
    // #1  Username fruitore già usato da un configuratore → registrazione rifiutata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#1 registra fruitore con username in uso da configuratore → rifiutata")
    void univocita_cross_archivio_username() throws IOException {
        Path pathConf = tempDir.resolve("configuratori.json");
        ArchivioConfiguratori archConf = new ArchivioConfiguratori(pathConf);
        Configuratore mario = new Configuratore("mario", "provv");
        mario.confermaCredenzialiPersonali("mario", "definitiva");
        archConf.salvaTutti(List.of(mario));

        ControllerFruitore controller = new ControllerFruitore(
                new ArchivioFruitori(tempDir.resolve("fruitori.json")),
                archConf,
                new ArchivioProposte(tempDir.resolve("proposte.json")),
                new TempoSimulato(OGGI)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.registra("mario", "qualsiasi"));
        assertTrue(ex.getMessage().contains("mario"), "il messaggio deve citare l'username in conflitto");
    }

    // -------------------------------------------------------------------------
    // #2  Aderisci → username in getAderenti()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#2 fruitore aderisce a proposta APERTA → compare tra aderenti")
    void aderisci_aggiunge_username_alla_lista_aderenti() {
        Proposta p = creaPropostaAperta(10);
        Fruitore f = fruitoreDefinitivo("aurora");
        p.aderisci(f, new TempoSimulato(OGGI));
        assertTrue(p.getAderenti().contains("aurora"));
        assertEquals(1, p.getAderenti().size());
    }

    // -------------------------------------------------------------------------
    // #3  Doppia iscrizione stesso fruitore → rifiutata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#3 stessa aderisci due volte → IllegalStateException")
    void doppia_iscrizione_stesso_fruitore_rifiutata() {
        Proposta p = creaPropostaAperta(10);
        Fruitore f = fruitoreDefinitivo("aurora");
        FornitoreTempo t = new TempoSimulato(OGGI);
        p.aderisci(f, t);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> p.aderisci(f, t));
        assertTrue(ex.getMessage().contains("già iscritto"));
    }

    // -------------------------------------------------------------------------
    // #4  Oltre termine → rifiutata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#4 aderisci oltre il termine iscrizione → rifiutata")
    void iscrizione_oltre_termine_rifiutata() {
        Proposta p = creaPropostaAperta(10);
        Fruitore f = fruitoreDefinitivo("aurora");
        FornitoreTempo tOltre = new TempoSimulato(POST_TERMINE);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> p.aderisci(f, tOltre));
        assertTrue(ex.getMessage().contains("termine"));
    }

    // -------------------------------------------------------------------------
    // #5  Proposta piena → rifiutata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#5 proposta piena (aderenti == numero partecipanti) → rifiutata")
    void iscrizione_proposta_piena_rifiutata() {
        Proposta p = creaPropostaAperta(2);
        FornitoreTempo t = new TempoSimulato(OGGI);
        p.aderisci(fruitoreDefinitivo("a"), t);
        p.aderisci(fruitoreDefinitivo("b"), t);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> p.aderisci(fruitoreDefinitivo("c"), t));
        assertTrue(ex.getMessage().contains("massimo"));
    }

    // -------------------------------------------------------------------------
    // #6  APERTA → CONFERMATA/ANNULLATA + notifica con promemoria + storico
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#6 (piena) APERTA → CONFERMATA + notifica con promemoria completo")
    void transizione_a_confermata_con_promemoria_snapshot() {
        Proposta p = creaPropostaAperta(2);
        Fruitore a = fruitoreDefinitivo("a");
        Fruitore b = fruitoreDefinitivo("b");
        FornitoreTempo tPre = new TempoSimulato(OGGI);
        p.aderisci(a, tPre);
        p.aderisci(b, tPre);

        p.elaboraTransizione(new TempoSimulato(POST_TERMINE));

        assertEquals(StatoProposta.CONFERMATA, p.getStato());
        // Entrambi i fruitori ricevono la notifica.
        assertEquals(1, a.getSpazioPersonale().getNotifiche().size());
        assertEquals(1, b.getSpazioPersonale().getNotifiche().size());

        Notifica n = a.getSpazioPersonale().getNotifiche().get(0);
        assertEquals(StatoProposta.CONFERMATA, n.getNuovoStato());
        assertEquals(p.getId(), n.getIdProposta());
        assertEquals(POST_TERMINE, n.getData());

        // Promemoria completo: snapshot contiene Data, Ora, Luogo, Quota (non vuoti/null).
        Map<String, String> snap = n.getSnapshotValori();
        assertEquals(DATA_EVENTO.toString(), snap.get(Proposta.CAMPO_DATA_EVENTO));
        assertEquals("18:00", snap.get("Ora"));
        assertEquals("Sala civica", snap.get("Luogo"));
        assertEquals("10.50", snap.get("Quota individuale"));
    }

    @Test
    @DisplayName("#6 (non piena) APERTA → ANNULLATA")
    void transizione_a_annullata_se_non_piena() {
        Proposta p = creaPropostaAperta(5);
        Fruitore a = fruitoreDefinitivo("a");
        p.aderisci(a, new TempoSimulato(OGGI));

        p.elaboraTransizione(new TempoSimulato(POST_TERMINE));

        assertEquals(StatoProposta.ANNULLATA, p.getStato());
        Notifica n = a.getSpazioPersonale().getNotifiche().get(0);
        assertEquals(StatoProposta.ANNULLATA, n.getNuovoStato());
    }

    // -------------------------------------------------------------------------
    // #7  CONFERMATA → CONCLUSA
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#7 CONFERMATA → CONCLUSA oltre data conclusiva")
    void transizione_confermata_a_conclusa() {
        Proposta p = creaPropostaAperta(1);
        Fruitore a = fruitoreDefinitivo("a");
        p.aderisci(a, new TempoSimulato(OGGI));
        p.elaboraTransizione(new TempoSimulato(POST_TERMINE));
        assertEquals(StatoProposta.CONFERMATA, p.getStato());

        p.elaboraTransizione(new TempoSimulato(POST_CONCLUSIVA));
        assertEquals(StatoProposta.CONCLUSA, p.getStato());

        // Due notifiche complessive: CONFERMATA e CONCLUSA.
        List<Notifica> notifiche = a.getSpazioPersonale().getNotifiche();
        assertEquals(2, notifiche.size());
        assertEquals(StatoProposta.CONFERMATA, notifiche.get(0).getNuovoStato());
        assertEquals(StatoProposta.CONCLUSA, notifiche.get(1).getNuovoStato());
    }

    // -------------------------------------------------------------------------
    // #8  Storico: append-only + date coincidono con fornitoreTempo simulato
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#8 storico completo (APERTA, CONFERMATA, CONCLUSA) con date simulate")
    void storico_preservato_con_date_simulate() {
        Proposta p = creaPropostaAperta(1);
        Fruitore a = fruitoreDefinitivo("a");
        p.aderisci(a, new TempoSimulato(OGGI));
        p.elaboraTransizione(new TempoSimulato(POST_TERMINE));
        p.elaboraTransizione(new TempoSimulato(POST_CONCLUSIVA));

        List<Proposta.VoceStorico> s = p.getStorico();
        assertEquals(3, s.size(), "storico deve avere 3 voci: APERTA, CONFERMATA, CONCLUSA");
        assertEquals(StatoProposta.APERTA, s.get(0).stato());
        assertEquals(OGGI, s.get(0).data());
        assertEquals(StatoProposta.CONFERMATA, s.get(1).stato());
        assertEquals(POST_TERMINE, s.get(1).data());
        assertEquals(StatoProposta.CONCLUSA, s.get(2).stato());
        assertEquals(POST_CONCLUSIVA, s.get(2).data());
    }

    // -------------------------------------------------------------------------
    // #9  Persistenza spazio personale: roundtrip JSON
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#9 notifiche sopravvivono a save+load su ArchivioFruitori")
    void notifiche_persistite_roundtrip() throws IOException {
        Path pathFru = tempDir.resolve("fruitori.json");
        ArchivioFruitori arch = new ArchivioFruitori(pathFru);

        Fruitore f = fruitoreDefinitivo("aurora");
        Notifica n = new Notifica(
                "uuid-fake-1", "Sport", StatoProposta.CONFERMATA, POST_TERMINE,
                Map.of("Data", DATA_EVENTO.toString(), "Ora", "18:00")
        );
        // Passiamo per l'API pubblica (Fruitore.aggiorna) — richiede una Proposta non null.
        Proposta dummyForObserverContract = new Proposta(categoria, valoriBaseValidi(1));
        f.aggiorna(dummyForObserverContract, n);
        assertEquals(1, f.getSpazioPersonale().getNotifiche().size());

        arch.salvaTutti(List.of(f));
        List<Fruitore> ricaricati = arch.caricaTutti();
        assertEquals(1, ricaricati.size());
        Fruitore r = ricaricati.get(0);
        List<Notifica> notifiche = r.getSpazioPersonale().getNotifiche();
        assertEquals(1, notifiche.size());
        assertEquals("uuid-fake-1", notifiche.get(0).getIdProposta());
        assertEquals(StatoProposta.CONFERMATA, notifiche.get(0).getNuovoStato());
        // Snapshot preservato dopo deserializzazione.
        assertEquals(DATA_EVENTO.toString(), notifiche.get(0).getSnapshotValori().get("Data"));
    }

    // -------------------------------------------------------------------------
    // #10  Cancella una notifica specifica → sparisce solo quella
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#10 rimuoviNotifica(idx) rimuove solo la notifica in quella posizione")
    void cancella_notifica_selettivamente() {
        Fruitore f = fruitoreDefinitivo("aurora");
        Proposta dummy = new Proposta(categoria, valoriBaseValidi(1));
        Notifica n1 = new Notifica("id1", "Sport", StatoProposta.CONFERMATA, POST_TERMINE, Map.of());
        Notifica n2 = new Notifica("id2", "Sport", StatoProposta.CONCLUSA, POST_CONCLUSIVA, Map.of());
        Notifica n3 = new Notifica("id3", "Sport", StatoProposta.ANNULLATA, POST_TERMINE, Map.of());
        f.aggiorna(dummy, n1);
        f.aggiorna(dummy, n2);
        f.aggiorna(dummy, n3);
        assertEquals(3, f.getSpazioPersonale().getNotifiche().size());

        f.getSpazioPersonale().rimuoviNotifica(1); // rimuove n2

        List<Notifica> rimaste = f.getSpazioPersonale().getNotifiche();
        assertEquals(2, rimaste.size());
        assertEquals("id1", rimaste.get(0).getIdProposta());
        assertEquals("id3", rimaste.get(1).getIdProposta());
    }

    @Test
    @DisplayName("#10b rimuoviNotifica con indice fuori intervallo → eccezione, lista intatta")
    void cancella_notifica_indice_invalido() {
        Fruitore f = fruitoreDefinitivo("aurora");
        Proposta dummy = new Proposta(categoria, valoriBaseValidi(1));
        f.aggiorna(dummy, new Notifica("id1", "Sport", StatoProposta.CONFERMATA, POST_TERMINE, Map.of()));

        assertThrows(IndexOutOfBoundsException.class,
                () -> f.getSpazioPersonale().rimuoviNotifica(5));
        assertEquals(1, f.getSpazioPersonale().getNotifiche().size());
    }

    // -------------------------------------------------------------------------
    // #11  Archivio proposte → storico + aderenti congelati
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#11 archivio proposte mostra aderenti congelati dopo CONFERMATA")
    void archivio_proposte_congelato_dopo_transizione() throws IOException {
        Path pathProp = tempDir.resolve("proposte.json");
        Path pathCfg  = tempDir.resolve("configurazione.json");
        Path pathCat  = tempDir.resolve("categorie.json");
        Path pathCnf  = tempDir.resolve("configuratori.json");
        Path pathTmp  = tempDir.resolve("tempo.json");

        // Setup persistente minimo per costruire ControllerConfiguratore.
        new ArchivioConfigurazione(pathCfg).salva(cfg);
        new ArchivioCategorie(pathCat).salvaTutte(List.of(categoria));
        Configuratore c = new Configuratore("cfg", "provv");
        c.confermaCredenzialiPersonali("cfg", "def");
        new ArchivioConfiguratori(pathCnf).salvaTutti(List.of(c));

        Proposta p = creaPropostaAperta(2);
        Fruitore a = fruitoreDefinitivo("a");
        Fruitore b = fruitoreDefinitivo("b");
        p.aderisci(a, new TempoSimulato(OGGI));
        p.aderisci(b, new TempoSimulato(OGGI));
        p.elaboraTransizione(new TempoSimulato(POST_TERMINE));
        assertEquals(StatoProposta.CONFERMATA, p.getStato());

        new ArchivioProposte(pathProp).salvaTutte(List.of(p));

        ControllerConfiguratore ctrl = new ControllerConfiguratore(
                new ArchivioConfigurazione(pathCfg),
                new ArchivioCategorie(pathCat),
                new ArchivioConfiguratori(pathCnf),
                new ArchivioProposte(pathProp),
                new ArchivioFruitori(tempDir.resolve("fruitori.json")),
                new ArchivioTempoSimulato(pathTmp),
                new TempoSimulato(POST_TERMINE)
        );
        List<Proposta> archivio = ctrl.getArchivioProposte();
        assertEquals(1, archivio.size());
        Proposta letta = archivio.get(0);
        assertEquals(StatoProposta.CONFERMATA, letta.getStato());
        // Aderenti congelati: stessi due usernames anche dopo la transizione.
        assertEquals(List.of("a", "b"), letta.getAderenti());
        // Storico contiene APERTA + CONFERMATA (VoceStorico ordine cronologico).
        List<Proposta.VoceStorico> s = letta.getStorico();
        assertEquals(2, s.size());
        assertEquals(StatoProposta.APERTA, s.get(0).stato());
        assertEquals(StatoProposta.CONFERMATA, s.get(1).stato());
    }

    // -------------------------------------------------------------------------
    // #12  Motore transizioni via inizializzaSessione() persiste notifiche
    //      per tutti gli aderenti, indipendentemente dal login successivo
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("#12 inizializzaSessione() persiste notifiche di TUTTI gli aderenti")
    void inizializza_sessione_notifica_tutti_gli_aderenti() throws IOException {
        Path pathFru  = tempDir.resolve("fruitori.json");
        Path pathProp = tempDir.resolve("proposte.json");
        Path pathCnf  = tempDir.resolve("configuratori.json");

        // Stato pre-transizione: 2 fruitori aderenti a una proposta piena.
        Fruitore aurora = fruitoreDefinitivo("aurora");
        Fruitore bea    = fruitoreDefinitivo("bea");
        new ArchivioFruitori(pathFru).salvaTutti(List.of(aurora, bea));
        new ArchivioConfiguratori(pathCnf).salvaTutti(List.of());

        Proposta p = creaPropostaAperta(2);
        p.aderisci(aurora, new TempoSimulato(OGGI));
        p.aderisci(bea, new TempoSimulato(OGGI));
        new ArchivioProposte(pathProp).salvaTutte(List.of(p));

        // Sessione 1: ControllerFruitore parte con tempo post-termine, invoca
        // inizializzaSessione(). Nessun login: la scelta ruolo sarà "Configuratore".
        ControllerFruitore ctrl1 = new ControllerFruitore(
                new ArchivioFruitori(pathFru),
                new ArchivioConfiguratori(pathCnf),
                new ArchivioProposte(pathProp),
                new TempoSimulato(POST_TERMINE)
        );
        ctrl1.inizializzaSessione();
        // Nessun login effettuato.
        assertNull(ctrl1.getFruitoreLoggato());

        // Sessione 2: aurora si logga a un successivo riavvio dell'app.
        ControllerFruitore ctrl2 = new ControllerFruitore(
                new ArchivioFruitori(pathFru),
                new ArchivioConfiguratori(pathCnf),
                new ArchivioProposte(pathProp),
                new TempoSimulato(POST_TERMINE)
        );
        // Nota: non chiamiamo inizializzaSessione() qui per verificare che la
        // notifica esiste già su disco, non appena caricata.
        ctrl2.login("aurora", "definitiva-aurora");
        List<Notifica> notificheAurora = ctrl2.getSpazioPersonale().getNotifiche();
        assertEquals(1, notificheAurora.size(), "aurora deve trovare la notifica scritta nella sessione 1");
        assertEquals(StatoProposta.CONFERMATA, notificheAurora.get(0).getNuovoStato());
        assertEquals(p.getId(), notificheAurora.get(0).getIdProposta());

        // Anche bea (che non si è ancora nemmeno loggata) trova la sua.
        ControllerFruitore ctrl3 = new ControllerFruitore(
                new ArchivioFruitori(pathFru),
                new ArchivioConfiguratori(pathCnf),
                new ArchivioProposte(pathProp),
                new TempoSimulato(POST_TERMINE)
        );
        ctrl3.login("bea", "definitiva-bea");
        assertEquals(1, ctrl3.getSpazioPersonale().getNotifiche().size());
    }
}

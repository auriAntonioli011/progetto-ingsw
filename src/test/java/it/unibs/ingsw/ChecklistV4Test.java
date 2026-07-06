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
 * Suite E2E V4: disdetta fruitore + ritiro configuratore.
 * Stessi pattern di ChecklistV3Test (Controller diretti, TempoSimulato, @TempDir).
 */
class ChecklistV4Test {

    // Fixture temporale.
    private static final LocalDate OGGI                = LocalDate.of(2026, 7, 1);
    private static final LocalDate TERMINE_ISCRIZIONE  = LocalDate.of(2026, 7, 31);
    private static final LocalDate DATA_EVENTO         = LocalDate.of(2026, 8, 5);
    private static final LocalDate DATA_CONCLUSIVA     = LocalDate.of(2026, 8, 6);
    private static final LocalDate POST_TERMINE        = LocalDate.of(2026, 8, 1);
    private static final LocalDate GIORNO_PRE_EVENTO   = LocalDate.of(2026, 8, 4);
    private static final LocalDate GIORNO_EVENTO       = LocalDate.of(2026, 8, 5);

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
        assertTrue(p.valida(tempoValidazione, cfg));
        p.marcaPubblicata(OGGI);
        return p;
    }

    private Fruitore fruitoreDefinitivo(String username) {
        Fruitore f = new Fruitore(username, "provv-" + username);
        f.confermaCredenzialiPersonali(username, "definitiva-" + username);
        return f;
    }

    // -------------------------------------------------------------------------
    // DISDETTA
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("disdetta valida entro il termine → aderenti vuota, nessun errore")
    void disdetta_valida_entro_termine() {
        Proposta p = creaPropostaAperta(5);
        Fruitore f = fruitoreDefinitivo("aurora");
        FornitoreTempo t = new TempoSimulato(OGGI);

        p.aderisci(f, t);
        assertEquals(List.of("aurora"), p.getAderenti());

        p.disdici(f, t);
        assertTrue(p.getAderenti().isEmpty());
        // Lo storico NON registra la disdetta (non è un cambio di stato della Proposta,
        // è solo un cambio dell'elenco aderenti). Verifica.
        assertEquals(1, p.getStorico().size());
        assertEquals(StatoProposta.APERTA, p.getStorico().get(0).stato());
    }

    @Test
    @DisplayName("disdetta oltre il termine iscrizione → IllegalStateException")
    void disdetta_oltre_termine_rifiutata() {
        Proposta p = creaPropostaAperta(5);
        Fruitore f = fruitoreDefinitivo("aurora");
        p.aderisci(f, new TempoSimulato(OGGI));

        FornitoreTempo tPost = new TempoSimulato(POST_TERMINE);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> p.disdici(f, tPost));
        assertTrue(ex.getMessage().contains("termine"));
        assertEquals(List.of("aurora"), p.getAderenti(), "aderenti invariati dopo disdetta rifiutata");
    }

    @Test
    @DisplayName("ri-iscrizione dopo disdetta consentita entro il termine")
    void ri_iscrizione_dopo_disdetta() {
        Proposta p = creaPropostaAperta(5);
        Fruitore f = fruitoreDefinitivo("aurora");
        FornitoreTempo t = new TempoSimulato(OGGI);

        p.aderisci(f, t);
        p.disdici(f, t);
        assertTrue(p.getAderenti().isEmpty());
        p.aderisci(f, t);
        assertEquals(List.of("aurora"), p.getAderenti());
    }

    @Test
    @DisplayName("disdici quando stato non è più APERTA → rifiutata (Nota 10)")
    void disdici_su_stato_non_aperta_rifiutata() {
        Proposta p = creaPropostaAperta(5);
        Fruitore f = fruitoreDefinitivo("aurora");
        p.aderisci(f, new TempoSimulato(OGGI));

        // Ritiro simulato → stato RITIRATA
        p.ritira(new TempoSimulato(GIORNO_PRE_EVENTO));
        assertEquals(StatoProposta.RITIRATA, p.getStato());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> p.disdici(f, new TempoSimulato(OGGI)));
        assertTrue(ex.getMessage().contains("APERTA"));
    }

    @Test
    @DisplayName("disdici di fruitore non iscritto → IllegalStateException")
    void disdici_fruitore_non_iscritto() {
        Proposta p = creaPropostaAperta(5);
        Fruitore f = fruitoreDefinitivo("aurora");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> p.disdici(f, new TempoSimulato(OGGI)));
        assertTrue(ex.getMessage().contains("non risulta iscritto"));
    }

    // -------------------------------------------------------------------------
    // RITIRO
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ritiro proposta APERTA prima del giorno evento → stato RITIRATA + notifiche + fuori bacheca")
    void ritiro_aperta_prima_evento() {
        Proposta p = creaPropostaAperta(5);
        Fruitore a = fruitoreDefinitivo("a");
        Fruitore b = fruitoreDefinitivo("b");
        p.aderisci(a, new TempoSimulato(OGGI));
        p.aderisci(b, new TempoSimulato(OGGI));

        p.ritira(new TempoSimulato(GIORNO_PRE_EVENTO));

        assertEquals(StatoProposta.RITIRATA, p.getStato());
        // Bacheca è filtrata su APERTA: verifichiamo che la proposta cambia stato e
        // quindi non compare più in un filtro banale APERTA.
        assertNotEquals(StatoProposta.APERTA, p.getStato());
        // Aderenti congelati.
        assertEquals(List.of("a", "b"), p.getAderenti());
        // Notifica ricevuta da entrambi.
        assertEquals(1, a.getSpazioPersonale().getNotifiche().size());
        assertEquals(1, b.getSpazioPersonale().getNotifiche().size());
        Notifica na = a.getSpazioPersonale().getNotifiche().get(0);
        assertEquals(StatoProposta.RITIRATA, na.getNuovoStato());
        assertEquals(p.getId(), na.getIdProposta());
        // Snapshot contiene tutti i valori originali (non-null, non-blank).
        assertEquals(DATA_EVENTO.toString(), na.getSnapshotValori().get(Proposta.CAMPO_DATA_EVENTO));
    }

    @Test
    @DisplayName("ritiro proposta CONFERMATA prima del giorno evento → RITIRATA + notifiche")
    void ritiro_confermata_prima_evento() {
        Proposta p = creaPropostaAperta(1);
        Fruitore a = fruitoreDefinitivo("a");
        p.aderisci(a, new TempoSimulato(OGGI));
        // Motore transizioni: APERTA → CONFERMATA (piena)
        p.elaboraTransizione(new TempoSimulato(POST_TERMINE));
        assertEquals(StatoProposta.CONFERMATA, p.getStato());

        p.ritira(new TempoSimulato(GIORNO_PRE_EVENTO));
        assertEquals(StatoProposta.RITIRATA, p.getStato());

        List<Notifica> nx = a.getSpazioPersonale().getNotifiche();
        assertEquals(2, nx.size(), "prima CONFERMATA, poi RITIRATA");
        assertEquals(StatoProposta.CONFERMATA, nx.get(0).getNuovoStato());
        assertEquals(StatoProposta.RITIRATA, nx.get(1).getNuovoStato());
    }

    @Test
    @DisplayName("ritiro il giorno dell'evento (oggi == data) → rifiutato")
    void ritiro_giorno_evento_rifiutato() {
        Proposta p = creaPropostaAperta(5);
        Fruitore f = fruitoreDefinitivo("aurora");
        p.aderisci(f, new TempoSimulato(OGGI));

        // Data evento è 2026-08-05. Test oggi == 2026-08-05: `!oggi.isBefore(dataEvento)`.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> p.ritira(new TempoSimulato(GIORNO_EVENTO)));
        assertTrue(ex.getMessage().contains("data evento"));
    }

    @Test
    @DisplayName("ritiro proposta ANNULLATA → rifiutato (stato non ammesso)")
    void ritiro_stato_non_ammesso_rifiutato() {
        Proposta p = creaPropostaAperta(5);
        Fruitore f = fruitoreDefinitivo("aurora");
        p.aderisci(f, new TempoSimulato(OGGI));
        // Motore: non piena → ANNULLATA
        p.elaboraTransizione(new TempoSimulato(POST_TERMINE));
        assertEquals(StatoProposta.ANNULLATA, p.getStato());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> p.ritira(new TempoSimulato(POST_TERMINE)));
        assertTrue(ex.getMessage().contains("APERTA") || ex.getMessage().contains("CONFERMATA"));
    }

    @Test
    @DisplayName("storico dopo ritiro: le voci precedenti sopravvivono, RITIRATA è aggiunta in coda")
    void storico_dopo_ritiro_append_only() {
        Proposta p = creaPropostaAperta(1);
        Fruitore a = fruitoreDefinitivo("a");
        p.aderisci(a, new TempoSimulato(OGGI));
        p.elaboraTransizione(new TempoSimulato(POST_TERMINE));
        // Storia: [APERTA@OGGI, CONFERMATA@POST_TERMINE]
        assertEquals(2, p.getStorico().size());

        LocalDate dataRitiro = GIORNO_PRE_EVENTO;
        p.ritira(new TempoSimulato(dataRitiro));
        List<Proposta.VoceStorico> s = p.getStorico();
        assertEquals(3, s.size());
        assertEquals(StatoProposta.APERTA, s.get(0).stato());
        assertEquals(StatoProposta.CONFERMATA, s.get(1).stato());
        assertEquals(StatoProposta.RITIRATA, s.get(2).stato());
        assertEquals(dataRitiro, s.get(2).data());
    }

    @Test
    @DisplayName("elaboraTransizione su proposta RITIRATA → no-op (stato invariato, nessuna notifica extra)")
    void ritirata_terminale_per_motore_transizioni() {
        Proposta p = creaPropostaAperta(5);
        Fruitore a = fruitoreDefinitivo("a");
        p.aderisci(a, new TempoSimulato(OGGI));
        p.ritira(new TempoSimulato(GIORNO_PRE_EVENTO));
        int notificheDopoRitiro = a.getSpazioPersonale().getNotifiche().size();
        int storicoDopoRitiro = p.getStorico().size();

        // Passa il tempo, tanto: motore non deve fare niente
        p.elaboraTransizione(new TempoSimulato(GIORNO_EVENTO));
        p.elaboraTransizione(new TempoSimulato(DATA_CONCLUSIVA.plusDays(5)));

        assertEquals(StatoProposta.RITIRATA, p.getStato());
        assertEquals(notificheDopoRitiro, a.getSpazioPersonale().getNotifiche().size());
        assertEquals(storicoDopoRitiro, p.getStorico().size());
    }

    // -------------------------------------------------------------------------
    // Integrazione: ritiro attraverso il ControllerConfiguratore + persistenza fruitori
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ritiraProposta persiste stato + notifiche a fruitori.json (fix osservatori V4)")
    void ritira_via_controller_configuratore_persiste_notifiche() throws IOException {
        Path pathProp = tempDir.resolve("proposte.json");
        Path pathFru  = tempDir.resolve("fruitori.json");
        Path pathCfg  = tempDir.resolve("configurazione.json");
        Path pathCat  = tempDir.resolve("categorie.json");
        Path pathCnf  = tempDir.resolve("configuratori.json");
        Path pathTmp  = tempDir.resolve("tempo.json");

        new ArchivioConfigurazione(pathCfg).salva(cfg);
        new ArchivioCategorie(pathCat).salvaTutte(List.of(categoria));
        Configuratore c = new Configuratore("cfg", "provv");
        c.confermaCredenzialiPersonali("cfg", "def");
        new ArchivioConfiguratori(pathCnf).salvaTutti(List.of(c));

        Fruitore aurora = fruitoreDefinitivo("aurora");
        Fruitore bea    = fruitoreDefinitivo("bea");
        new ArchivioFruitori(pathFru).salvaTutti(List.of(aurora, bea));

        Proposta p = creaPropostaAperta(5);
        p.aderisci(aurora, new TempoSimulato(OGGI));
        p.aderisci(bea, new TempoSimulato(OGGI));
        new ArchivioProposte(pathProp).salvaTutte(List.of(p));

        // Sessione "configuratore": qui il ricollega osservatori accade nel costruttore.
        ControllerConfiguratore ctrl = new ControllerConfiguratore(
                new ArchivioConfigurazione(pathCfg),
                new ArchivioCategorie(pathCat),
                new ArchivioConfiguratori(pathCnf),
                new ArchivioProposte(pathProp),
                new ArchivioFruitori(pathFru),
                new ArchivioTempoSimulato(pathTmp),
                new TempoSimulato(GIORNO_PRE_EVENTO)
        );

        List<Proposta> ritirabili = ctrl.getProposteRitirabili();
        assertEquals(1, ritirabili.size());
        ctrl.ritiraProposta(ritirabili.get(0));

        // Verifica JSON su disco: proposta RITIRATA + fruitori con notifica.
        List<Proposta> propRilette = new ArchivioProposte(pathProp).caricaTutte();
        assertEquals(1, propRilette.size());
        assertEquals(StatoProposta.RITIRATA, propRilette.get(0).getStato());
        assertEquals(List.of("aurora", "bea"), propRilette.get(0).getAderenti());

        List<Fruitore> fruRiletti = new ArchivioFruitori(pathFru).caricaTutti();
        assertEquals(2, fruRiletti.size());
        for (Fruitore f : fruRiletti) {
            List<Notifica> n = f.getSpazioPersonale().getNotifiche();
            assertEquals(1, n.size(), "fruitore " + f.getUsername() + " deve avere 1 notifica");
            assertEquals(StatoProposta.RITIRATA, n.get(0).getNuovoStato());
            assertEquals(p.getId(), n.get(0).getIdProposta());
        }

        // Al login del fruitore in una futura sessione, la notifica è già in fruitori.json.
        ControllerFruitore ctrlF = new ControllerFruitore(
                new ArchivioFruitori(pathFru),
                new ArchivioConfiguratori(pathCnf),
                new ArchivioProposte(pathProp),
                new TempoSimulato(GIORNO_PRE_EVENTO)
        );
        ctrlF.login("aurora", "definitiva-aurora");
        assertEquals(1, ctrlF.getSpazioPersonale().getNotifiche().size());
    }

    @Test
    @DisplayName("disdici via ControllerFruitore aggiorna aderenti su disco")
    void disdici_via_controller_persiste() throws IOException {
        Path pathProp = tempDir.resolve("proposte.json");
        Path pathFru  = tempDir.resolve("fruitori.json");
        Path pathCnf  = tempDir.resolve("configuratori.json");

        Fruitore aurora = fruitoreDefinitivo("aurora");
        new ArchivioFruitori(pathFru).salvaTutti(List.of(aurora));
        new ArchivioConfiguratori(pathCnf).salvaTutti(List.of());

        Proposta p = creaPropostaAperta(5);
        p.aderisci(aurora, new TempoSimulato(OGGI));
        new ArchivioProposte(pathProp).salvaTutte(List.of(p));

        ControllerFruitore ctrl = new ControllerFruitore(
                new ArchivioFruitori(pathFru),
                new ArchivioConfiguratori(pathCnf),
                new ArchivioProposte(pathProp),
                new TempoSimulato(OGGI)
        );
        ctrl.login("aurora", "definitiva-aurora");
        List<Proposta> mie = ctrl.getProposteAderite();
        assertEquals(1, mie.size());

        ctrl.disdici(mie.get(0));

        // Rilettura da disco: aderenti vuoto.
        List<Proposta> rilette = new ArchivioProposte(pathProp).caricaTutte();
        assertTrue(rilette.get(0).getAderenti().isEmpty(),
                "aderenti deve essere vuoto dopo disdici + persist");
    }
}

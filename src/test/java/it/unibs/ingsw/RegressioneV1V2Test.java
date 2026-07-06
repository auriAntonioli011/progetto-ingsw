package it.unibs.ingsw;

import it.unibs.ingsw.controller.ControllerConfiguratore;
import it.unibs.ingsw.model.Bacheca;
import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.Configuratore;
import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.StatoProposta;
import it.unibs.ingsw.model.TipoCampo;
import it.unibs.ingsw.persistence.ArchivioCategorie;
import it.unibs.ingsw.persistence.ArchivioConfigurazione;
import it.unibs.ingsw.persistence.ArchivioConfiguratori;
import it.unibs.ingsw.persistence.ArchivioProposte;
import it.unibs.ingsw.persistence.ArchivioTempoSimulato;
import it.unibs.ingsw.util.TempoSimulato;
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
 * #13 della checklist: regressione flussi V1/V2.
 * Verifica che le funzioni dei tag v1 e v2 rimangano funzionanti dopo i cambi V3.
 */
class RegressioneV1V2Test {

    private static final LocalDate OGGI = LocalDate.of(2026, 7, 1);

    @TempDir Path tempDir;

    private ControllerConfiguratore controller(Path dir) throws IOException {
        return new ControllerConfiguratore(
                new ArchivioConfigurazione(dir.resolve("configurazione.json")),
                new ArchivioCategorie(dir.resolve("categorie.json")),
                new ArchivioConfiguratori(dir.resolve("configuratori.json")),
                new ArchivioProposte(dir.resolve("proposte.json")),
                new ArchivioTempoSimulato(dir.resolve("tempo.json")),
                new TempoSimulato(OGGI)
        );
    }

    // -------------------------------------------------------------------------
    // V1 — primo accesso
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V1 — primo accesso configuratore + conferma credenziali personali")
    void v1_primo_accesso_configuratore() throws IOException {
        ControllerConfiguratore c = controller(tempDir);
        assertTrue(c.isPrimoAccesso());

        Configuratore provv = c.primoAccesso("admin", "admin");
        c.confermaCredenzialiPersonali(provv, "aurora", "aurora123");

        assertEquals("aurora", c.getConfiguratoreLoggato().getUsername());
        assertFalse(c.isPrimoAccesso(), "dopo la conferma non è più primo accesso");

        // Ricarica su nuovo controller: le credenziali persistono e il login funziona.
        ControllerConfiguratore c2 = controller(tempDir);
        assertFalse(c2.isPrimoAccesso());
        Configuratore loggato = c2.login("aurora", "aurora123");
        assertEquals("aurora", loggato.getUsername());
        assertFalse(loggato.isCredenzialiProvvisorie());
    }

    // -------------------------------------------------------------------------
    // V1 — campi base
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V1 — fissa campi base, non si possono rifissare, comune con nome uguale a base rifiutato")
    void v1_campi_base_fissabili_una_sola_volta() throws IOException {
        ControllerConfiguratore c = controller(tempDir);
        c.fissaCampiBase(List.of(
                new Campo("Titolo", TipoCampo.STRINGA, true),
                new Campo("Numero", TipoCampo.INTERO, true)
        ));
        assertTrue(c.getConfigurazioneGlobale().campiBaseFissati());

        assertThrows(IllegalStateException.class,
                () -> c.fissaCampiBase(List.of(new Campo("Altro", TipoCampo.STRINGA, false))));

        // Campo comune con nome uguale a un campo base → rifiutato
        assertThrows(IllegalArgumentException.class,
                () -> c.impostaCampiComuni(List.of(new Campo("Titolo", TipoCampo.STRINGA, false))));
    }

    // -------------------------------------------------------------------------
    // V1 — categorie + campi specifici
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V1 — crea/rimuovi categoria + aggiungi/rimuovi campo specifico")
    void v1_categoria_e_campi_specifici() throws IOException {
        ControllerConfiguratore c = controller(tempDir);
        c.fissaCampiBase(List.of(new Campo("Titolo", TipoCampo.STRINGA, true)));

        Categoria sport = c.creaCategoria("Sport");
        assertEquals("Sport", sport.getNome());
        assertEquals(1, c.getCategorie().size());

        // Duplicato rifiutato.
        assertThrows(IllegalArgumentException.class, () -> c.creaCategoria("Sport"));

        c.aggiungiCampoSpecifico("Sport", new Campo("Disciplina", TipoCampo.STRINGA, false));
        assertEquals(1, c.getCategorie().get(0).getCampiSpecifici().size());

        // Specifico con nome uguale al base → rifiutato.
        assertThrows(IllegalArgumentException.class,
                () -> c.aggiungiCampoSpecifico("Sport", new Campo("Titolo", TipoCampo.STRINGA, false)));

        c.rimuoviCampoSpecifico("Sport", "Disciplina");
        assertTrue(c.getCategorie().get(0).getCampiSpecifici().isEmpty());

        c.rimuoviCategoria("Sport");
        assertTrue(c.getCategorie().isEmpty());
    }

    // -------------------------------------------------------------------------
    // V2 — creazione + validazione + pubblicazione + bacheca + persistenza
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("V2 — crea proposta valida, pubblica, appare in bacheca, sopravvive a riavvio")
    void v2_ciclo_proposta_valida_e_persistente() throws IOException {
        ControllerConfiguratore c = controller(tempDir);
        c.fissaCampiBase(List.of(
                new Campo("Titolo", TipoCampo.STRINGA, true),
                new Campo(Proposta.CAMPO_NUMERO_PARTECIPANTI, TipoCampo.INTERO, true),
                new Campo(Proposta.CAMPO_TERMINE_ISCRIZIONE, TipoCampo.DATA, true),
                new Campo(Proposta.CAMPO_DATA_EVENTO, TipoCampo.DATA, true),
                new Campo(Proposta.CAMPO_DATA_CONCLUSIVA, TipoCampo.DATA, true)
        ));
        Categoria cat = c.creaCategoria("Sport");

        Map<String, String> valori = new LinkedHashMap<>();
        valori.put("Titolo", "Trekking");
        valori.put(Proposta.CAMPO_NUMERO_PARTECIPANTI, "5");
        valori.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, "2026-07-15");
        valori.put(Proposta.CAMPO_DATA_EVENTO, "2026-07-20");
        valori.put(Proposta.CAMPO_DATA_CONCLUSIVA, "2026-07-21");

        Proposta p = c.creaProposta(cat, valori);
        assertEquals(StatoProposta.VALIDA, p.getStato());

        c.richiediPubblicazione(p);
        assertEquals(StatoProposta.APERTA, p.getStato());
        assertEquals(OGGI, p.getDataPubblicazione());

        // Bacheca contiene la proposta APERTA.
        Bacheca b = c.getBacheca();
        assertFalse(b.isVuota());
        assertEquals(1, b.tutte().size());

        // Riavvio: nuova istanza controller legge dallo stesso tempDir.
        ControllerConfiguratore c2 = controller(tempDir);
        assertEquals(1, c2.getBacheca().tutte().size(),
                "la proposta APERTA deve sopravvivere al riavvio");
    }

    @Test
    @DisplayName("V2 — proposta con termine iscrizione a meno di 2 giorni dalla data evento → NON valida")
    void v2_termine_troppo_vicino_all_evento_non_valida() throws IOException {
        ControllerConfiguratore c = controller(tempDir);
        c.fissaCampiBase(List.of(
                new Campo("Titolo", TipoCampo.STRINGA, true),
                new Campo(Proposta.CAMPO_NUMERO_PARTECIPANTI, TipoCampo.INTERO, true),
                new Campo(Proposta.CAMPO_TERMINE_ISCRIZIONE, TipoCampo.DATA, true),
                new Campo(Proposta.CAMPO_DATA_EVENTO, TipoCampo.DATA, true),
                new Campo(Proposta.CAMPO_DATA_CONCLUSIVA, TipoCampo.DATA, true)
        ));
        Categoria cat = c.creaCategoria("Sport");

        Map<String, String> valori = new LinkedHashMap<>();
        valori.put("Titolo", "X");
        valori.put(Proposta.CAMPO_NUMERO_PARTECIPANTI, "5");
        valori.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, "2026-07-15");
        valori.put(Proposta.CAMPO_DATA_EVENTO, "2026-07-16"); // solo 1 giorno dopo
        valori.put(Proposta.CAMPO_DATA_CONCLUSIVA, "2026-07-17");

        Proposta p = c.creaProposta(cat, valori);
        assertNull(p.getStato(), "proposta non deve raggiungere VALIDA");
        assertThrows(IllegalStateException.class, () -> c.richiediPubblicazione(p));
    }

    @Test
    @DisplayName("V2 — proposta con campo obbligatorio mancante → NON valida")
    void v2_campo_obbligatorio_mancante_non_valida() throws IOException {
        ControllerConfiguratore c = controller(tempDir);
        c.fissaCampiBase(List.of(
                new Campo("Titolo", TipoCampo.STRINGA, true),
                new Campo(Proposta.CAMPO_NUMERO_PARTECIPANTI, TipoCampo.INTERO, true),
                new Campo(Proposta.CAMPO_TERMINE_ISCRIZIONE, TipoCampo.DATA, true),
                new Campo(Proposta.CAMPO_DATA_EVENTO, TipoCampo.DATA, true),
                new Campo(Proposta.CAMPO_DATA_CONCLUSIVA, TipoCampo.DATA, true)
        ));
        Categoria cat = c.creaCategoria("Sport");

        Map<String, String> valori = new LinkedHashMap<>();
        // Titolo mancante volontariamente.
        valori.put(Proposta.CAMPO_NUMERO_PARTECIPANTI, "5");
        valori.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, "2026-07-15");
        valori.put(Proposta.CAMPO_DATA_EVENTO, "2026-07-20");
        valori.put(Proposta.CAMPO_DATA_CONCLUSIVA, "2026-07-21");

        Proposta p = c.creaProposta(cat, valori);
        assertNull(p.getStato());
    }
}

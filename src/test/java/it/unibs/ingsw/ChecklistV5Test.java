package it.unibs.ingsw;

import it.unibs.ingsw.controller.ControllerConfiguratore;
import it.unibs.ingsw.controller.ImportatoreBatch;
import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.ConfigurazioneGlobale;
import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.StatoProposta;
import it.unibs.ingsw.model.TipoCampo;
import it.unibs.ingsw.persistence.ArchivioCategorie;
import it.unibs.ingsw.persistence.ArchivioConfigurazione;
import it.unibs.ingsw.persistence.ArchivioConfiguratori;
import it.unibs.ingsw.persistence.ArchivioFruitori;
import it.unibs.ingsw.persistence.ArchivioProposte;
import it.unibs.ingsw.persistence.ArchivioTempoSimulato;
import it.unibs.ingsw.util.TempoSimulato;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite E2E V5: import batch di categorie, campi e proposte da file JSON.
 * Stessi pattern di ChecklistV3Test/ChecklistV4Test (Controller diretti,
 * TempoSimulato, @TempDir).
 */
class ChecklistV5Test {

    // Fixture temporale (identica a ChecklistV4Test).
    private static final LocalDate OGGI               = LocalDate.of(2026, 7, 1);
    private static final LocalDate TERMINE_ISCRIZIONE = LocalDate.of(2026, 7, 31);
    private static final LocalDate DATA_EVENTO        = LocalDate.of(2026, 8, 5);
    private static final LocalDate DATA_CONCLUSIVA    = LocalDate.of(2026, 8, 6);

    @TempDir Path tempDir;

    private ControllerConfiguratore controller;

    @BeforeEach
    void setUp() throws IOException {
        ConfigurazioneGlobale cfg = new ConfigurazioneGlobale();
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
        new ArchivioConfigurazione(tempDir.resolve("configurazione.json")).salva(cfg);
        new ArchivioCategorie(tempDir.resolve("categorie.json"))
                .salvaTutte(List.of(new Categoria("Sport")));

        controller = nuovoController();
    }

    private ControllerConfiguratore nuovoController() throws IOException {
        return new ControllerConfiguratore(
                new ArchivioConfigurazione(tempDir.resolve("configurazione.json")),
                new ArchivioCategorie(tempDir.resolve("categorie.json")),
                new ArchivioConfiguratori(tempDir.resolve("configuratori.json")),
                new ArchivioProposte(tempDir.resolve("proposte.json")),
                new ArchivioFruitori(tempDir.resolve("fruitori.json")),
                new ArchivioTempoSimulato(tempDir.resolve("tempo.json")),
                new TempoSimulato(OGGI)
        );
    }

    private Path scriviFileImport(String contenuto) throws IOException {
        Path file = tempDir.resolve("import.json");
        Files.writeString(file, contenuto);
        return file;
    }

    // Blocco "valori" completo e valido per la fixture temporale.
    private String valoriValidi(String titolo) {
        return """
                {
                  "Titolo": "%s",
                  "Numero di partecipanti": "5",
                  "Termine ultimo di iscrizione": "%s",
                  "Data": "%s",
                  "Ora": "18:00",
                  "Luogo": "Sala civica",
                  "Quota individuale": "10.50",
                  "Data conclusiva": "%s"
                }""".formatted(titolo, TERMINE_ISCRIZIONE, DATA_EVENTO, DATA_CONCLUSIVA);
    }

    // -------------------------------------------------------------------------
    // Import pulito e riferimenti nello stesso file
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("import pulito: 1 categoria nuova + 2 proposte valide → tutte importate e APERTE")
    void import_pulito() throws IOException {
        String json = """
                {
                  "categorie": [
                    { "nome": "Cucina",
                      "campiSpecifici": [
                        { "nome": "Argomento", "tipo": "STRINGA", "obbligatorio": false }
                      ] }
                  ],
                  "proposte": [
                    { "categoria": "Sport",  "valori": %s },
                    { "categoria": "Cucina", "valori": %s }
                  ]
                }""".formatted(valoriValidi("Torneo di scacchi"), valoriValidi("Corso di pasta fresca"));

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(1, esito.categorieImportate());
        assertEquals(0, esito.categorieScartate());
        assertEquals(2, esito.proposteImportate());
        assertEquals(0, esito.proposteScartate());
        assertTrue(esito.avvisi().isEmpty(), "nessun avviso atteso: " + esito.avvisi());

        // Categoria creata con il campo specifico.
        Categoria cucina = controller.getCategorie().stream()
                .filter(c -> c.getNome().equals("Cucina")).findFirst().orElseThrow();
        assertEquals(1, cucina.getCampiSpecifici().size());
        assertEquals("Argomento", cucina.getCampiSpecifici().get(0).getNome());

        // Le proposte importate sono pubblicate (APERTA) e in bacheca.
        assertEquals(2, controller.getBacheca().tutte().size());
        for (Proposta p : controller.getBacheca().tutte()) {
            assertEquals(StatoProposta.APERTA, p.getStato());
            assertEquals(OGGI, p.getDataPubblicazione());
        }
    }

    @Test
    @DisplayName("una proposta può referenziare una categoria definita nello stesso file")
    void proposta_referenzia_categoria_stesso_file() throws IOException {
        String json = """
                {
                  "categorie": [ { "nome": "Teatro", "campiSpecifici": [] } ],
                  "proposte":  [ { "categoria": "Teatro", "valori": %s } ]
                }""".formatted(valoriValidi("Serata improvvisazione"));

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(1, esito.categorieImportate());
        assertEquals(1, esito.proposteImportate());
        assertEquals(0, esito.proposteScartate());
        assertEquals("Teatro",
                controller.getBacheca().tutte().get(0).getCategoria().getNome());
    }

    // -------------------------------------------------------------------------
    // Scarti sulle categorie e sui campi
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("categoria con nome già esistente → skip con avviso, quella presente resta intatta")
    void categoria_duplicata_skippata() throws IOException {
        String json = """
                {
                  "categorie": [
                    { "nome": "Sport",
                      "campiSpecifici": [
                        { "nome": "Intruso", "tipo": "STRINGA", "obbligatorio": false }
                      ] }
                  ]
                }""";

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(0, esito.categorieImportate());
        assertEquals(1, esito.categorieScartate());
        assertTrue(esito.avvisi().stream().anyMatch(a -> a.contains("già esistente")));

        // La categoria esistente non è stata toccata (nessun merge dei campi).
        Categoria sport = controller.getCategorie().stream()
                .filter(c -> c.getNome().equals("Sport")).findFirst().orElseThrow();
        assertTrue(sport.getCampiSpecifici().isEmpty());
    }

    @Test
    @DisplayName("campo specifico duplicato → skip del campo, categoria comunque creata")
    void campo_duplicato_skippato() throws IOException {
        String json = """
                {
                  "categorie": [
                    { "nome": "Giochi",
                      "campiSpecifici": [
                        { "nome": "Regolamento", "tipo": "STRINGA", "obbligatorio": false },
                        { "nome": "Regolamento", "tipo": "INTERO",  "obbligatorio": true }
                      ] }
                  ]
                }""";

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(1, esito.categorieImportate());
        assertTrue(esito.avvisi().stream().anyMatch(a -> a.contains("Regolamento")));

        Categoria giochi = controller.getCategorie().stream()
                .filter(c -> c.getNome().equals("Giochi")).findFirst().orElseThrow();
        assertEquals(1, giochi.getCampiSpecifici().size(), "solo la prima occorrenza del campo");
        assertEquals(TipoCampo.STRINGA, giochi.getCampiSpecifici().get(0).getTipo());
    }

    @Test
    @DisplayName("tipo di campo non riconosciuto → skip del campo con avviso, nessun crash")
    void tipo_campo_non_riconosciuto_skippato() throws IOException {
        String json = """
                {
                  "categorie": [
                    { "nome": "Musica",
                      "campiSpecifici": [
                        { "nome": "Volume", "tipo": "PERCENTUALE", "obbligatorio": false },
                        { "nome": "Genere", "tipo": "STRINGA",     "obbligatorio": false }
                      ] }
                  ]
                }""";

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(1, esito.categorieImportate());
        assertTrue(esito.avvisi().stream()
                .anyMatch(a -> a.contains("PERCENTUALE") && a.contains("non riconosciuto")));

        Categoria musica = controller.getCategorie().stream()
                .filter(c -> c.getNome().equals("Musica")).findFirst().orElseThrow();
        assertEquals(1, musica.getCampiSpecifici().size());
        assertEquals("Genere", musica.getCampiSpecifici().get(0).getNome());
    }

    // -------------------------------------------------------------------------
    // Scarti sulle proposte
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("proposta con categoria inesistente → scartata con avviso, import prosegue")
    void proposta_categoria_inesistente_scartata() throws IOException {
        String json = """
                {
                  "proposte": [
                    { "categoria": "Fantasma", "valori": %s },
                    { "categoria": "Sport",    "valori": %s }
                  ]
                }""".formatted(valoriValidi("Evento perduto"), valoriValidi("Torneo di scacchi"));

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(1, esito.proposteImportate());
        assertEquals(1, esito.proposteScartate());
        assertTrue(esito.avvisi().stream()
                .anyMatch(a -> a.contains("Fantasma") && a.contains("inesistente")));
        assertEquals(1, controller.getBacheca().tutte().size());
    }

    @Test
    @DisplayName("proposta con vincolo sulle date violato → scartata, avviso indica il vincolo")
    void proposta_vincolo_date_violato_scartata() throws IOException {
        // Data evento a 1 solo giorno dal termine iscrizione (vincolo: >= 2 giorni).
        String json = """
                {
                  "proposte": [
                    { "categoria": "Sport",
                      "valori": {
                        "Titolo": "Evento troppo ravvicinato",
                        "Numero di partecipanti": "5",
                        "Termine ultimo di iscrizione": "2026-07-31",
                        "Data": "2026-08-01",
                        "Ora": "18:00",
                        "Luogo": "Sala civica",
                        "Quota individuale": "10.50",
                        "Data conclusiva": "2026-08-01"
                      } }
                  ]
                }""";

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(0, esito.proposteImportate());
        assertEquals(1, esito.proposteScartate());
        assertTrue(esito.avvisi().stream().anyMatch(a -> a.contains("2 giorni")),
                "l'avviso deve indicare il vincolo violato: " + esito.avvisi());
        assertTrue(controller.getBacheca().isVuota());
    }

    @Test
    @DisplayName("proposta con campo obbligatorio mancante → scartata, avviso nomina il campo")
    void proposta_obbligatorio_mancante_scartata() throws IOException {
        // Manca "Luogo" (campo base obbligatorio).
        String json = """
                {
                  "proposte": [
                    { "categoria": "Sport",
                      "valori": {
                        "Titolo": "Evento senza luogo",
                        "Numero di partecipanti": "5",
                        "Termine ultimo di iscrizione": "%s",
                        "Data": "%s",
                        "Ora": "18:00",
                        "Quota individuale": "10.50",
                        "Data conclusiva": "%s"
                      } }
                  ]
                }""".formatted(TERMINE_ISCRIZIONE, DATA_EVENTO, DATA_CONCLUSIVA);

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(0, esito.proposteImportate());
        assertEquals(1, esito.proposteScartate());
        assertTrue(esito.avvisi().stream()
                .anyMatch(a -> a.contains("Luogo") && a.contains("obbligatorio")));
        assertTrue(controller.getBacheca().isVuota());
    }

    // -------------------------------------------------------------------------
    // Robustezza sul file
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("file malformato → errore gestito, nessuna scrittura, nessun crash")
    void file_malformato_gestito() throws IOException {
        Path file = scriviFileImport("{ questo non è JSON valido ");

        ImportatoreBatch.EsitoImport esito = new ImportatoreBatch(controller).importa(file);

        assertEquals(0, esito.categorieImportate());
        assertEquals(0, esito.proposteImportate());
        assertTrue(esito.avvisi().stream().anyMatch(a -> a.contains("malformato")));

        // Nessuna scrittura: gli archivi su disco sono rimasti quelli della fixture.
        List<Categoria> categorie =
                new ArchivioCategorie(tempDir.resolve("categorie.json")).caricaTutte();
        assertEquals(List.of(new Categoria("Sport")), categorie);
        assertTrue(new ArchivioProposte(tempDir.resolve("proposte.json")).caricaTutte().isEmpty());
    }

    @Test
    @DisplayName("file inesistente → errore gestito, nessun crash")
    void file_inesistente_gestito() {
        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(tempDir.resolve("non-esiste.json"));

        assertEquals(0, esito.categorieImportate());
        assertEquals(0, esito.proposteImportate());
        assertTrue(esito.avvisi().stream().anyMatch(a -> a.contains("inesistente")));
    }

    @Test
    @DisplayName("elemento malformato isolato: gli altri elementi del file vengono importati")
    void elemento_malformato_non_aborta_import() throws IOException {
        String json = """
                {
                  "categorie": [
                    "non sono un oggetto",
                    { "nome": "Cinema", "campiSpecifici": [] }
                  ],
                  "proposte": [
                    { "categoria": "Cinema", "valori": %s }
                  ]
                }""".formatted(valoriValidi("Rassegna all'aperto"));

        ImportatoreBatch.EsitoImport esito =
                new ImportatoreBatch(controller).importa(scriviFileImport(json));

        assertEquals(1, esito.categorieImportate());
        assertEquals(1, esito.categorieScartate());
        assertEquals(1, esito.proposteImportate());
        assertTrue(esito.avvisi().stream().anyMatch(a -> a.contains("malformato")));
    }

    // -------------------------------------------------------------------------
    // Persistenza
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("roundtrip: categorie e proposte importate sopravvivono a un riavvio (rilettura da disco)")
    void persistenza_roundtrip() throws IOException {
        String json = """
                {
                  "categorie": [
                    { "nome": "Cucina",
                      "campiSpecifici": [
                        { "nome": "Argomento", "tipo": "STRINGA", "obbligatorio": false }
                      ] }
                  ],
                  "proposte": [ { "categoria": "Cucina", "valori": %s } ]
                }""".formatted(valoriValidi("Corso di pasta fresca"));

        new ImportatoreBatch(controller).importa(scriviFileImport(json));

        // "Riavvio": nuovo controller che ricarica tutto dagli stessi archivi.
        ControllerConfiguratore riavviato = nuovoController();

        Categoria cucina = riavviato.getCategorie().stream()
                .filter(c -> c.getNome().equals("Cucina")).findFirst().orElseThrow();
        assertEquals(1, cucina.getCampiSpecifici().size());

        List<Proposta> aperte = riavviato.getBacheca().tutte();
        assertEquals(1, aperte.size());
        assertEquals(StatoProposta.APERTA, aperte.get(0).getStato());
        assertEquals("Corso di pasta fresca", aperte.get(0).getValori().get("Titolo"));
        assertEquals(List.of(), aperte.get(0).getAderenti());
    }
}

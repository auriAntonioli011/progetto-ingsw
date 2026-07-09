package it.unibs.ingsw;

import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.ConfigurazioneGlobale;
import it.unibs.ingsw.model.Configuratore;
import it.unibs.ingsw.model.Fruitore;
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

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strumento riutilizzabile (non è un test JUnit): genera da zero il dataset
 * dimostrativo in data-demo/, con gli stati proposta richiesti per la demo
 * d'esame (3 APERTA, 2 CONFERMATA, 1 CONCLUSA, 1 ANNULLATA, 1 RITIRATA),
 * 2 configuratori, 3 fruitori e 2 categorie.
 *
 * Meccanismo: lo stesso dei test E2E — le entità vengono create con date
 * coerenti a una data iniziale simulata, poi TempoSimulato viene fatto
 * avanzare oltre le soglie rilevanti ed è il motore transizioni esistente
 * (ArchivioProposte.elaboraTransizioni) a portare ogni proposta nello stato
 * voluto. La proposta RITIRATA usa Proposta.ritira() (azione del
 * configuratore), non una transizione automatica.
 *
 * Il file tempo.json generato fissa la data simulata a DATA_DEMO: caricando
 * il dataset, l'applicazione parte sempre "al" 2026-09-01, così gli stati
 * restano stabili qualunque sia la data reale della dimostrazione.
 *
 * Esecuzione (dalla root del progetto):
 *   mvn -q test-compile
 *   java -cp "target/classes:target/test-classes:$HOME/.m2/repository/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar" it.unibs.ingsw.GeneratoreDatasetDemo
 */
public class GeneratoreDatasetDemo {

    // Linea temporale del dataset.
    private static final LocalDate DATA_CREAZIONE = LocalDate.of(2026, 7, 20);
    private static final LocalDate DOPO_TERMINE_AGOSTO_5 = LocalDate.of(2026, 8, 6);
    private static final LocalDate DOPO_CONCLUSIVA_AGOSTO_11 = LocalDate.of(2026, 8, 12);
    private static final LocalDate DOPO_TERMINE_AGOSTO_20 = LocalDate.of(2026, 8, 21);
    private static final LocalDate DATA_RITIRO = LocalDate.of(2026, 8, 25);
    private static final LocalDate DATA_DEMO = LocalDate.of(2026, 9, 1);

    public static void main(String[] args) throws IOException {
        java.nio.file.Path dir = java.nio.file.Path.of("data-demo");
        TempoSimulato tempo = new TempoSimulato(DATA_CREAZIONE);

        // --- Configurazione globale (stessi campi base della suite di test) ---
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
        cfg.impostaCampiComuni(List.of(new Campo("Note", TipoCampo.STRINGA, false)));

        // --- Categorie ---
        Categoria escursioni = new Categoria("Escursioni");
        escursioni.aggiungiCampoSpecifico(
                new Campo("Lunghezza percorso (km)", TipoCampo.DECIMALE, false), cfg);
        Categoria corsi = new Categoria("Corsi di cucina");
        corsi.aggiungiCampoSpecifico(
                new Campo("Argomento", TipoCampo.STRINGA, true), cfg);

        // --- Utenti (credenziali semplici, password in chiaro = username) ---
        Configuratore c1 = configuratore("configuratore1");
        Configuratore c2 = configuratore("configuratore2");
        Fruitore f1 = fruitore("fruitore1");
        Fruitore f2 = fruitore("fruitore2");
        Fruitore f3 = fruitore("fruitore3");

        // --- Proposte: create valide al 2026-07-20 e subito pubblicate (APERTA) ---
        List<Proposta> proposte = new ArrayList<>();

        // Resteranno APERTE alla data demo (termine 2026-09-20 ancora futuro).
        Proposta apertaUno = pubblica(tempo, cfg, escursioni,
                valori("Escursione al Monte Guglielmo", 3, "2026-09-20", "2026-09-25", "2026-09-26",
                        "08:30", "Ritrovo piazzale funivia", "5.00",
                        Map.of("Lunghezza percorso (km)", "12.5")));
        apertaUno.aderisci(f1, tempo);
        proposte.add(apertaUno);

        proposte.add(pubblica(tempo, cfg, escursioni,
                valori("Camminata al lago di Iseo", 2, "2026-09-20", "2026-09-25", "2026-09-25",
                        "09:00", "Lungolago di Sarnico", "0.00",
                        Map.of("Lunghezza percorso (km)", "8.0"))));

        Proposta apertaTre = pubblica(tempo, cfg, corsi,
                valori("Corso di pasta fresca", 3, "2026-09-20", "2026-09-25", "2026-09-25",
                        "18:00", "Cucina della sala civica", "15.00",
                        Map.of("Argomento", "Pasta ripiena")));
        apertaTre.aderisci(f2, tempo);
        proposte.add(apertaTre);

        // Diventeranno CONFERMATE (piene) al superamento del termine 2026-08-20.
        Proposta confermataUno = pubblica(tempo, cfg, escursioni,
                valori("Escursione alle Torbiere", 2, "2026-08-20", "2026-09-10", "2026-09-11",
                        "09:30", "Ingresso riserva Torbiere del Sebino", "3.00",
                        Map.of("Lunghezza percorso (km)", "6.0")));
        confermataUno.aderisci(f1, tempo);
        confermataUno.aderisci(f2, tempo);
        proposte.add(confermataUno);

        Proposta confermataDue = pubblica(tempo, cfg, corsi,
                valori("Corso di panificazione", 2, "2026-08-20", "2026-09-10", "2026-09-10",
                        "17:30", "Laboratorio del forno sociale", "12.00",
                        Map.of("Argomento", "Pane con lievito madre")));
        confermataDue.aderisci(f1, tempo);
        confermataDue.aderisci(f3, tempo);
        proposte.add(confermataDue);

        // Diventerà CONCLUSA: piena al termine 2026-08-05, conclusiva 2026-08-11 superata.
        Proposta conclusa = pubblica(tempo, cfg, escursioni,
                valori("Salita al Monte Isola", 2, "2026-08-05", "2026-08-10", "2026-08-11",
                        "08:00", "Imbarcadero di Sulzano", "7.50",
                        Map.of("Lunghezza percorso (km)", "9.0")));
        conclusa.aderisci(f2, tempo);
        conclusa.aderisci(f3, tempo);
        proposte.add(conclusa);

        // Diventerà ANNULLATA: al termine 2026-08-05 ha 1 aderente su 3 richiesti.
        Proposta annullata = pubblica(tempo, cfg, corsi,
                valori("Corso di cucina vegetariana", 3, "2026-08-05", "2026-08-10", "2026-08-10",
                        "18:30", "Cucina della sala civica", "10.00",
                        Map.of("Argomento", "Menù di stagione")));
        annullata.aderisci(f1, tempo);
        proposte.add(annullata);

        // Verrà RITIRATA dal configuratore prima del giorno dell'evento.
        Proposta ritirata = pubblica(tempo, cfg, escursioni,
                valori("Traversata in quota", 3, "2026-09-20", "2026-09-25", "2026-09-26",
                        "07:00", "Rifugio Almici", "20.00",
                        Map.of("Lunghezza percorso (km)", "16.0")));
        ritirata.aderisci(f3, tempo);
        proposte.add(ritirata);

        // --- Avanzamento del tempo simulato + motore transizioni esistente ---
        ArchivioProposte archivioProposte = new ArchivioProposte(dir.resolve("proposte.json"));

        tempo.impostaData(DOPO_TERMINE_AGOSTO_5.atStartOfDay());
        archivioProposte.elaboraTransizioni(proposte, tempo);   // → CONFERMATA (piena) e ANNULLATA

        tempo.impostaData(DOPO_CONCLUSIVA_AGOSTO_11.atStartOfDay());
        archivioProposte.elaboraTransizioni(proposte, tempo);   // → CONCLUSA

        tempo.impostaData(DOPO_TERMINE_AGOSTO_20.atStartOfDay());
        archivioProposte.elaboraTransizioni(proposte, tempo);   // → le due CONFERMATA

        tempo.impostaData(DATA_RITIRO.atStartOfDay());
        ritirata.ritira(tempo);                                 // azione del configuratore

        tempo.impostaData(DATA_DEMO.atStartOfDay());
        archivioProposte.elaboraTransizioni(proposte, tempo);   // nessuna transizione: stati stabili

        // --- Persistenza completa del dataset ---
        new ArchivioConfigurazione(dir.resolve("configurazione.json")).salva(cfg);
        new ArchivioCategorie(dir.resolve("categorie.json")).salvaTutte(List.of(escursioni, corsi));
        new ArchivioConfiguratori(dir.resolve("configuratori.json")).salvaTutti(List.of(c1, c2));
        new ArchivioFruitori(dir.resolve("fruitori.json")).salvaTutti(List.of(f1, f2, f3));
        new ArchivioTempoSimulato(dir.resolve("tempo.json")).salvaDataSimulata(DATA_DEMO);

        // --- Riepilogo su console ---
        System.out.println("Dataset demo generato in " + dir.toAbsolutePath());
        System.out.println("Data simulata (tempo.json): " + DATA_DEMO);
        Map<StatoProposta, Integer> conteggio = new LinkedHashMap<>();
        for (Proposta p : proposte) {
            conteggio.merge(p.getStato(), 1, Integer::sum);
            System.out.println("  - " + p.getValori().get("Titolo")
                    + "  [" + p.getCategoria().getNome() + "]  stato=" + p.getStato()
                    + "  aderenti=" + p.getAderenti());
        }
        System.out.println("Conteggio per stato: " + conteggio);
    }

    // --- helper ---

    private static Configuratore configuratore(String username) {
        Configuratore c = new Configuratore(username, username);
        c.confermaCredenzialiPersonali(username, username);
        return c;
    }

    private static Fruitore fruitore(String username) {
        Fruitore f = new Fruitore(username, username);
        f.confermaCredenzialiPersonali(username, username);
        return f;
    }

    private static Map<String, String> valori(String titolo, int numeroPartecipanti,
                                              String termine, String dataEvento, String conclusiva,
                                              String ora, String luogo, String quota,
                                              Map<String, String> specifici) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Titolo", titolo);
        m.put(Proposta.CAMPO_NUMERO_PARTECIPANTI, String.valueOf(numeroPartecipanti));
        m.put(Proposta.CAMPO_TERMINE_ISCRIZIONE, termine);
        m.put(Proposta.CAMPO_DATA_EVENTO, dataEvento);
        m.put("Ora", ora);
        m.put("Luogo", luogo);
        m.put("Quota individuale", quota);
        m.put(Proposta.CAMPO_DATA_CONCLUSIVA, conclusiva);
        m.putAll(specifici);
        return m;
    }

    private static Proposta pubblica(TempoSimulato tempo, ConfigurazioneGlobale cfg,
                                     Categoria categoria, Map<String, String> valori) {
        Proposta p = new Proposta(categoria, valori);
        if (!p.valida(tempo, cfg)) {
            throw new IllegalStateException("dataset demo: proposta non valida ('"
                    + valori.get("Titolo") + "'): "
                    + String.join("; ", p.vincoliViolati(tempo, cfg)));
        }
        p.marcaPubblicata(tempo.oggi());
        return p;
    }
}

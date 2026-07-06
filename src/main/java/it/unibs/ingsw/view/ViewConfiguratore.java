package it.unibs.ingsw.view;

import it.unibs.ingsw.controller.ControllerConfiguratore;
import it.unibs.ingsw.model.Bacheca;
import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.ConfigurazioneGlobale;
import it.unibs.ingsw.model.Configuratore;
import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.StatoProposta;
import it.unibs.ingsw.model.TipoCampo;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Unico punto di I/O console per il ruolo configuratore.
 * Nessun'altra classe del progetto deve usare System.out, System.in o Scanner.
 */
public class ViewConfiguratore {

    private static final String SEP = "-".repeat(52);

    private final ControllerConfiguratore controller;
    private final Scanner scanner;

    // pre:  controller != null && scanner != null
    // post: this.controller == controller && this.scanner == scanner
    public ViewConfiguratore(ControllerConfiguratore controller, Scanner scanner) {
        if (controller == null) throw new IllegalArgumentException("controller non può essere null");
        if (scanner == null) throw new IllegalArgumentException("scanner non può essere null");
        this.controller = controller;
        this.scanner = scanner;
    }

    // post: gestisce l'intero ciclo di vita dell'interazione col configuratore fino all'uscita
    public void avvia() {
        if (controller.getConfiguratoreLoggato() == null) {
            if (controller.isPrimoAccesso()) {
                gestisciPrimoAccesso();
            } else {
                gestisciLogin();
            }
        }
        menuPrincipale();
    }

    // -------------------------------------------------------------------------
    // Autenticazione
    // -------------------------------------------------------------------------

    private void gestisciPrimoAccesso() {
        stampaSezione("PRIMO ACCESSO AL SISTEMA");
        println("Inserire le credenziali predefinite ricevute dall'amministratore.");

        Configuratore configuratore = null;
        while (configuratore == null) {
            try {
                String u = leggiStringa("Username predefinito");
                String p = leggiStringa("Password predefinita");
                configuratore = controller.primoAccesso(u, p);
            } catch (IllegalArgumentException | IllegalStateException e) {
                println("[ERRORE] " + e.getMessage());
            }
        }

        println("");
        println("Credenziali predefinite accettate. Scegliere le credenziali personali.");
        while (true) {
            try {
                String nuovoU = leggiStringa("Nuovo username personale");
                String nuovaP = leggiStringa("Nuova password personale");
                controller.confermaCredenzialiPersonali(configuratore, nuovoU, nuovaP);
                println("Credenziali confermate. Benvenuto, "
                        + controller.getConfiguratoreLoggato().getUsername() + "!");
                return;
            } catch (IllegalArgumentException | IllegalStateException e) {
                println("[ERRORE] " + e.getMessage());
            } catch (IOException e) {
                println("[ERRORE I/O] Impossibile salvare le credenziali: " + e.getMessage());
            }
        }
    }

    private void gestisciLogin() {
        stampaSezione("ACCESSO AL SISTEMA");
        while (true) {
            try {
                String u = leggiStringa("Username");
                String p = leggiStringa("Password");
                controller.login(u, p);
                println("Benvenuto, " + controller.getConfiguratoreLoggato().getUsername() + "!");
                return;
            } catch (IllegalArgumentException e) {
                println("[ERRORE] " + e.getMessage() + " — riprovare.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Menu principale
    // -------------------------------------------------------------------------

    private void menuPrincipale() {
        while (true) {
            stampaSezione("MENU CONFIGURATORE");
            ConfigurazioneGlobale cfg = controller.getConfigurazioneGlobale();

            if (!cfg.campiBaseFissati()) {
                println("  1) Fissa campi base");
            }
            println("  2) Fissa/modifica campi comuni");
            println("  3) Crea categoria");
            println("  4) Rimuovi categoria");
            println("  5) Aggiungi campo specifico a categoria");
            println("  6) Rimuovi campo specifico da categoria");
            println("  7) Visualizza categorie e i loro campi");
            println("  8) Crea proposta");
            println("  9) Pubblica proposta valida");
            println(" 10) Visualizza bacheca");
            println(" 11) Visualizza archivio proposte (storico + aderenti)");
            println(" 12) Imposta/modifica data simulata"
                    + (controller.isTempoSimulato() ? "  [attualmente: " + controller.getDataCorrente() + "]" : "  [attualmente: tempo reale]"));
            println(" 13) Rimuovi data simulata (torna a tempo reale al riavvio)");
            println("  0) Esci");

            switch (leggiIntero("Scelta")) {
                case 1 -> {
                    if (!cfg.campiBaseFissati()) optFissaCampiBase();
                    else println("[AVVISO] I campi base sono già stati fissati e non possono essere modificati.");
                }
                case 2 -> optFissaCampiComuni();
                case 3 -> optCreaCategoria();
                case 4 -> optRimuoviCategoria();
                case 5 -> optAggiungiCampoSpecifico();
                case 6 -> optRimuoviCampoSpecifico();
                case 7 -> optVisualizzaCategorie();
                case 8 -> optCreaProposta();
                case 9 -> optPubblicaPropostaValida();
                case 10 -> optVisualizzaBacheca();
                case 11 -> optVisualizzaArchivio();
                case 12 -> optImpostaDataSimulata();
                case 13 -> optRimuoviDataSimulata();
                case 0 -> { println("Arrivederci."); return; }
                default -> println("[AVVISO] Opzione non riconosciuta.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Opzioni menu
    // -------------------------------------------------------------------------

    private void optFissaCampiBase() {
        stampaSezione("FISSA CAMPI BASE");
        try {
            List<Campo> campi = leggiListaCampi();
            if (campi.isEmpty()) {
                println("[AVVISO] Nessun campo inserito — operazione annullata.");
                return;
            }
            controller.fissaCampiBase(campi);
            println("Campi base fissati (" + campi.size() + " campo/i).");
        } catch (IllegalArgumentException | IllegalStateException e) {
            println("[ERRORE] " + e.getMessage());
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private void optFissaCampiComuni() {
        stampaSezione("FISSA/MODIFICA CAMPI COMUNI");
        println("Inserire il nuovo elenco completo dei campi comuni (sovrascrive quello esistente).");
        try {
            List<Campo> campi = leggiListaCampi();
            controller.impostaCampiComuni(campi);
            println("Campi comuni aggiornati (" + campi.size() + " campo/i).");
        } catch (IllegalArgumentException | IllegalStateException e) {
            println("[ERRORE] " + e.getMessage());
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private void optCreaCategoria() {
        stampaSezione("CREA CATEGORIA");
        try {
            String nome = leggiStringa("Nome categoria");
            Categoria creata = controller.creaCategoria(nome);
            println("Categoria '" + creata.getNome() + "' creata.");
        } catch (IllegalArgumentException e) {
            println("[ERRORE] " + e.getMessage());
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private void optRimuoviCategoria() {
        stampaSezione("RIMUOVI CATEGORIA");
        try {
            String nome = leggiStringa("Nome categoria da rimuovere");
            controller.rimuoviCategoria(nome);
            println("Categoria '" + nome + "' rimossa.");
        } catch (IllegalArgumentException e) {
            println("[ERRORE] " + e.getMessage());
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private void optAggiungiCampoSpecifico() {
        stampaSezione("AGGIUNGI CAMPO SPECIFICO");
        try {
            String nomeCategoria = leggiStringa("Nome categoria");
            println("Definire il nuovo campo:");
            Campo campo = leggiCampo();
            controller.aggiungiCampoSpecifico(nomeCategoria, campo);
            println("Campo '" + campo.getNome() + "' aggiunto alla categoria '" + nomeCategoria + "'.");
        } catch (IllegalArgumentException e) {
            println("[ERRORE] " + e.getMessage());
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private void optRimuoviCampoSpecifico() {
        stampaSezione("RIMUOVI CAMPO SPECIFICO");
        try {
            String nomeCategoria = leggiStringa("Nome categoria");
            String nomeCampo = leggiStringa("Nome campo da rimuovere");
            controller.rimuoviCampoSpecifico(nomeCategoria, nomeCampo);
            println("Campo '" + nomeCampo + "' rimosso dalla categoria '" + nomeCategoria + "'.");
        } catch (IllegalArgumentException e) {
            println("[ERRORE] " + e.getMessage());
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private void optVisualizzaCategorie() {
        stampaSezione("CATEGORIE E CAMPI");
        List<Categoria> categorie = controller.getCategorie();
        ConfigurazioneGlobale cfg = controller.getConfigurazioneGlobale();

        if (categorie.isEmpty()) {
            println("Nessuna categoria definita.");
            return;
        }
        for (Categoria cat : categorie) {
            println("Categoria: " + cat.getNome());
            List<Campo> campi = cat.getTuttiICampi(cfg);
            if (campi.isEmpty()) {
                println("  (nessun campo)");
            } else {
                for (Campo c : campi) {
                    String obblig = c.isObbligatorio() ? " [OBBLIGATORIO]" : " [facoltativo]";
                    println("  - " + c.getNome() + " (" + c.getTipo() + ")" + obblig);
                }
            }
            println("");
        }
    }

    // -------------------------------------------------------------------------
    // Opzioni menu V2 — Proposte
    // -------------------------------------------------------------------------

    private void optCreaProposta() {
        stampaSezione("CREA PROPOSTA");
        List<Categoria> categorie = controller.getCategorie();
        if (categorie.isEmpty()) {
            println("[AVVISO] Nessuna categoria definita. Crea prima una categoria (opzione 3).");
            return;
        }
        ConfigurazioneGlobale cfg = controller.getConfigurazioneGlobale();

        Categoria scelta = leggiCategoria(categorie);
        if (scelta == null) return;

        List<Campo> campi = scelta.getTuttiICampi(cfg);
        if (campi.isEmpty()) {
            println("[AVVISO] La categoria selezionata non ha campi. Configurare campi base/comuni prima.");
            return;
        }

        println("Compila i valori dei campi (Invio per lasciare vuoto un campo facoltativo).");
        println("Per i campi di tipo DATA usare il formato ISO yyyy-MM-dd (es. 2026-07-15).");
        Map<String, String> valori = new LinkedHashMap<>();
        for (Campo c : campi) {
            String obblig = c.isObbligatorio() ? "[OBBLIGATORIO]" : "[facoltativo]";
            print("  " + c.getNome() + " (" + c.getTipo() + ") " + obblig + ": ");
            String v = scanner.nextLine().trim();
            if (!v.isEmpty()) valori.put(c.getNome(), v);
        }

        try {
            Proposta creata = controller.creaProposta(scelta, valori);
            if (creata.getStato() == StatoProposta.VALIDA) {
                println("Proposta creata e risulta VALIDA. Puoi pubblicarla dal menu (opzione 9).");
            } else {
                println("[AVVISO] Proposta creata ma non ancora VALIDA.");
                println("        Verifica: campi obbligatori compilati, termine iscrizione > oggi,");
                println("        data evento >= termine iscrizione + 2 giorni, dataConclusiva >= data evento.");
                println("        Nomi attesi per i campi data: '" + Proposta.CAMPO_TERMINE_ISCRIZIONE
                        + "', '" + Proposta.CAMPO_DATA_EVENTO + "', '" + Proposta.CAMPO_DATA_CONCLUSIVA + "'.");
            }
        } catch (IllegalArgumentException e) {
            println("[ERRORE] " + e.getMessage());
        }
    }

    private void optPubblicaPropostaValida() {
        stampaSezione("PUBBLICA PROPOSTA VALIDA");
        List<Proposta> valide = controller.getProposteValide();
        if (valide.isEmpty()) {
            println("[AVVISO] Nessuna proposta VALIDA da pubblicare nella sessione corrente.");
            return;
        }
        println("Proposte VALIDA disponibili:");
        for (int i = 0; i < valide.size(); i++) {
            Proposta p = valide.get(i);
            println("  " + (i + 1) + ") categoria=" + p.getCategoria().getNome()
                    + "  campi=" + p.getValori().size());
        }
        int scelta = leggiIntero("Numero proposta da pubblicare (0 per annullare)");
        if (scelta == 0) return;
        if (scelta < 1 || scelta > valide.size()) {
            println("[AVVISO] Selezione fuori intervallo.");
            return;
        }
        try {
            controller.richiediPubblicazione(valide.get(scelta - 1));
            println("Proposta pubblicata (stato APERTA, dataPubblicazione impostata).");
        } catch (IllegalStateException | IllegalArgumentException e) {
            println("[ERRORE] " + e.getMessage());
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private void optVisualizzaBacheca() {
        stampaSezione("BACHECA");
        Bacheca bacheca = controller.getBacheca();
        if (bacheca.isVuota()) {
            println("La bacheca è vuota.");
            return;
        }
        Map<Categoria, List<Proposta>> gruppi = bacheca.raggruppatePerCategoria();
        for (Map.Entry<Categoria, List<Proposta>> e : gruppi.entrySet()) {
            println("Categoria: " + e.getKey().getNome());
            for (Proposta p : e.getValue()) {
                println("  - pubblicata il " + p.getDataPubblicazione()
                        + "  (campi valorizzati: " + p.getValori().size() + ")");
                for (Map.Entry<String, String> v : p.getValori().entrySet()) {
                    println("      " + v.getKey() + " = " + v.getValue());
                }
            }
            println("");
        }
    }

    private void optVisualizzaArchivio() {
        stampaSezione("ARCHIVIO PROPOSTE (STORICO + ADERENTI)");
        List<Proposta> archivio = controller.getArchivioProposte();
        if (archivio.isEmpty()) {
            println("Nessuna proposta in archivio.");
            return;
        }
        for (Proposta p : archivio) {
            println("Categoria: " + p.getCategoria().getNome() + "  |  Stato: " + p.getStato());
            println("  Pubblicata il: " + p.getDataPubblicazione());
            println("  Storico stati:");
            List<Proposta.VoceStorico> storico = p.getStorico();
            if (storico.isEmpty()) {
                println("    (nessuna transizione registrata)");
            } else {
                for (Proposta.VoceStorico v : storico) {
                    println("    - " + v.stato() + " il " + v.data());
                }
            }
            List<String> aderenti = p.getAderenti();
            println("  Aderenti (" + aderenti.size() + "):");
            if (aderenti.isEmpty()) {
                println("    (nessun aderente)");
            } else {
                for (String u : aderenti) {
                    println("    - " + u);
                }
            }
            println("");
        }
    }

    private void optImpostaDataSimulata() {
        stampaSezione("IMPOSTA DATA SIMULATA");
        println("Data attuale: " + controller.getDataCorrente()
                + (controller.isTempoSimulato() ? " (simulata)" : " (tempo reale)"));
        while (true) {
            String s = leggiStringa("Nuova data simulata (yyyy-MM-dd)");
            try {
                LocalDate nuova = LocalDate.parse(s);
                boolean applicataAdesso = controller.impostaDataSimulata(nuova);
                if (applicataAdesso) {
                    println("Data simulata aggiornata a " + nuova
                            + " ed è già attiva in questa sessione.");
                } else {
                    println("Data simulata salvata (" + nuova + "). Verrà applicata"
                            + " al prossimo riavvio dell'applicazione.");
                }
                return;
            } catch (DateTimeParseException e) {
                println("[AVVISO] Formato non valido, usa yyyy-MM-dd (es. 2026-08-15).");
            } catch (IllegalArgumentException e) {
                println("[ERRORE] " + e.getMessage());
                return;
            } catch (IOException e) {
                println("[ERRORE I/O] " + e.getMessage());
                return;
            }
        }
    }

    private void optRimuoviDataSimulata() {
        stampaSezione("RIMUOVI DATA SIMULATA");
        try {
            controller.rimuoviDataSimulata();
            if (controller.isTempoSimulato()) {
                println("File data/tempo.json rimosso. In questa sessione il tempo"
                        + " resta simulato: al prossimo riavvio verrà usato il tempo reale.");
            } else {
                println("Nessuna data simulata attiva. Nessuna modifica effettuata.");
            }
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private Categoria leggiCategoria(List<Categoria> categorie) {
        println("Categorie disponibili:");
        for (int i = 0; i < categorie.size(); i++) {
            println("  " + (i + 1) + ") " + categorie.get(i).getNome());
        }
        int scelta = leggiIntero("Numero categoria (0 per annullare)");
        if (scelta == 0) return null;
        if (scelta < 1 || scelta > categorie.size()) {
            println("[AVVISO] Selezione fuori intervallo.");
            return null;
        }
        return categorie.get(scelta - 1);
    }

    // -------------------------------------------------------------------------
    // Helper per la raccolta dell'input
    // -------------------------------------------------------------------------

    private List<Campo> leggiListaCampi() {
        println("Inserire i campi uno alla volta. Lasciare il nome vuoto per terminare.");
        List<Campo> campi = new ArrayList<>();
        while (true) {
            print("  Nome campo (invio per terminare): ");
            String nome = scanner.nextLine().trim();
            if (nome.isEmpty()) break;
            TipoCampo tipo = leggiTipoCampo();
            boolean obbligatorio = leggiBoolean("  Obbligatorio?");
            campi.add(new Campo(nome, tipo, obbligatorio));
            println("  Campo '" + nome + "' aggiunto.");
        }
        return campi;
    }

    private Campo leggiCampo() {
        String nome = leggiStringa("  Nome campo");
        TipoCampo tipo = leggiTipoCampo();
        boolean obbligatorio = leggiBoolean("  Obbligatorio?");
        return new Campo(nome, tipo, obbligatorio);
    }

    private TipoCampo leggiTipoCampo() {
        TipoCampo[] valori = TipoCampo.values();
        println("  Tipo di campo:");
        for (int i = 0; i < valori.length; i++) {
            println("    " + (i + 1) + ") " + valori[i]);
        }
        while (true) {
            int scelta = leggiIntero("  Scelta tipo");
            if (scelta >= 1 && scelta <= valori.length) return valori[scelta - 1];
            println("[AVVISO] Scegliere un numero tra 1 e " + valori.length + ".");
        }
    }

    private boolean leggiBoolean(String prompt) {
        while (true) {
            print(prompt + " (s/n): ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("s") || input.equals("si") || input.equals("sì")) return true;
            if (input.equals("n") || input.equals("no")) return false;
            println("[AVVISO] Rispondere con 's' o 'n'.");
        }
    }

    private String leggiStringa(String prompt) {
        while (true) {
            print(prompt + ": ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) return input;
            println("[AVVISO] Il valore non può essere vuoto.");
        }
    }

    private int leggiIntero(String prompt) {
        while (true) {
            print(prompt + ": ");
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                println("[AVVISO] Inserire un numero intero.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper per l'output
    // -------------------------------------------------------------------------

    private void stampaSezione(String titolo) {
        println("");
        println(SEP);
        println("  " + titolo);
        println(SEP);
    }

    private void println(String testo) {
        System.out.println(testo);
    }

    private void print(String testo) {
        System.out.print(testo);
        System.out.flush();
    }
}

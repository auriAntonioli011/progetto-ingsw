package it.unibs.ingsw.view;

import it.unibs.ingsw.controller.ControllerFruitore;
import it.unibs.ingsw.model.Bacheca;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.Notifica;
import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.SpazioPersonale;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Unico punto di I/O console per il ruolo fruitore.
 * Nessun'altra classe del progetto — a parte ViewConfiguratore — deve usare System.out/in.
 */
public class ViewFruitore {

    private static final String SEP = "-".repeat(52);

    private final ControllerFruitore controller;
    private final Scanner scanner;

    // pre:  controller != null && scanner != null
    // post: this.controller == controller && this.scanner == scanner
    public ViewFruitore(ControllerFruitore controller, Scanner scanner) {
        if (controller == null) throw new IllegalArgumentException("controller non può essere null");
        if (scanner == null) throw new IllegalArgumentException("scanner non può essere null");
        this.controller = controller;
        this.scanner = scanner;
    }

    // post: gestisce il ciclo di vita dell'interazione con il fruitore fino all'uscita.
    //       Prima di qualunque cosa fa girare inizializzaSessione() sul controller
    //       (ricollega osservatori + motore transizioni).
    public void avvia() {
        try {
            controller.inizializzaSessione();
        } catch (IOException e) {
            println("[ERRORE I/O] Impossibile inizializzare la sessione: " + e.getMessage());
            return;
        }

        if (!gestisciAccesso()) return;
        menuPrincipale();
    }

    // -------------------------------------------------------------------------
    // Accesso
    // -------------------------------------------------------------------------

    private boolean gestisciAccesso() {
        stampaSezione("ACCESSO FRUITORE");
        while (true) {
            println("  1) Registrati");
            println("  2) Accedi");
            println("  0) Torna indietro");
            switch (leggiIntero("Scelta")) {
                case 1 -> { if (gestisciRegistrazione()) return true; }
                case 2 -> { if (gestisciLogin()) return true; }
                case 0 -> { return false; }
                default -> println("[AVVISO] Opzione non riconosciuta.");
            }
        }
    }

    private boolean gestisciRegistrazione() {
        stampaSezione("REGISTRAZIONE FRUITORE");
        while (true) {
            try {
                String u = leggiStringa("Scegli username");
                String p = leggiStringa("Scegli password");
                controller.registra(u, p);
                println("Registrazione completata. Benvenuto, "
                        + controller.getFruitoreLoggato().getUsername() + "!");
                return true;
            } catch (IllegalArgumentException e) {
                println("[ERRORE] " + e.getMessage());
                if (!chiediRiprova()) return false;
            } catch (IOException e) {
                println("[ERRORE I/O] " + e.getMessage());
                return false;
            }
        }
    }

    private boolean gestisciLogin() {
        stampaSezione("LOGIN FRUITORE");
        while (true) {
            try {
                String u = leggiStringa("Username");
                String p = leggiStringa("Password");
                controller.login(u, p);
                println("Benvenuto, " + controller.getFruitoreLoggato().getUsername() + "!");
                return true;
            } catch (IllegalArgumentException e) {
                println("[ERRORE] " + e.getMessage());
                if (!chiediRiprova()) return false;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Menu principale
    // -------------------------------------------------------------------------

    private void menuPrincipale() {
        while (true) {
            stampaSezione("MENU FRUITORE — " + controller.getFruitoreLoggato().getUsername());
            println("  1) Visualizza bacheca proposte aperte");
            println("  2) Aderisci a una proposta");
            println("  3) Spazio personale (notifiche + proposte)");
            println("  4) Cancella una notifica");
            println("  0) Esci");

            switch (leggiIntero("Scelta")) {
                case 1 -> optVisualizzaBacheca();
                case 2 -> optAderisci();
                case 3 -> optSpazioPersonale();
                case 4 -> optCancellaNotifica();
                case 0 -> { println("Arrivederci."); return; }
                default -> println("[AVVISO] Opzione non riconosciuta.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Opzioni menu
    // -------------------------------------------------------------------------

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
                        + "  aderenti: " + p.getAderenti().size());
                for (Map.Entry<String, String> v : p.getValori().entrySet()) {
                    println("      " + v.getKey() + " = " + v.getValue());
                }
            }
            println("");
        }
    }

    private void optAderisci() {
        stampaSezione("ADERISCI A UNA PROPOSTA");
        Bacheca bacheca = controller.getBacheca();
        List<Proposta> aperte = bacheca.tutte();
        if (aperte.isEmpty()) {
            println("Nessuna proposta APERTA disponibile.");
            return;
        }
        println("Proposte disponibili:");
        for (int i = 0; i < aperte.size(); i++) {
            Proposta p = aperte.get(i);
            println("  " + (i + 1) + ") " + p.getCategoria().getNome()
                    + "  aderenti: " + p.getAderenti().size()
                    + "/" + p.getValori().getOrDefault(Proposta.CAMPO_NUMERO_PARTECIPANTI, "?"));
        }
        int scelta = leggiIntero("Numero proposta (0 per annullare)");
        if (scelta == 0) return;
        if (scelta < 1 || scelta > aperte.size()) {
            println("[AVVISO] Selezione fuori intervallo.");
            return;
        }
        try {
            controller.aderisci(aperte.get(scelta - 1));
            println("Adesione registrata.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            println("[ERRORE] " + e.getMessage());
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    private void optSpazioPersonale() {
        stampaSezione("SPAZIO PERSONALE");
        SpazioPersonale sp = controller.getSpazioPersonale();

        println("--- Notifiche ---");
        List<Notifica> notifiche = sp.getNotifiche();
        if (notifiche.isEmpty()) {
            println("  Nessuna notifica ricevuta.");
        } else {
            for (Notifica n : notifiche) {
                println("  [" + n.getData() + "] categoria '" + n.getNomeCategoria()
                        + "' → stato " + n.getNuovoStato()
                        + "  (proposta id: " + n.getIdProposta() + ")");
                // Promemoria completo (data, ora, luogo, importo, ...) solo per CONFERMATA,
                // come da spec: "gli aderenti ricevono un promemoria relativo a data, ora
                // e luogo dell'evento nonché all'eventuale importo dovuto".
                if (n.getNuovoStato() == it.unibs.ingsw.model.StatoProposta.CONFERMATA) {
                    Map<String, String> snapshot = n.getSnapshotValori();
                    if (snapshot.isEmpty()) {
                        println("      (promemoria non disponibile)");
                    } else {
                        println("      PROMEMORIA:");
                        for (Map.Entry<String, String> e : snapshot.entrySet()) {
                            println("        " + e.getKey() + " = " + e.getValue());
                        }
                    }
                }
            }
        }

        println("");
        println("--- Proposte a cui hai aderito ---");
        List<Proposta> aderite = controller.getProposteAderite();
        if (aderite.isEmpty()) {
            println("  Nessuna adesione registrata.");
        } else {
            for (Proposta p : aderite) {
                println("  - " + p.getCategoria().getNome()
                        + "  (stato: " + p.getStato()
                        + ", aderenti: " + p.getAderenti().size() + ")");
            }
        }
    }

    private void optCancellaNotifica() {
        stampaSezione("CANCELLA UNA NOTIFICA");
        List<Notifica> notifiche = controller.getSpazioPersonale().getNotifiche();
        if (notifiche.isEmpty()) {
            println("Nessuna notifica da cancellare.");
            return;
        }
        for (int i = 0; i < notifiche.size(); i++) {
            Notifica n = notifiche.get(i);
            println("  " + (i + 1) + ") [" + n.getData() + "] '" + n.getNomeCategoria()
                    + "' → " + n.getNuovoStato());
        }
        int scelta = leggiIntero("Numero notifica da cancellare (0 per annullare)");
        if (scelta == 0) return;
        if (scelta < 1 || scelta > notifiche.size()) {
            println("[AVVISO] Selezione fuori intervallo.");
            return;
        }
        try {
            controller.cancellaNotifica(scelta - 1);
            println("Notifica cancellata.");
        } catch (IOException e) {
            println("[ERRORE I/O] " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helper input/output
    // -------------------------------------------------------------------------

    private boolean chiediRiprova() {
        while (true) {
            print("Riprovare? (s/n): ");
            String r = scanner.nextLine().trim().toLowerCase();
            if (r.equals("s") || r.equals("si") || r.equals("sì")) return true;
            if (r.equals("n") || r.equals("no")) return false;
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

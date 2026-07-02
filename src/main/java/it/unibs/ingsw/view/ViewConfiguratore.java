package it.unibs.ingsw.view;

import it.unibs.ingsw.controller.ControllerConfiguratore;
import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.ConfigurazioneGlobale;
import it.unibs.ingsw.model.Configuratore;
import it.unibs.ingsw.model.TipoCampo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

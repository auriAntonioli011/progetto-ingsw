package it.unibs.ingsw;

import it.unibs.ingsw.controller.ControllerConfiguratore;
import it.unibs.ingsw.controller.ControllerFruitore;
import it.unibs.ingsw.persistence.ArchivioCategorie;
import it.unibs.ingsw.persistence.ArchivioConfigurazione;
import it.unibs.ingsw.persistence.ArchivioConfiguratori;
import it.unibs.ingsw.persistence.ArchivioFruitori;
import it.unibs.ingsw.persistence.ArchivioProposte;
import it.unibs.ingsw.persistence.ArchivioTempoSimulato;
import it.unibs.ingsw.util.FornitoreTempo;
import it.unibs.ingsw.util.TempoReale;
import it.unibs.ingsw.util.TempoSimulato;
import it.unibs.ingsw.view.ViewConfiguratore;
import it.unibs.ingsw.view.ViewFruitore;

import java.io.IOException;
import java.util.Scanner;

/**
 * Entry point. Ordine di avvio V3:
 *  1. istanzia tutti gli archivi (compreso il nuovo ArchivioFruitori)
 *  2. crea ControllerFruitore e ne invoca inizializzaSessione(): ricollega gli
 *     osservatori, fa girare il motore transizioni sulle proposte, persiste
 *     stato aggiornato di proposte e fruitori (le notifiche accumulate nel
 *     salto temporale finiscono nei rispettivi SpazioPersonale).
 *  3. chiede all'utente il ruolo — Configuratore o Fruitore — e avvia la view
 *     corrispondente. ControllerConfiguratore viene creato solo se serve, così
 *     legge dal disco proposte già aggiornate dal passo 2.
 */
public class Main {

    public static void main(String[] args) {
        try {
            ArchivioConfigurazione archivioConfigurazione = new ArchivioConfigurazione();
            ArchivioCategorie archivioCategorie = new ArchivioCategorie();
            ArchivioConfiguratori archivioConfiguratori = new ArchivioConfiguratori();
            ArchivioProposte archivioProposte = new ArchivioProposte();
            ArchivioFruitori archivioFruitori = new ArchivioFruitori();
            ArchivioTempoSimulato archivioTempoSimulato = new ArchivioTempoSimulato();
            // V3: se data/tempo.json esiste, usa TempoSimulato con quella data;
            // altrimenti TempoReale. Il configuratore può creare/modificare/rimuovere
            // il file via menu, e il cambio prende effetto (pieno) al prossimo riavvio.
            FornitoreTempo tempo = archivioTempoSimulato.caricaDataSimulata()
                    .map(d -> (FornitoreTempo) new TempoSimulato(d))
                    .orElseGet(TempoReale::new);

            ControllerFruitore controllerFruitore = new ControllerFruitore(
                    archivioFruitori,
                    archivioConfiguratori,
                    archivioProposte,
                    tempo
            );
            // Passo 2: qui vengono ricollegati gli osservatori e fatto girare il motore
            // transizioni. Va invocato PRIMA di ControllerConfiguratore così eventuali
            // aggiornamenti a proposte.json sono già persistiti quando il configuratore
            // rilegge la sua copia in memoria.
            controllerFruitore.inizializzaSessione();

            try (Scanner scanner = new Scanner(System.in)) {
                if (scegliRuoloConfiguratore(scanner)) {
                    ControllerConfiguratore controllerConfiguratore = new ControllerConfiguratore(
                            archivioConfigurazione,
                            archivioCategorie,
                            archivioConfiguratori,
                            archivioProposte,
                            archivioTempoSimulato,
                            tempo
                    );
                    new ViewConfiguratore(controllerConfiguratore, scanner).avvia();
                } else {
                    new ViewFruitore(controllerFruitore, scanner).avvia();
                }
            }
        } catch (IOException e) {
            System.err.println("[ERRORE FATALE] Impossibile avviare l'applicazione: " + e.getMessage());
        }
    }

    // Prompt di selezione ruolo a livello Main: è un piccolo I/O di bootstrap
    // (analogo alla riga di errore su System.err già presente in V1) e non fa
    // parte di alcuna logica di dominio; le view restano l'unico punto di I/O
    // "operativo".
    private static boolean scegliRuoloConfiguratore(Scanner scanner) {
        while (true) {
            System.out.println();
            System.out.println("Chi sei?");
            System.out.println("  1) Configuratore");
            System.out.println("  2) Fruitore");
            System.out.print("Scelta: ");
            System.out.flush();
            String riga = scanner.nextLine().trim();
            if (riga.equals("1")) return true;
            if (riga.equals("2")) return false;
            System.out.println("[AVVISO] Digitare 1 o 2.");
        }
    }
}

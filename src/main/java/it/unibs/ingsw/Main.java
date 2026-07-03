package it.unibs.ingsw;

import it.unibs.ingsw.controller.ControllerConfiguratore;
import it.unibs.ingsw.persistence.ArchivioCategorie;
import it.unibs.ingsw.persistence.ArchivioConfigurazione;
import it.unibs.ingsw.persistence.ArchivioConfiguratori;
import it.unibs.ingsw.persistence.ArchivioProposte;
import it.unibs.ingsw.util.TempoReale;
import it.unibs.ingsw.view.ViewConfiguratore;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        try {
            ArchivioConfigurazione archivioConfigurazione = new ArchivioConfigurazione();
            ArchivioCategorie archivioCategorie = new ArchivioCategorie();
            ArchivioConfiguratori archivioConfiguratori = new ArchivioConfiguratori();
            ArchivioProposte archivioProposte = new ArchivioProposte();

            ControllerConfiguratore controller = new ControllerConfiguratore(
                    archivioConfigurazione,
                    archivioCategorie,
                    archivioConfiguratori,
                    archivioProposte,
                    new TempoReale()
            );

            Scanner scanner = new Scanner(System.in);
            ViewConfiguratore view = new ViewConfiguratore(controller, scanner);
            view.avvia();

            scanner.close();
        } catch (IOException e) {
            System.err.println("[ERRORE FATALE] Impossibile avviare l'applicazione: " + e.getMessage());
        }
    }
}

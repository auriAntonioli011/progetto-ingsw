package it.unibs.ingsw.model;

/**
 * Utente con ruolo di configuratore del sistema.
 * Predisposta per estensioni future (V2+): in V1 non aggiunge attributi né comportamenti.
 */
public class Configuratore extends Utente {

    // pre:  username != null && !username.isBlank()
    //       && password != null && !password.isBlank()
    // post: super(username, password) — credenzialiProvvisorie == true
    public Configuratore(String username, String password) {
        super(username, password);
    }
}

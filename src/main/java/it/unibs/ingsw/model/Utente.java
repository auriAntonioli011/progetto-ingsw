package it.unibs.ingsw.model;

import java.util.Objects;

/**
 * Utente del sistema. Lo username non è dichiarato final perché
 * confermaCredenzialiPersonali() deve poterlo sostituire al primo accesso:
 * la mutabilità è intenzionale ma vincolata — l'unico percorso di modifica è
 * quel metodo, protetto dal flag credenzialiProvvisorie. Dopo la conferma lo
 * username è di fatto immutabile per tutta la vita dell'oggetto.
 *
 * Attenzione: inserire un Utente con credenziali provvisorie in un Set o come
 * chiave di una Map e poi chiamare confermaCredenzialiPersonali() invalida la
 * sua posizione nella struttura, perché hashCode cambierebbe. Gestire questo
 * caso è responsabilità dell'archivio/contenitore.
 */
public abstract class Utente {

    private String username;
    private String password;
    private boolean credenzialiProvvisorie;

    // pre:  username != null && !username.isBlank()
    //       && password != null && !password.isBlank()
    // post: this.username == username && this.password == password
    //       && credenzialiProvvisorie == true
    // inv:  username e password non sono mai null né blank
    protected Utente(String username, String password) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("username non può essere null o vuoto");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("password non può essere null o vuota");
        this.username = username;
        this.password = password;
        this.credenzialiProvvisorie = true;
    }

    // post: restituisce lo username corrente; non null né blank
    public String getUsername() {
        return username;
    }

    // post: restituisce true se le credenziali sono ancora quelle provvisorie assegnate
    //       alla creazione dell'account
    public boolean isCredenzialiProvvisorie() {
        return credenzialiProvvisorie;
    }

    // pre:  tentativo != null
    // post: restituisce true se e solo se tentativo è uguale alla password corrente
    //       (confronto in chiaro — conforme alla Nota 4 del testo del progetto)
    public boolean verificaPassword(String tentativo) {
        if (tentativo == null) return false;
        return password.equals(tentativo);
    }

    // pre:  nuovaPassword != null && !nuovaPassword.isBlank()
    // post: this.password == nuovaPassword
    public void cambiaPassword(String nuovaPassword) {
        if (nuovaPassword == null || nuovaPassword.isBlank())
            throw new IllegalArgumentException("nuovaPassword non può essere null o vuota");
        this.password = nuovaPassword;
    }

    // pre:  credenzialiProvvisorie == true
    //       && nuovoUsername != null && !nuovoUsername.isBlank()
    //       && nuovaPassword != null && !nuovaPassword.isBlank()
    // post: this.username == nuovoUsername && this.password == nuovaPassword
    //       && credenzialiProvvisorie == false
    // inv:  può essere chiamato una sola volta per istanza
    public void confermaCredenzialiPersonali(String nuovoUsername, String nuovaPassword) {
        if (!credenzialiProvvisorie)
            throw new IllegalStateException(
                    "le credenziali personali sono già state confermate");
        if (nuovoUsername == null || nuovoUsername.isBlank())
            throw new IllegalArgumentException("nuovoUsername non può essere null o vuoto");
        if (nuovaPassword == null || nuovaPassword.isBlank())
            throw new IllegalArgumentException("nuovaPassword non può essere null o vuota");
        this.username = nuovoUsername;
        this.password = nuovaPassword;
        this.credenzialiProvvisorie = false;
    }

    // post: restituisce true se o è un Utente con lo stesso username (case-sensitive)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Utente altro)) return false;
        return username.equals(altro.username);
    }

    // inv:  consistente con equals; cambia se username cambia via confermaCredenzialiPersonali
    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    // post: rappresentazione leggibile per debugging; non usata nella view
    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{username='" + username
                + "', credenzialiProvvisorie=" + credenzialiProvvisorie + "}";
    }
}

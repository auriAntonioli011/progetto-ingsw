package it.unibs.ingsw.model;

/**
 * Utente con ruolo di fruitore del sistema. Al contrario del Configuratore,
 * il Fruitore si registra autonomamente scegliendo direttamente le proprie
 * credenziali definitive: non passa per il flusso "credenziali provvisorie".
 *
 * Implementa OsservatoreProposta: quando aderisce a una proposta viene
 * registrato come osservatore e riceve le notifiche di transizione di stato,
 * che vengono accumulate nel proprio SpazioPersonale.
 */
public class Fruitore extends Utente implements OsservatoreProposta {

    private SpazioPersonale spazioPersonale;

    // pre:  username != null && !username.isBlank()
    //       && password != null && !password.isBlank()
    // post: super(username, password) — credenzialiProvvisorie == true
    //       && spazioPersonale non null e vuoto
    // nota: il flusso di registrazione (ControllerFruitore.registra) invoca subito
    //       confermaCredenzialiPersonali con gli stessi valori per portare
    //       credenzialiProvvisorie a false — il Fruitore non ha un percorso a due
    //       fasi come il Configuratore.
    public Fruitore(String username, String password) {
        super(username, password);
        this.spazioPersonale = new SpazioPersonale();
    }

    // post: restituisce lo spazio personale, inizializzandolo se null
    //       (necessario perché Gson bypassa il costruttore in deserializzazione)
    public SpazioPersonale getSpazioPersonale() {
        if (spazioPersonale == null) spazioPersonale = new SpazioPersonale();
        return spazioPersonale;
    }

    // pre:  proposta != null && notifica != null
    // post: la notifica è aggiunta al proprio spazio personale
    // inv:  non lancia eccezioni: la notifica è una comunicazione unidirezionale
    //       dalla Proposta agli osservatori (contratto OsservatoreProposta)
    @Override
    public void aggiorna(Proposta proposta, Notifica notifica) {
        if (proposta == null || notifica == null) return;
        getSpazioPersonale().aggiungiNotifica(notifica);
    }
}

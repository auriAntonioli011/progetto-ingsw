package it.unibs.ingsw.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenitore personale di un Fruitore: raccoglie le notifiche ricevute.
 *
 * Le "proposte a cui il fruitore ha aderito" NON sono memorizzate qui: sarebbero
 * ridondanti rispetto a Proposta.getAderenti() ed esporrebbero rischio di
 * disallineamento. Il controller le ricava scorrendo l'archivio proposte e
 * filtrando per username, coerentemente con la scelta V2 di non duplicare stato.
 */
public class SpazioPersonale {

    private List<Notifica> notifiche;

    // post: notifiche è una lista vuota mutabile
    public SpazioPersonale() {
        this.notifiche = new ArrayList<>();
    }

    // pre:  notifica != null
    // post: notifica è aggiunta in coda alla lista notifiche
    public void aggiungiNotifica(Notifica notifica) {
        if (notifica == null)
            throw new IllegalArgumentException("notifica non può essere null");
        if (notifiche == null) notifiche = new ArrayList<>();
        notifiche.add(notifica);
    }

    // post: restituisce una vista immutabile della lista notifiche;
    //       lista vuota se il fruitore non ha mai ricevuto notifiche
    public List<Notifica> getNotifiche() {
        if (notifiche == null) notifiche = new ArrayList<>();
        return List.copyOf(notifiche);
    }

    // post: restituisce true se non ci sono notifiche registrate
    public boolean isVuoto() {
        return notifiche == null || notifiche.isEmpty();
    }

    // pre:  indice >= 0 && indice < getNotifiche().size()
    // post: la notifica in posizione indice è rimossa; le altre restano nell'ordine
    //       precedente. Lancia IndexOutOfBoundsException se indice fuori intervallo.
    public void rimuoviNotifica(int indice) {
        if (notifiche == null) notifiche = new ArrayList<>();
        if (indice < 0 || indice >= notifiche.size())
            throw new IndexOutOfBoundsException(
                    "indice fuori intervallo: " + indice + " (dimensione: " + notifiche.size() + ")");
        notifiche.remove(indice);
    }
}

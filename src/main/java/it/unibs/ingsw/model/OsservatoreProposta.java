package it.unibs.ingsw.model;

/**
 * Observer di dominio: chi implementa questa interfaccia riceve aggiornamenti sui
 * cambi di stato di una Proposta a cui è registrato.
 *
 * L'implementazione di riferimento è Fruitore: quando aderisce a una proposta,
 * viene registrato come osservatore e riceve le notifiche successive
 * (CONFERMATA, ANNULLATA, CONCLUSA).
 */
public interface OsservatoreProposta {

    // pre:  proposta != null && notifica != null
    // post: l'osservatore ha registrato l'evento (tipicamente aggiungendo la
    //       notifica al proprio spazio personale). Nessuna eccezione di dominio
    //       deve essere sollevata: la notifica è "fire and forget" dal punto di
    //       vista del soggetto.
    void aggiorna(Proposta proposta, Notifica notifica);
}

package it.unibs.ingsw.model;

/**
 * Stato di una Proposta.
 *
 * V2 introduce solamente i due stati necessari al ciclo "creazione → validazione → pubblicazione":
 *   - VALIDA: la proposta soddisfa tutti i vincoli e può essere pubblicata (non ancora in bacheca).
 *   - APERTA: la proposta è stata pubblicata ed è visibile in bacheca.
 *
 * Gli stati successivi (chiusa, fallita, conclusa, ecc.) saranno introdotti in V3/V4
 * insieme al ciclo di vita completo e allo State pattern esplicito, come da nota del progetto.
 */
public enum StatoProposta {
    VALIDA,
    APERTA
}

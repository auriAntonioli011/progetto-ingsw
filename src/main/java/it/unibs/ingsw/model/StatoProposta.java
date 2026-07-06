package it.unibs.ingsw.model;

/**
 * Stato di una Proposta.
 *
 * Ciclo di vita completo:
 *   - VALIDA: la proposta soddisfa tutti i vincoli e può essere pubblicata (non ancora in bacheca). [V2]
 *   - APERTA: pubblicata, visibile in bacheca, accetta iscrizioni. [V2]
 *   - CONFERMATA: raggiunto il "Numero di partecipanti" entro il termine iscrizione. [V3]
 *   - ANNULLATA: termine iscrizione superato senza raggiungere il "Numero di partecipanti". [V3]
 *   - CONCLUSA: passato il giorno successivo alla "Data conclusiva". [V3]
 *   - RITIRATA: il configuratore ha ritirato esplicitamente la proposta prima del giorno
 *               dell'evento (azione manuale, non automatica). [V4]
 *
 * Transizioni V3 (§3.2 della guida): APERTA → {CONFERMATA, ANNULLATA} a mezzanotte del termine
 * di iscrizione; CONFERMATA → CONCLUSA il giorno successivo alla data conclusiva. Nessuna
 * transizione uscente da ANNULLATA o CONCLUSA (stati terminali).
 *
 * Transizione V4: {APERTA, CONFERMATA} → RITIRATA su richiesta esplicita del configuratore,
 * consentita fino alle 23:59 del giorno precedente la "Data" dell'evento. RITIRATA è terminale:
 * il motore transizioni (elaboraTransizione) non produce alcuna transizione in uscita.
 */
public enum StatoProposta {
    VALIDA,
    APERTA,
    CONFERMATA,
    ANNULLATA,
    CONCLUSA,
    RITIRATA
}

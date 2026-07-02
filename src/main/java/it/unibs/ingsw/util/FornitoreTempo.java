package it.unibs.ingsw.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Astrazione del tempo per il dominio: impedisce l'uso diretto di LocalDate.now()
 * e LocalDateTime.now() nel model, rendendo il tempo iniettabile e testabile.
 */
public interface FornitoreTempo {

    // post: restituisce la data corrente secondo questa fonte del tempo; non null
    LocalDate oggi();

    // post: restituisce la data e l'ora correnti secondo questa fonte del tempo; non null
    LocalDateTime adesso();
}

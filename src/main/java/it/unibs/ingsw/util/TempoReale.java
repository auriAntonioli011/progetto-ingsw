package it.unibs.ingsw.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Implementazione di FornitoreTempo che delega all'orologio di sistema.
 * Usata in produzione.
 */
public class TempoReale implements FornitoreTempo {

    // post: restituisce LocalDate.now()
    @Override
    public LocalDate oggi() {
        return LocalDate.now();
    }

    // post: restituisce LocalDateTime.now()
    @Override
    public LocalDateTime adesso() {
        return LocalDateTime.now();
    }
}

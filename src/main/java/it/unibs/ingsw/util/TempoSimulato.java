package it.unibs.ingsw.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Implementazione di FornitoreTempo con data/ora configurabile manualmente.
 * Usata nei test e nella modalità configuratore per simulare scorrere del tempo.
 */
public class TempoSimulato implements FornitoreTempo {

    private LocalDateTime dataOraCorrente;

    // pre:  dataIniziale != null
    // post: adesso() == dataIniziale && oggi() == dataIniziale.toLocalDate()
    public TempoSimulato(LocalDateTime dataIniziale) {
        if (dataIniziale == null) throw new IllegalArgumentException("dataIniziale non può essere null");
        this.dataOraCorrente = dataIniziale;
    }

    // pre:  dataIniziale != null
    // post: adesso() corrisponde a dataIniziale alle 00:00
    public TempoSimulato(LocalDate dataIniziale) {
        if (dataIniziale == null) throw new IllegalArgumentException("dataIniziale non può essere null");
        this.dataOraCorrente = dataIniziale.atTime(LocalTime.MIDNIGHT);
    }

    // post: restituisce la data della dataOraCorrente simulata; non null
    @Override
    public LocalDate oggi() {
        return dataOraCorrente.toLocalDate();
    }

    // post: restituisce dataOraCorrente; non null
    @Override
    public LocalDateTime adesso() {
        return dataOraCorrente;
    }

    // pre:  giorni > 0
    // post: oggi() == old(oggi()).plusDays(giorni)
    public void avanzaGiorni(int giorni) {
        if (giorni <= 0) throw new IllegalArgumentException("giorni deve essere positivo");
        dataOraCorrente = dataOraCorrente.plusDays(giorni);
    }

    // pre:  ore > 0
    // post: adesso() == old(adesso()).plusHours(ore)
    public void avanzaOre(int ore) {
        if (ore <= 0) throw new IllegalArgumentException("ore deve essere positivo");
        dataOraCorrente = dataOraCorrente.plusHours(ore);
    }

    // pre:  nuovaData != null
    // post: adesso() == nuovaData && oggi() == nuovaData.toLocalDate()
    public void impostaData(LocalDateTime nuovaData) {
        if (nuovaData == null) throw new IllegalArgumentException("nuovaData non può essere null");
        this.dataOraCorrente = nuovaData;
    }
}

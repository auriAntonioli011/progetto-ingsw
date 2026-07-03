package it.unibs.ingsw.model;

import it.unibs.ingsw.util.FornitoreTempo;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Proposta di scambio riferita a una Categoria.
 *
 * I valori dei campi sono memorizzati come Map<String,String> (chiave = nome del Campo,
 * valore = rappresentazione testuale del valore inserito dall'utente). L'interpretazione
 * tipizzata (es. parsing di una data) avviene solo quando serve, guidata da Campo.getTipo(),
 * per evitare polimorfismo nella serializzazione JSON — coerente con la scelta V1 di
 * mantenere Gson semplice.
 *
 * Nomi convenzionali dei campi data usati dalla validazione (a-b-c) — CAMPO_TERMINE_ISCRIZIONE,
 * CAMPO_DATA_EVENTO, CAMPO_DATA_CONCLUSIVA — sono costanti pubbliche: il configuratore deve
 * definire campi base con questi nomi (di TipoCampo.DATA) perché una proposta possa risultare
 * VALIDA. Se un nome atteso non è presente o il valore non è parsificabile come LocalDate,
 * valida() ritorna false senza modificare lo stato.
 */
public class Proposta {

    // Nomi convenzionali dei campi data controllati da valida().
    // Vedi commento di classe: devono coincidere con nomi di campi base di TipoCampo.DATA.
    public static final String CAMPO_TERMINE_ISCRIZIONE = "Termine ultimo di iscrizione";
    public static final String CAMPO_DATA_EVENTO = "Data";
    public static final String CAMPO_DATA_CONCLUSIVA = "Data conclusiva";

    private final Categoria categoria;
    private final Map<String, String> valori;
    private StatoProposta stato;
    private LocalDate dataPubblicazione;

    // pre:  categoria != null && valori != null
    // post: this.categoria == categoria
    //       && this.valori è una copia difensiva di valori
    //       && this.stato == null (non ancora validata)
    //       && this.dataPubblicazione == null
    // inv:  categoria e valori non sono mai null; stato passa da null a VALIDA e da VALIDA ad APERTA
    public Proposta(Categoria categoria, Map<String, String> valori) {
        if (categoria == null)
            throw new IllegalArgumentException("categoria non può essere null");
        if (valori == null)
            throw new IllegalArgumentException("valori non può essere null");
        this.categoria = categoria;
        this.valori = new HashMap<>(valori);
        this.stato = null;
        this.dataPubblicazione = null;
    }

    // post: restituisce la categoria di riferimento; non null
    public Categoria getCategoria() {
        return categoria;
    }

    // post: restituisce una vista immutabile della mappa dei valori
    public Map<String, String> getValori() {
        return Map.copyOf(valori);
    }

    // post: restituisce lo stato corrente; null se valida() non è ancora stata invocata con esito positivo
    public StatoProposta getStato() {
        return stato;
    }

    // post: restituisce la data di pubblicazione; null finché la proposta non è APERTA
    public LocalDate getDataPubblicazione() {
        return dataPubblicazione;
    }

    // pre:  fornitoreTempo != null && configurazioneGlobale != null
    // post: se tutti i vincoli (a) termine iscrizione > oggi,
    //                          (b) data evento >= termine iscrizione + 2 giorni,
    //                          (c) dataConclusiva >= data evento,
    //                          (d) tutti i campi obbligatori valorizzati non vuoti
    //       sono soddisfatti, allora stato = VALIDA e restituisce true;
    //       altrimenti stato resta invariato (non promuove ad APERTA né declassa VALIDA) e restituisce false.
    // inv:  non usa mai LocalDate.now(): tutte le date correnti passano per fornitoreTempo.oggi().
    //       ConfigurazioneGlobale viene ricevuta come parametro (non conservata) per rispettare
    //       la stessa convenzione già adottata da Categoria.getTuttiICampi.
    public boolean valida(FornitoreTempo fornitoreTempo, ConfigurazioneGlobale configurazioneGlobale) {
        if (fornitoreTempo == null)
            throw new IllegalArgumentException("fornitoreTempo non può essere null");
        if (configurazioneGlobale == null)
            throw new IllegalArgumentException("configurazioneGlobale non può essere null");

        // (d) campi obbligatori
        List<Campo> tutti = categoria.getTuttiICampi(configurazioneGlobale);
        for (Campo c : tutti) {
            if (c.isObbligatorio()) {
                String v = valori.get(c.getNome());
                if (v == null || v.isBlank()) return false;
            }
        }

        // (a-b-c) coerenza date
        LocalDate termineIscrizione = leggiData(CAMPO_TERMINE_ISCRIZIONE);
        LocalDate dataEvento = leggiData(CAMPO_DATA_EVENTO);
        LocalDate dataConclusiva = leggiData(CAMPO_DATA_CONCLUSIVA);
        if (termineIscrizione == null || dataEvento == null || dataConclusiva == null)
            return false;

        LocalDate oggi = fornitoreTempo.oggi();
        if (!termineIscrizione.isAfter(oggi)) return false;                       // (a)
        if (dataEvento.isBefore(termineIscrizione.plusDays(2))) return false;      // (b)
        if (dataConclusiva.isBefore(dataEvento)) return false;                     // (c)

        this.stato = StatoProposta.VALIDA;
        return true;
    }

    // pre:  stato == StatoProposta.VALIDA && dataPubblicazione != null
    // post: stato == StatoProposta.APERTA && this.dataPubblicazione == dataPubblicazione
    // inv:  la transizione VALIDA → APERTA è irreversibile in V2
    public void marcaPubblicata(LocalDate dataPubblicazione) {
        if (stato != StatoProposta.VALIDA)
            throw new IllegalStateException(
                    "solo una proposta VALIDA può essere marcata come pubblicata");
        if (dataPubblicazione == null)
            throw new IllegalArgumentException("dataPubblicazione non può essere null");
        this.stato = StatoProposta.APERTA;
        this.dataPubblicazione = dataPubblicazione;
    }

    // Legge il valore grezzo di un campo data e lo interpreta come LocalDate ISO.
    // Restituisce null se la chiave non è presente, il valore è vuoto o il parsing fallisce:
    // la scelta è deliberata — valida() usa il null come segnale di "vincolo non verificabile"
    // e restituisce false senza rompere il flusso.
    private LocalDate leggiData(String nomeCampo) {
        String v = valori.get(nomeCampo);
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // post: due proposte sono uguali se hanno la stessa categoria e la stessa mappa valori.
    //       Serve principalmente per evitare duplicati in memoria; equals su identità sarebbe
    //       comunque accettabile — questa è una scelta prudente.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Proposta altra)) return false;
        return Objects.equals(categoria, altra.categoria)
                && Objects.equals(valori, altra.valori);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoria, valori);
    }

    @Override
    public String toString() {
        return "Proposta{categoria=" + categoria.getNome()
                + ", stato=" + stato
                + ", dataPubblicazione=" + dataPubblicazione
                + ", valori=" + valori.size() + "}";
    }
}

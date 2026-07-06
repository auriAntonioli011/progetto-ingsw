package it.unibs.ingsw.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Valore immutabile che rappresenta una notifica ricevuta dallo spazio personale
 * di un Fruitore in seguito a una transizione di stato di una Proposta.
 *
 * V3 — Contenuto informativo (fix post-review):
 *  - idProposta: id stabile della Proposta (Proposta.getId()), necessario per
 *    distinguere due proposte diverse della stessa categoria — un fruitore
 *    iscritto a più proposte della stessa Categoria altrimenti non saprebbe
 *    quale è stata confermata/annullata/conclusa.
 *  - snapshotValori: copia immutabile dei valori della Proposta al momento della
 *    transizione, sufficiente a estrarre il "promemoria" richiesto dalla spec
 *    (data-evento, ora, luogo, importo dovuto) per lo stato CONFERMATA. Snapshot,
 *    non reference: modifiche successive alla Proposta non alterano la Notifica
 *    già recapitata.
 *
 * Volutamente NON contiene un riferimento diretto a Proposta: le due entità
 * vivono in archivi distinti (proposte.json / fruitori.json) e propagare la
 * reference creerebbe cicli di serializzazione. idProposta + snapshotValori
 * bastano sia a mostrare il promemoria sia — se serve — a ritrovare la Proposta
 * originale via lookup nell'archivio.
 */
public class Notifica {

    private final String idProposta;
    private final String nomeCategoria;
    private final StatoProposta nuovoStato;
    private final LocalDate data;
    private final Map<String, String> snapshotValori;

    // pre:  idProposta != null && !idProposta.isBlank()
    //       && nomeCategoria != null && !nomeCategoria.isBlank()
    //       && nuovoStato != null && data != null
    //       && snapshotValori != null (può essere vuoto)
    // post: i campi sono valorizzati con i parametri; snapshotValori è una copia
    //       immutabile di quello passato (defensive copy)
    // inv:  la Notifica è immutabile dopo la costruzione
    public Notifica(String idProposta,
                    String nomeCategoria,
                    StatoProposta nuovoStato,
                    LocalDate data,
                    Map<String, String> snapshotValori) {
        if (idProposta == null || idProposta.isBlank())
            throw new IllegalArgumentException("idProposta non può essere null o vuoto");
        if (nomeCategoria == null || nomeCategoria.isBlank())
            throw new IllegalArgumentException("nomeCategoria non può essere null o vuoto");
        if (nuovoStato == null)
            throw new IllegalArgumentException("nuovoStato non può essere null");
        if (data == null)
            throw new IllegalArgumentException("data non può essere null");
        if (snapshotValori == null)
            throw new IllegalArgumentException("snapshotValori non può essere null");
        this.idProposta = idProposta;
        this.nomeCategoria = nomeCategoria;
        this.nuovoStato = nuovoStato;
        this.data = data;
        this.snapshotValori = Map.copyOf(snapshotValori);
    }

    // post: restituisce l'id della Proposta a cui si riferisce la notifica
    public String getIdProposta() {
        return idProposta;
    }

    // post: restituisce il nome della categoria della proposta a cui si riferisce
    public String getNomeCategoria() {
        return nomeCategoria;
    }

    // post: restituisce lo stato in cui è transitata la proposta
    public StatoProposta getNuovoStato() {
        return nuovoStato;
    }

    // post: restituisce la data in cui è stata prodotta la notifica
    public LocalDate getData() {
        return data;
    }

    // post: restituisce una vista immutabile dei valori della Proposta al momento
    //       della transizione (snapshot); mai null (eventualmente vuoto).
    //       La view estrae da qui il promemoria per CONFERMATA.
    public Map<String, String> getSnapshotValori() {
        // Fallback difensivo per JSON deserializzato senza il campo (legacy o file corrotto):
        // Gson bypassa il costruttore, quindi il final può risultare null se assente in JSON.
        if (snapshotValori == null) return Map.of();
        return snapshotValori;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notifica altra)) return false;
        return Objects.equals(idProposta, altra.idProposta)
                && nuovoStato == altra.nuovoStato
                && Objects.equals(data, altra.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idProposta, nuovoStato, data);
    }

    @Override
    public String toString() {
        return "Notifica{idProposta=" + idProposta
                + ", cat=" + nomeCategoria
                + ", nuovoStato=" + nuovoStato
                + ", data=" + data + "}";
    }
}

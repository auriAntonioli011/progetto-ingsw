package it.unibs.ingsw.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vista aggregata delle sole proposte APERTE, raggruppabili per Categoria.
 *
 * Bacheca è una view di dominio: non è un archivio, non persiste nulla, si costruisce
 * a partire dalla lista di proposte APERTE mantenuta dal controller. Fornire questa
 * astrazione (invece di ritornare direttamente una Map dal controller) mantiene la view
 * disaccoppiata dalla struttura dati scelta.
 */
public class Bacheca {

    private final List<Proposta> proposteAperte;

    // pre:  proposteAperte != null
    //       && ogni elemento ha stato == StatoProposta.APERTA
    // post: this.proposteAperte è una copia difensiva di proposteAperte
    // inv:  contiene solo proposte con stato APERTA
    public Bacheca(List<Proposta> proposteAperte) {
        if (proposteAperte == null)
            throw new IllegalArgumentException("proposteAperte non può essere null");
        for (Proposta p : proposteAperte) {
            if (p == null || p.getStato() != StatoProposta.APERTA)
                throw new IllegalArgumentException(
                        "la bacheca può contenere solo proposte APERTA");
        }
        this.proposteAperte = new ArrayList<>(proposteAperte);
    }

    // post: restituisce una vista immutabile della lista di proposte APERTE
    public List<Proposta> tutte() {
        return List.copyOf(proposteAperte);
    }

    // post: restituisce una mappa Categoria → lista di proposte APERTE di quella categoria.
    //       L'ordine di inserimento è preservato (LinkedHashMap). Le liste interne sono immutabili.
    public Map<Categoria, List<Proposta>> raggruppatePerCategoria() {
        Map<Categoria, List<Proposta>> risultato = new LinkedHashMap<>();
        for (Proposta p : proposteAperte) {
            risultato.computeIfAbsent(p.getCategoria(), k -> new ArrayList<>()).add(p);
        }
        Map<Categoria, List<Proposta>> immutabile = new LinkedHashMap<>();
        for (Map.Entry<Categoria, List<Proposta>> e : risultato.entrySet()) {
            immutabile.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Map.copyOf(immutabile);
    }

    // post: restituisce true se la bacheca non contiene proposte
    public boolean isVuota() {
        return proposteAperte.isEmpty();
    }
}

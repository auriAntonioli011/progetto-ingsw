package it.unibs.ingsw.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurazione globale del sistema: definisce i campi base (immutabili dopo la prima
 * fissazione) e i campi comuni (modificabili in qualsiasi momento).
 *
 * Vincolo di unicità nomi: un campo comune non può avere lo stesso nome di un campo base,
 * per evitare ambiguità quando una Categoria assembla il proprio elenco completo di campi.
 */
public class ConfigurazioneGlobale {

    private List<Campo> campiBase;
    private List<Campo> campiComuni;
    private boolean campiBaseFissati;

    // post: campiBase vuota, campiComuni vuota, campiBaseFissati() == false
    public ConfigurazioneGlobale() {
        this.campiBase = new ArrayList<>();
        this.campiComuni = new ArrayList<>();
        this.campiBaseFissati = false;
    }

    // pre:  nuoviCampiBase != null && !nuoviCampiBase.isEmpty() && !campiBaseFissati()
    // post: getCampiBase() restituisce una copia di nuoviCampiBase && campiBaseFissati() == true
    // inv:  dopo questa chiamata i campi base non possono più essere modificati
    public void fissaCampiBase(List<Campo> nuoviCampiBase) {
        if (nuoviCampiBase == null)
            throw new IllegalArgumentException("nuoviCampiBase non può essere null");
        if (nuoviCampiBase.isEmpty())
            throw new IllegalArgumentException("nuoviCampiBase non può essere vuota");
        if (campiBaseFissati)
            throw new IllegalStateException("i campi base sono già stati fissati e non possono essere modificati");
        this.campiBase = new ArrayList<>(nuoviCampiBase);
        this.campiBaseFissati = true;
    }

    // pre:  nuoviCampiComuni != null
    //       && nessun elemento di nuoviCampiComuni ha lo stesso nome di un campo base fissato
    // post: getCampiComuni() restituisce una copia di nuoviCampiComuni
    public void impostaCampiComuni(List<Campo> nuoviCampiComuni) {
        if (nuoviCampiComuni == null)
            throw new IllegalArgumentException("nuoviCampiComuni non può essere null");
        for (Campo c : nuoviCampiComuni)
            verificaNonConflittoConBase(c);
        this.campiComuni = new ArrayList<>(nuoviCampiComuni);
    }

    // pre:  campo != null && campo.getNome() non coincide con il nome di alcun campo base fissato
    // post: campo è aggiunto in coda a campiComuni
    public void aggiungiCampoComune(Campo campo) {
        if (campo == null)
            throw new IllegalArgumentException("campo non può essere null");
        verificaNonConflittoConBase(campo);
        campiComuni.add(campo);
    }

    // pre:  nomeCampo != null && !nomeCampo.isBlank()
    //       && esiste almeno un campo in campiComuni con quel nome
    // post: il campo con nome == nomeCampo è rimosso da campiComuni
    public void rimuoviCampoComune(String nomeCampo) {
        if (nomeCampo == null || nomeCampo.isBlank())
            throw new IllegalArgumentException("nomeCampo non può essere null o vuoto");
        boolean rimosso = campiComuni.removeIf(c -> c.getNome().equals(nomeCampo));
        if (!rimosso)
            throw new IllegalArgumentException("nessun campo comune con nome '" + nomeCampo + "'");
    }

    // post: restituisce una vista immutabile di campiBase; lista vuota se non ancora fissati
    public List<Campo> getCampiBase() {
        return List.copyOf(campiBase);
    }

    // post: restituisce una vista immutabile di campiComuni
    public List<Campo> getCampiComuni() {
        return List.copyOf(campiComuni);
    }

    // post: restituisce true se e solo se fissaCampiBase() è già stato invocato con successo
    public boolean campiBaseFissati() {
        return campiBaseFissati;
    }

    // Controlla che il nome di campo non collida con nessun campo base già fissato.
    // Campo.equals è definito sul nome, quindi contains() esegue esattamente il confronto
    // sul nome (case-sensitive) senza logica aggiuntiva.
    private void verificaNonConflittoConBase(Campo campo) {
        if (campiBase.contains(campo))
            throw new IllegalArgumentException(
                    "il campo '" + campo.getNome() + "' ha lo stesso nome di un campo base già fissato");
    }
}

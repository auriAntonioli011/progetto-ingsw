package it.unibs.ingsw.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Categoria di scambio: aggrega i campi specifici che si sommano ai campi base
 * e comuni definiti dalla ConfigurazioneGlobale.
 *
 * Vincolo di unicità nomi: il nome è unico nel sistema; la responsabilità di
 * garantirlo appartiene all'archivio/contenitore, non a questa classe.
 */
public class Categoria {

    private final String nome;
    private final List<Campo> campiSpecifici;

    // pre:  nome != null && !nome.isBlank()
    // post: this.nome == nome && getCampiSpecifici().isEmpty()
    // inv:  nome non è mai null né blank
    public Categoria(String nome) {
        if (nome == null || nome.isBlank())
            throw new IllegalArgumentException("nome non può essere null o vuoto");
        this.nome = nome;
        this.campiSpecifici = new ArrayList<>();
    }

    // post: restituisce il nome della categoria; non null né blank
    public String getNome() {
        return nome;
    }

    // pre:  campo != null && configurazioneGlobale != null
    //       && il nome di campo non coincide con nessun campo base di configurazioneGlobale
    //       && il nome di campo non coincide con nessun campo comune di configurazioneGlobale
    //       && il nome di campo non coincide con nessun campo specifico già presente
    // post: campo è aggiunto in coda a campiSpecifici
    // inv:  nessun campo specifico ha lo stesso nome di un campo base, comune o di un altro specifico
    public void aggiungiCampoSpecifico(Campo campo, ConfigurazioneGlobale configurazioneGlobale) {
        if (campo == null)
            throw new IllegalArgumentException("campo non può essere null");
        if (configurazioneGlobale == null)
            throw new IllegalArgumentException("configurazioneGlobale non può essere null");

        if (configurazioneGlobale.getCampiBase().contains(campo))
            throw new IllegalArgumentException(
                    "il campo '" + campo.getNome() + "' ha lo stesso nome di un campo base");
        if (configurazioneGlobale.getCampiComuni().contains(campo))
            throw new IllegalArgumentException(
                    "il campo '" + campo.getNome() + "' ha lo stesso nome di un campo comune");
        if (campiSpecifici.contains(campo))
            throw new IllegalArgumentException(
                    "il campo '" + campo.getNome() + "' è già presente tra i campi specifici");

        campiSpecifici.add(campo);
    }

    // pre:  nomeCampo != null && !nomeCampo.isBlank()
    //       && esiste un campo in campiSpecifici con quel nome
    // post: il campo con nome == nomeCampo è rimosso da campiSpecifici
    public void rimuoviCampoSpecifico(String nomeCampo) {
        if (nomeCampo == null || nomeCampo.isBlank())
            throw new IllegalArgumentException("nomeCampo non può essere null o vuoto");
        boolean rimosso = campiSpecifici.removeIf(c -> c.getNome().equals(nomeCampo));
        if (!rimosso)
            throw new IllegalArgumentException(
                    "nessun campo specifico con nome '" + nomeCampo + "'");
    }

    // post: restituisce una vista immutabile di campiSpecifici
    public List<Campo> getCampiSpecifici() {
        return List.copyOf(campiSpecifici);
    }

    // pre:  configurazioneGlobale != null
    // post: restituisce una lista immutabile che concatena, nell'ordine,
    //       campi base + campi comuni (da configurazioneGlobale) + campi specifici
    public List<Campo> getTuttiICampi(ConfigurazioneGlobale configurazioneGlobale) {
        if (configurazioneGlobale == null)
            throw new IllegalArgumentException("configurazioneGlobale non può essere null");
        List<Campo> tutti = new ArrayList<>();
        tutti.addAll(configurazioneGlobale.getCampiBase());
        tutti.addAll(configurazioneGlobale.getCampiComuni());
        tutti.addAll(campiSpecifici);
        return List.copyOf(tutti);
    }

    // post: restituisce true se o è una Categoria con lo stesso nome (case-sensitive)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Categoria altra)) return false;
        return nome.equals(altra.nome);
    }

    // inv:  consistente con equals
    @Override
    public int hashCode() {
        return Objects.hash(nome);
    }

    // post: rappresentazione leggibile per debugging; non usata nella view
    @Override
    public String toString() {
        return "Categoria{nome='" + nome + "', campiSpecifici=" + campiSpecifici.size() + "}";
    }
}

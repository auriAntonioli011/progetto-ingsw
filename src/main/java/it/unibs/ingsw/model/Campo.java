package it.unibs.ingsw.model;

import java.util.Objects;

/**
 * Descrive un campo di una Categoria: nome, tipo di dato e obbligatorietà.
 *
 * Vincolo di unicità: il nome è unico all'interno della Categoria (o configurazione)
 * che contiene questo Campo. La responsabilità di garantire tale unicità appartiene
 * al contenitore, non a questa classe.
 */
public class Campo {

    private final String nome;
    private final TipoCampo tipo;
    private final boolean obbligatorio;

    // pre:  nome != null && !nome.isBlank() && tipo != null
    // post: this.nome == nome && this.tipo == tipo && this.obbligatorio == obbligatorio
    // inv:  nome non è mai null né blank; tipo non è mai null
    public Campo(String nome, TipoCampo tipo, boolean obbligatorio) {
        if (nome == null || nome.isBlank())
            throw new IllegalArgumentException("nome non può essere null o vuoto");
        if (tipo == null)
            throw new IllegalArgumentException("tipo non può essere null");
        this.nome = nome;
        this.tipo = tipo;
        this.obbligatorio = obbligatorio;
    }

    // post: restituisce il nome del campo; non null né blank
    public String getNome() {
        return nome;
    }

    // post: restituisce il tipo di dato del campo; non null
    public TipoCampo getTipo() {
        return tipo;
    }

    // post: restituisce true se il campo è obbligatorio nella proposta
    public boolean isObbligatorio() {
        return obbligatorio;
    }

    // Due campi sono uguali se e solo se hanno lo stesso nome (case-sensitive).
    // Questo permette di rilevare duplicati in Set e Map usati dal contenitore.
    // post: restituisce true se o è un Campo con lo stesso nome
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Campo altro)) return false;
        return nome.equals(altro.nome);
    }

    // inv:  consistente con equals: due campi uguali hanno lo stesso hashCode
    @Override
    public int hashCode() {
        return Objects.hash(nome);
    }

    // post: rappresentazione leggibile per debugging; non usata nella view
    @Override
    public String toString() {
        return "Campo{nome='" + nome + "', tipo=" + tipo + ", obbligatorio=" + obbligatorio + "}";
    }
}

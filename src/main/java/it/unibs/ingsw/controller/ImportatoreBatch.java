package it.unibs.ingsw.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import it.unibs.ingsw.model.Campo;
import it.unibs.ingsw.model.Categoria;
import it.unibs.ingsw.model.Proposta;
import it.unibs.ingsw.model.StatoProposta;
import it.unibs.ingsw.model.TipoCampo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V5 — Import batch degli ingressi del back-end: categorie (con campi specifici)
 * e nuove proposte da un file JSON, in alternativa alla modalità interattiva
 * (che resta operativa e invariata).
 *
 * Formato del file: un oggetto JSON con due blocchi entrambi opzionali, che
 * riusano lo stesso schema dei file di persistenza (nomi campo identici a
 * categorie.json; per le proposte, la coppia "categoria" per nome + "valori"
 * con le stesse chiavi di proposte.json, es. "Termine ultimo di iscrizione"):
 *
 *   {
 *     "categorie": [ { "nome": "...",
 *                      "campiSpecifici": [ { "nome": "...", "tipo": "...",
 *                                            "obbligatorio": true/false } ] } ],
 *     "proposte":  [ { "categoria": "...", "valori": { "Titolo": "...", ... } } ]
 *   }
 *
 * Ordine di elaborazione: prima TUTTE le categorie, poi TUTTE le proposte —
 * così una proposta può referenziare una categoria definita nello stesso file.
 *
 * Politica continua-e-riporta: nessun elemento malformato o non valido deve
 * abortire l'import degli altri. Ogni elemento è isolato in un proprio blocco
 * try; gli scarti sono contati e motivati negli avvisi dell'EsitoImport.
 * Se invece l'intero file è inesistente o sintatticamente malformato, l'import
 * termina senza alcuna scrittura.
 *
 * Zero duplicazione di logica: la classe non tocca mai direttamente archivi o
 * regole di dominio — categorie e campi passano da creaCategoria /
 * aggiungiCampoSpecifico, le proposte da creaProposta (che invoca valida() di
 * V2) e richiediPubblicazione. Persistenza e vincoli sono quindi per
 * costruzione gli stessi della modalità interattiva.
 */
public class ImportatoreBatch {

    private static final String BLOCCO_CATEGORIE = "categorie";
    private static final String BLOCCO_PROPOSTE = "proposte";
    private static final String CHIAVE_NOME = "nome";
    private static final String CHIAVE_CAMPI_SPECIFICI = "campiSpecifici";
    private static final String CHIAVE_TIPO = "tipo";
    private static final String CHIAVE_OBBLIGATORIO = "obbligatorio";
    private static final String CHIAVE_CATEGORIA = "categoria";
    private static final String CHIAVE_VALORI = "valori";

    /**
     * Riepilogo di un'esecuzione dell'import: contatori per esito e lista di
     * avvisi leggibili (uno per ogni elemento scartato o parzialmente importato).
     */
    public record EsitoImport(int categorieImportate,
                              int categorieScartate,
                              int proposteImportate,
                              int proposteScartate,
                              List<String> avvisi) {
        public EsitoImport {
            if (avvisi == null) throw new IllegalArgumentException("avvisi non può essere null");
            avvisi = List.copyOf(avvisi);
        }
    }

    private final ControllerConfiguratore controller;

    // pre:  controller != null
    // post: this.controller == controller
    public ImportatoreBatch(ControllerConfiguratore controller) {
        if (controller == null)
            throw new IllegalArgumentException("controller non può essere null");
        this.controller = controller;
    }

    // pre:  percorsoFile != null
    // post: se il file non esiste o non è JSON ben formato, restituisce un esito
    //       a contatori zero con l'errore negli avvisi e NESSUNA scrittura sugli
    //       archivi. Altrimenti importa prima le categorie poi le proposte del
    //       file, applicando la politica continua-e-riporta; ogni elemento
    //       importato è già persistito (tramite i metodi del controller) quando
    //       il metodo ritorna. Le proposte importate valide risultano APERTA
    //       (pubblicate in bacheca).
    public EsitoImport importa(Path percorsoFile) {
        if (percorsoFile == null)
            throw new IllegalArgumentException("percorsoFile non può essere null");

        List<String> avvisi = new ArrayList<>();

        if (Files.notExists(percorsoFile)) {
            avvisi.add("file inesistente: " + percorsoFile);
            return new EsitoImport(0, 0, 0, 0, avvisi);
        }

        JsonObject radice;
        try (Reader reader = Files.newBufferedReader(percorsoFile, StandardCharsets.UTF_8)) {
            JsonElement contenuto = JsonParser.parseReader(reader);
            if (!contenuto.isJsonObject()) {
                avvisi.add("file malformato: atteso un oggetto JSON con blocchi '"
                        + BLOCCO_CATEGORIE + "' e/o '" + BLOCCO_PROPOSTE + "'");
                return new EsitoImport(0, 0, 0, 0, avvisi);
            }
            radice = contenuto.getAsJsonObject();
        } catch (IOException | JsonParseException e) {
            avvisi.add("file malformato o non leggibile: " + e.getMessage());
            return new EsitoImport(0, 0, 0, 0, avvisi);
        }

        int categorieImportate = 0;
        int categorieScartate = 0;
        for (JsonElement el : leggiArray(radice, BLOCCO_CATEGORIE, avvisi)) {
            if (importaCategoria(el, avvisi)) categorieImportate++;
            else categorieScartate++;
        }

        int proposteImportate = 0;
        int proposteScartate = 0;
        int indice = 0;
        for (JsonElement el : leggiArray(radice, BLOCCO_PROPOSTE, avvisi)) {
            indice++;
            if (importaProposta(el, indice, avvisi)) proposteImportate++;
            else proposteScartate++;
        }

        return new EsitoImport(categorieImportate, categorieScartate,
                proposteImportate, proposteScartate, avvisi);
    }

    // --- import di una singola categoria (con i suoi campi specifici) ---

    // post: restituisce true se la categoria è stata creata (eventualmente con
    //       alcuni campi saltati — la categoria resta comunque creata), false se
    //       l'intero elemento è stato scartato (nome mancante o già esistente,
    //       elemento malformato). Ogni scarto produce un avviso.
    private boolean importaCategoria(JsonElement elemento, List<String> avvisi) {
        try {
            if (!elemento.isJsonObject()) {
                avvisi.add("categoria scartata: elemento malformato (atteso oggetto JSON)");
                return false;
            }
            JsonObject obj = elemento.getAsJsonObject();
            String nome = leggiStringa(obj, CHIAVE_NOME);
            if (nome == null || nome.isBlank()) {
                avvisi.add("categoria scartata: campo 'nome' mancante o vuoto");
                return false;
            }
            boolean giaEsistente = controller.getCategorie().stream()
                    .anyMatch(c -> c.getNome().equals(nome));
            if (giaEsistente) {
                avvisi.add("categoria '" + nome + "' già esistente: saltata (nessuna modifica a quella presente)");
                return false;
            }

            controller.creaCategoria(nome);

            if (obj.has(CHIAVE_CAMPI_SPECIFICI)) {
                JsonElement campi = obj.get(CHIAVE_CAMPI_SPECIFICI);
                if (campi.isJsonArray()) {
                    for (JsonElement elCampo : campi.getAsJsonArray()) {
                        importaCampoSpecifico(nome, elCampo, avvisi);
                    }
                } else {
                    avvisi.add("categoria '" + nome + "': '" + CHIAVE_CAMPI_SPECIFICI
                            + "' non è una lista — campi specifici ignorati");
                }
            }
            return true;
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            avvisi.add("categoria scartata: " + e.getMessage());
            return false;
        }
    }

    // post: aggiunge il campo alla categoria via controller; se il campo è
    //       malformato, di tipo non riconosciuto o duplicato (rispetto a campi
    //       base, comuni o specifici già presenti) viene saltato con avviso,
    //       senza toccare la categoria già creata.
    private void importaCampoSpecifico(String nomeCategoria, JsonElement elemento, List<String> avvisi) {
        try {
            if (!elemento.isJsonObject()) {
                avvisi.add("categoria '" + nomeCategoria + "': campo malformato saltato (atteso oggetto JSON)");
                return;
            }
            JsonObject obj = elemento.getAsJsonObject();
            String nome = leggiStringa(obj, CHIAVE_NOME);
            if (nome == null || nome.isBlank()) {
                avvisi.add("categoria '" + nomeCategoria + "': campo senza nome saltato");
                return;
            }
            String tipoTesto = leggiStringa(obj, CHIAVE_TIPO);
            TipoCampo tipo;
            try {
                tipo = TipoCampo.valueOf(tipoTesto == null ? "" : tipoTesto.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                avvisi.add("categoria '" + nomeCategoria + "': campo '" + nome
                        + "' saltato — tipo '" + tipoTesto + "' non riconosciuto");
                return;
            }
            boolean obbligatorio = false;
            if (obj.has(CHIAVE_OBBLIGATORIO) && obj.get(CHIAVE_OBBLIGATORIO).isJsonPrimitive()) {
                obbligatorio = obj.get(CHIAVE_OBBLIGATORIO).getAsBoolean();
            }

            controller.aggiungiCampoSpecifico(nomeCategoria, new Campo(nome, tipo, obbligatorio));
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            // Tipico: nome duplicato rispetto a campi base/comuni/specifici.
            avvisi.add("categoria '" + nomeCategoria + "': campo saltato — " + e.getMessage());
        }
    }

    // --- import di una singola proposta ---

    // post: restituisce true se la proposta è stata creata, validata e pubblicata
    //       (stato APERTA, persistita in bacheca); false se scartata — categoria
    //       inesistente, elemento malformato o vincoli di validità violati. In
    //       quest'ultimo caso l'avviso elenca i vincoli falliti (via
    //       descriviVincoliViolati, stesse regole di valida() V2).
    private boolean importaProposta(JsonElement elemento, int indice, List<String> avvisi) {
        try {
            if (!elemento.isJsonObject()) {
                avvisi.add("proposta #" + indice + " scartata: elemento malformato (atteso oggetto JSON)");
                return false;
            }
            JsonObject obj = elemento.getAsJsonObject();
            String nomeCategoria = leggiStringa(obj, CHIAVE_CATEGORIA);
            if (nomeCategoria == null || nomeCategoria.isBlank()) {
                avvisi.add("proposta #" + indice + " scartata: campo 'categoria' mancante o vuoto");
                return false;
            }
            Optional<Categoria> categoria = controller.getCategorie().stream()
                    .filter(c -> c.getNome().equals(nomeCategoria))
                    .findFirst();
            if (categoria.isEmpty()) {
                avvisi.add("proposta #" + indice + " scartata: categoria '"
                        + nomeCategoria + "' inesistente");
                return false;
            }

            Map<String, String> valori = new LinkedHashMap<>();
            if (obj.has(CHIAVE_VALORI) && obj.get(CHIAVE_VALORI).isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject(CHIAVE_VALORI).entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        valori.put(e.getKey(), e.getValue().getAsString());
                    } else {
                        avvisi.add("proposta #" + indice + ": valore non testuale per '"
                                + e.getKey() + "' ignorato");
                    }
                }
            }

            String descrizione = "#" + indice
                    + (valori.containsKey("Titolo") ? " ('" + valori.get("Titolo") + "')" : "")
                    + " [categoria '" + nomeCategoria + "']";

            Proposta proposta = controller.creaProposta(categoria.get(), valori);
            if (proposta.getStato() != StatoProposta.VALIDA) {
                avvisi.add("proposta " + descrizione + " scartata, vincoli violati: "
                        + String.join("; ", controller.descriviVincoliViolati(proposta)));
                return false;
            }
            // In batch non esiste una "sessione interattiva" in cui l'utente possa
            // decidere in un secondo momento se pubblicare: una proposta valida
            // viene pubblicata subito (stato APERTA, persistita in bacheca).
            controller.richiediPubblicazione(proposta);
            return true;
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            avvisi.add("proposta #" + indice + " scartata: " + e.getMessage());
            return false;
        }
    }

    // --- helper di lettura difensiva dell'albero JSON ---

    // post: restituisce il blocco array richiesto, o un array vuoto se il blocco
    //       è assente; se presente ma non è un array, avviso + array vuoto.
    private JsonArray leggiArray(JsonObject radice, String chiave, List<String> avvisi) {
        if (!radice.has(chiave)) return new JsonArray();
        JsonElement blocco = radice.get(chiave);
        if (!blocco.isJsonArray()) {
            avvisi.add("blocco '" + chiave + "' ignorato: atteso un array JSON");
            return new JsonArray();
        }
        return blocco.getAsJsonArray();
    }

    // post: restituisce il valore stringa della chiave, o null se assente o non primitivo
    private String leggiStringa(JsonObject obj, String chiave) {
        if (!obj.has(chiave) || !obj.get(chiave).isJsonPrimitive()) return null;
        return obj.get(chiave).getAsString();
    }
}

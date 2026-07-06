package it.unibs.ingsw.model;

import it.unibs.ingsw.util.FornitoreTempo;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Proposta di scambio riferita a una Categoria.
 *
 * I valori dei campi sono memorizzati come Map<String,String> (chiave = nome del Campo,
 * valore = rappresentazione testuale del valore inserito dall'utente). L'interpretazione
 * tipizzata (es. parsing di una data) avviene solo quando serve, guidata da Campo.getTipo(),
 * per evitare polimorfismo nella serializzazione JSON — coerente con la scelta V1 di
 * mantenere Gson semplice.
 *
 * Nomi convenzionali dei campi controllati dal ciclo di vita — CAMPO_TERMINE_ISCRIZIONE,
 * CAMPO_DATA_EVENTO, CAMPO_DATA_CONCLUSIVA, CAMPO_NUMERO_PARTECIPANTI — sono costanti
 * pubbliche: il configuratore deve definire campi base con questi nomi perché una proposta
 * possa risultare VALIDA e per applicare le transizioni V3.
 *
 * V3 — Osservatori e persistenza degli aderenti (scelta implementativa):
 * la lista di aderenti è persistita come lista di username (String), non come lista di
 * Fruitore. Motivi: (i) evita cicli di serializzazione (Fruitore ↔ SpazioPersonale ↔
 * Notifica ↔ Proposta), (ii) evita di duplicare gli oggetti Fruitore sul disco (una
 * volta in fruitori.json e una in proposte.json). Il collegamento oggetto→oggetto
 * necessario alla notifica Observer è mantenuto tramite la lista TRANSIENT osservatori,
 * ripopolata dopo il caricamento dal ControllerFruitore. Il metodo aderisci(...) aggiorna
 * entrambe le liste in un unico atto.
 */
public class Proposta {

    // Nomi convenzionali dei campi controllati da valida() e dal motore transizioni.
    // Devono coincidere case-sensitive con nomi di campi base di TipoCampo.DATA/INTERO.
    public static final String CAMPO_TERMINE_ISCRIZIONE = "Termine ultimo di iscrizione";
    public static final String CAMPO_DATA_EVENTO = "Data";
    public static final String CAMPO_DATA_CONCLUSIVA = "Data conclusiva";
    public static final String CAMPO_NUMERO_PARTECIPANTI = "Numero di partecipanti";

    /**
     * Voce dello storico degli stati: coppia (stato assunto, data assunzione).
     * Immutable value type: la lista storico() è append-only, non è previsto rollback.
     */
    public record VoceStorico(StatoProposta stato, LocalDate data) {
        public VoceStorico {
            if (stato == null) throw new IllegalArgumentException("stato non può essere null");
            if (data == null) throw new IllegalArgumentException("data non può essere null");
        }
    }

    // V3: identificatore stabile della proposta. Serve alle Notifica per legare
    // l'evento allo specifico oggetto anche quando due proposte hanno stessa categoria
    // e valori simili. Generato in costruttore; lazy-cementato in assicuraStrutture()
    // per gestire JSON legacy V2 (che non ha il campo) — al primo elaboraTransizione
    // successivo al caricamento l'id viene persistito.
    private String id;
    private final Categoria categoria;
    private final Map<String, String> valori;
    private StatoProposta stato;
    private LocalDate dataPubblicazione;
    // V3: usernames dei fruitori iscritti. Persistito. Vedi javadoc di classe.
    private List<String> aderenti;
    // V3: storico delle transizioni di stato (append-only).
    private List<VoceStorico> storico;
    // V3: osservatori Fruitore ripopolati dopo il caricamento; transient per non
    // duplicare Fruitore sul disco né creare cicli di serializzazione.
    private transient List<OsservatoreProposta> osservatori;

    // pre:  categoria != null && valori != null
    // post: this.categoria == categoria
    //       && this.valori è una copia difensiva di valori
    //       && this.stato == null (non ancora validata)
    //       && this.dataPubblicazione == null
    //       && aderenti, storico, osservatori inizializzati vuoti
    // inv:  categoria e valori non sono mai null; stato segue le transizioni ammesse in StatoProposta
    public Proposta(Categoria categoria, Map<String, String> valori) {
        if (categoria == null)
            throw new IllegalArgumentException("categoria non può essere null");
        if (valori == null)
            throw new IllegalArgumentException("valori non può essere null");
        this.id = UUID.randomUUID().toString();
        this.categoria = categoria;
        this.valori = new HashMap<>(valori);
        this.stato = null;
        this.dataPubblicazione = null;
        this.aderenti = new ArrayList<>();
        this.storico = new ArrayList<>();
        this.osservatori = new ArrayList<>();
    }

    // post: restituisce l'identificatore stabile della proposta; mai null
    //       (lazy-inizializzato per Proposta caricate da JSON V2 legacy senza id)
    public String getId() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        return id;
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

    // post: restituisce una vista immutabile della lista di username degli aderenti
    public List<String> getAderenti() {
        assicuraStrutture();
        return List.copyOf(aderenti);
    }

    // post: restituisce una vista immutabile dello storico delle transizioni
    public List<VoceStorico> getStorico() {
        assicuraStrutture();
        return List.copyOf(storico);
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
    //       && lo storico contiene una voce (APERTA, dataPubblicazione)
    // inv:  la transizione VALIDA → APERTA è irreversibile in V2
    public void marcaPubblicata(LocalDate dataPubblicazione) {
        if (stato != StatoProposta.VALIDA)
            throw new IllegalStateException(
                    "solo una proposta VALIDA può essere marcata come pubblicata");
        if (dataPubblicazione == null)
            throw new IllegalArgumentException("dataPubblicazione non può essere null");
        assicuraStrutture();
        this.stato = StatoProposta.APERTA;
        this.dataPubblicazione = dataPubblicazione;
        this.storico.add(new VoceStorico(StatoProposta.APERTA, dataPubblicazione));
    }

    // pre:  fruitore != null && fornitoreTempo != null
    // post: se tutti i vincoli sono rispettati — (i) stato == APERTA,
    //       (ii) l'username del fruitore non è già in aderenti,
    //       (iii) oggi <= termine iscrizione,
    //       (iv) aderenti.size() < "Numero di partecipanti" — allora
    //       l'username è aggiunto a aderenti, il fruitore è registrato come osservatore
    //       e restituisce true; altrimenti lancia IllegalStateException con motivo.
    // inv:  "solo se stesso" (il fruitore deve iscriversi in prima persona) è vincolo
    //       enforced al livello del controller: qui accettiamo il Fruitore passato
    //       come "il fruitore che sta chiamando".
    public void aderisci(Fruitore fruitore, FornitoreTempo fornitoreTempo) {
        if (fruitore == null)
            throw new IllegalArgumentException("fruitore non può essere null");
        if (fornitoreTempo == null)
            throw new IllegalArgumentException("fornitoreTempo non può essere null");
        assicuraStrutture();

        if (stato != StatoProposta.APERTA)
            throw new IllegalStateException(
                    "adesione consentita solo su proposte APERTA (stato attuale: " + stato + ")");

        String username = fruitore.getUsername();
        if (aderenti.contains(username))
            throw new IllegalStateException(
                    "il fruitore '" + username + "' è già iscritto a questa proposta");

        LocalDate termine = leggiData(CAMPO_TERMINE_ISCRIZIONE);
        if (termine == null)
            throw new IllegalStateException(
                    "campo '" + CAMPO_TERMINE_ISCRIZIONE + "' assente o non parsabile");
        if (fornitoreTempo.oggi().isAfter(termine))
            throw new IllegalStateException(
                    "termine di iscrizione superato (" + termine + ")");

        Integer numeroPartecipanti = leggiIntero(CAMPO_NUMERO_PARTECIPANTI);
        if (numeroPartecipanti == null)
            throw new IllegalStateException(
                    "campo '" + CAMPO_NUMERO_PARTECIPANTI + "' assente o non parsabile");
        if (aderenti.size() >= numeroPartecipanti)
            throw new IllegalStateException(
                    "numero massimo di aderenti raggiunto (" + numeroPartecipanti + ")");

        aderenti.add(username);
        registraOsservatore(fruitore);
    }

    // pre:  osservatore != null
    // post: osservatore è registrato tra gli osservatori (se non già presente);
    //       usato dopo il caricamento dal ControllerFruitore per rilegare i Fruitore
    //       ai loro username già presenti in aderenti.
    // inv:  osservatori è transient — non viene serializzato; va sempre ricostruito
    //       ad ogni avvio applicazione.
    public void registraOsservatore(OsservatoreProposta osservatore) {
        if (osservatore == null)
            throw new IllegalArgumentException("osservatore non può essere null");
        assicuraStrutture();
        if (!osservatori.contains(osservatore))
            osservatori.add(osservatore);
    }

    // pre:  fornitoreTempo != null
    // post: applica le regole del §3.2:
    //         - stato == APERTA e oggi > termineIscrizione:
    //             aderenti.size() == numeroPartecipanti → stato = CONFERMATA
    //             altrimenti                            → stato = ANNULLATA
    //         - stato == CONFERMATA e oggi > dataConclusiva → stato = CONCLUSA
    //       ogni transizione appende una VoceStorico(nuovoStato, oggi) e notifica
    //       tutti gli osservatori con OsservatoreProposta.aggiorna(this, notifica).
    //       Se lo stato non transita, non produce effetti collaterali.
    // inv:  ANNULLATA e CONCLUSA sono stati terminali (nessuna transizione in uscita).
    //       Se i campi data/intero necessari sono assenti o non parsabili la transizione
    //       viene saltata silenziosamente (fail-safe: preferiamo non modificare stato
    //       piuttosto che promuoverlo su input ambiguo).
    public void elaboraTransizione(FornitoreTempo fornitoreTempo) {
        if (fornitoreTempo == null)
            throw new IllegalArgumentException("fornitoreTempo non può essere null");
        assicuraStrutture();
        if (stato == null) return;

        LocalDate oggi = fornitoreTempo.oggi();

        if (stato == StatoProposta.APERTA) {
            LocalDate termine = leggiData(CAMPO_TERMINE_ISCRIZIONE);
            Integer numeroPartecipanti = leggiIntero(CAMPO_NUMERO_PARTECIPANTI);
            if (termine == null || numeroPartecipanti == null) return;
            if (!oggi.isAfter(termine)) return;

            StatoProposta nuovoStato = (aderenti.size() >= numeroPartecipanti)
                    ? StatoProposta.CONFERMATA
                    : StatoProposta.ANNULLATA;
            applicaTransizione(nuovoStato, oggi);
            return;
        }

        if (stato == StatoProposta.CONFERMATA) {
            LocalDate dataConclusiva = leggiData(CAMPO_DATA_CONCLUSIVA);
            if (dataConclusiva == null) return;
            if (!oggi.isAfter(dataConclusiva)) return;
            applicaTransizione(StatoProposta.CONCLUSA, oggi);
        }
    }

    private void applicaTransizione(StatoProposta nuovoStato, LocalDate oggi) {
        this.stato = nuovoStato;
        this.storico.add(new VoceStorico(nuovoStato, oggi));
        // snapshotValori: copia immutabile dei valori della proposta al momento della
        // transizione. È lo "spazio" da cui la view estrae il promemoria per CONFERMATA
        // (data-evento, ora, luogo, importo dovuto, ecc.) senza dover risalire alla
        // Proposta — evita che una modifica successiva alla proposta possa alterare
        // retroattivamente il promemoria già recapitato.
        Notifica notifica = new Notifica(
                getId(),
                categoria.getNome(),
                nuovoStato,
                oggi,
                Map.copyOf(valori)
        );
        for (OsservatoreProposta o : osservatori) {
            o.aggiorna(this, notifica);
        }
    }

    // Legge il valore grezzo di un campo data e lo interpreta come LocalDate ISO.
    // Restituisce null se la chiave non è presente, il valore è vuoto o il parsing fallisce:
    // la scelta è deliberata — valida() e elaboraTransizione() usano il null come segnale
    // di "vincolo non verificabile" e si comportano di conseguenza (rispettivamente:
    // ritornano false / non transitano).
    private LocalDate leggiData(String nomeCampo) {
        String v = valori.get(nomeCampo);
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Simmetrico a leggiData ma per campi INTERO.
    private Integer leggiIntero(String nomeCampo) {
        String v = valori.get(nomeCampo);
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Gson bypassa il costruttore e alloca i field a zero/null. Se una Proposta è
    // stata deserializzata da un file precedente (o mancano campi V3 in JSON legacy V2),
    // aderenti/storico/osservatori possono essere null: reinizializziamo prima di qualsiasi
    // accesso mutante o di lettura.
    private void assicuraStrutture() {
        if (aderenti == null) aderenti = new ArrayList<>();
        if (storico == null) storico = new ArrayList<>();
        if (osservatori == null) osservatori = new ArrayList<>();
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
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
                + ", valori=" + valori.size()
                + ", aderenti=" + (aderenti != null ? aderenti.size() : 0) + "}";
    }
}

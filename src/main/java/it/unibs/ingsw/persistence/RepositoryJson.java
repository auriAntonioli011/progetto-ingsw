package it.unibs.ingsw.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository generico: carica e salva una lista di oggetti di tipo T su un file JSON.
 * Ogni aggregato del dominio istanzia il proprio RepositoryJson con il path dedicato.
 */
public class RepositoryJson<T> {

    private final Path percorsoFile;
    private final Class<T> tipoElemento;
    private final Gson gson;

    // pre:  percorsoFile != null && tipoElemento != null
    // post: this.percorsoFile == percorsoFile && this.tipoElemento == tipoElemento
    // inv:  gson configurato con pretty printing e AdapterLocalDate per LocalDate
    public RepositoryJson(Path percorsoFile, Class<T> tipoElemento) {
        if (percorsoFile == null) throw new IllegalArgumentException("percorsoFile non può essere null");
        if (tipoElemento == null) throw new IllegalArgumentException("tipoElemento non può essere null");
        this.percorsoFile = percorsoFile;
        this.tipoElemento = tipoElemento;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDate.class, new AdapterLocalDate())
                .create();
    }

    // pre:  elementi != null
    // post: percorsoFile esiste e contiene la serializzazione JSON di elementi;
    //       le directory intermedie vengono create se non esistono
    public void salva(List<T> elementi) throws IOException {
        if (elementi == null) throw new IllegalArgumentException("elementi non può essere null");
        Path parent = percorsoFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(percorsoFile, StandardCharsets.UTF_8)) {
            gson.toJson(elementi, writer);
        }
    }

    // post: restituisce la lista de-serializzata da percorsoFile,
    //       oppure una lista vuota mutabile se il file non esiste o è vuoto
    public List<T> carica() throws IOException {
        if (Files.notExists(percorsoFile)) {
            return new ArrayList<>();
        }
        Type tipoLista = TypeToken.getParameterized(List.class, tipoElemento).getType();
        try (Reader reader = Files.newBufferedReader(percorsoFile, StandardCharsets.UTF_8)) {
            List<T> risultato = gson.fromJson(reader, tipoLista);
            return risultato != null ? risultato : new ArrayList<>();
        }
    }
}

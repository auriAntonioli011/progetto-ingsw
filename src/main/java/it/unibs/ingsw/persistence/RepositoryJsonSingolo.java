package it.unibs.ingsw.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository generico per un singolo oggetto di tipo T serializzato su file JSON.
 * Parallelo a RepositoryJson<T>, ma opera su un oggetto invece che su una lista.
 */
public class RepositoryJsonSingolo<T> {

    private final Path percorsoFile;
    private final Class<T> tipoElemento;
    private final Gson gson;

    // pre:  percorsoFile != null && tipoElemento != null
    // post: this.percorsoFile == percorsoFile && this.tipoElemento == tipoElemento
    // inv:  gson configurato con pretty printing e AdapterLocalDate per LocalDate
    public RepositoryJsonSingolo(Path percorsoFile, Class<T> tipoElemento) {
        if (percorsoFile == null) throw new IllegalArgumentException("percorsoFile non può essere null");
        if (tipoElemento == null) throw new IllegalArgumentException("tipoElemento non può essere null");
        this.percorsoFile = percorsoFile;
        this.tipoElemento = tipoElemento;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDate.class, new AdapterLocalDate())
                .create();
    }

    // pre:  elemento != null
    // post: percorsoFile esiste e contiene la serializzazione JSON di elemento;
    //       le directory intermedie vengono create se non esistono
    public void salva(T elemento) throws IOException {
        if (elemento == null) throw new IllegalArgumentException("elemento non può essere null");
        Path parent = percorsoFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(percorsoFile, StandardCharsets.UTF_8)) {
            gson.toJson(elemento, writer);
        }
    }

    // post: restituisce Optional.of(oggetto deserializzato) se percorsoFile esiste,
    //       Optional.empty() se il file non esiste ancora
    public Optional<T> carica() throws IOException {
        if (Files.notExists(percorsoFile)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(percorsoFile, StandardCharsets.UTF_8)) {
            T risultato = gson.fromJson(reader, tipoElemento);
            return Optional.ofNullable(risultato);
        }
    }
}

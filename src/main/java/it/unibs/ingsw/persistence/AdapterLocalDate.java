package it.unibs.ingsw.persistence;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Adapter Gson per LocalDate: serializza/deserializza nel formato ISO-8601 (yyyy-MM-dd).
 * Da registrare su GsonBuilder con .registerTypeAdapter(LocalDate.class, new AdapterLocalDate()).
 */
public class AdapterLocalDate extends TypeAdapter<LocalDate> {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ISO_LOCAL_DATE;

    // pre:  writer != null
    // post: scrive il valore come stringa ISO-8601 oppure null JSON se value == null
    @Override
    public void write(JsonWriter writer, LocalDate value) throws IOException {
        if (value == null) {
            writer.nullValue();
            return;
        }
        writer.value(value.format(FORMATO));
    }

    // pre:  reader != null
    // post: restituisce il LocalDate corrispondente alla stringa ISO-8601 letta,
    //       oppure null se il token è null JSON
    // inv:  il formato atteso è yyyy-MM-dd; lancia DateTimeParseException su formato errato
    @Override
    public LocalDate read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return LocalDate.parse(reader.nextString(), FORMATO);
    }
}

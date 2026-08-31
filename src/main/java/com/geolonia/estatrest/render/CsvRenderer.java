package com.geolonia.estatrest.render;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Renders query rows as RFC 4180 CSV with a header row, UTF-8 encoded. */
@Component
public class CsvRenderer {

    private static final String LINE_SEPARATOR = "\r\n";

    /**
     * @param columns header, in order: the dataset's property columns followed by the geometry column
     * @param rows    one map per feature, keyed exactly like {@code columns}
     */
    public byte[] render(List<String> columns, List<Map<String, Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeRow(writer, columns);
            for (Map<String, Object> row : rows) {
                writeRow(writer, columns.stream().map(c -> format(row.get(c))).toList());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render CSV", e);
        }
        return out.toByteArray();
    }

    private void writeRow(Writer writer, List<String> fields) throws Exception {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                writer.write(",");
            }
            writer.write(escape(fields.get(i)));
        }
        writer.write(LINE_SEPARATOR);
    }

    private String format(Object value) {
        return value == null ? "" : value.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

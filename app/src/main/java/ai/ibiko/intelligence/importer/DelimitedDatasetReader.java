package ai.ibiko.intelligence.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Streaming RFC-4180-style delimited reader for CSV/TSV. Handles quoted separators and escaped quotes, both doubled ("") and backslashed (\\"). */
public final class DelimitedDatasetReader {
    /** The longest one record may be. */
    static final int MOST_RECORD = 1 << 20;

    public interface RowConsumer { void accept(List<String> header, List<String> row, long rowNumber) throws IOException; }
    private DelimitedDatasetReader() {}

    public static long read(Reader source, char delimiter, RowConsumer consumer) throws IOException {
        if (source == null || consumer == null) throw new IllegalArgumentException("source and consumer are required");
        BufferedReader in = source instanceof BufferedReader ? (BufferedReader) source : new BufferedReader(source);
        List<String> header = null;
        StringBuilder record = new StringBuilder();
        boolean quoted = false;
        long rows = 0;
        int ch;
        while ((ch = in.read()) != -1) {
            if (record.length() > MOST_RECORD) {
                // an unclosed quote made the rest of a large file one record, held in memory until it ran out
                throw new IOException("A record is longer than " + (MOST_RECORD / 1024) + " KB: the file may have an unclosed quote");
            }
            char c = (char) ch;
            if (c == '\\' && quoted) {
                // a backslash-escaped quote (\") inside a quoted field, as many CSV exports write it
                in.mark(1);
                int next = in.read();
                record.append(c);
                if (next == '"') record.append('"');
                else if (next != -1) in.reset();
            } else if (c == '"') {
                record.append(c);
                if (quoted) {
                    in.mark(1);
                    int next = in.read();
                    if (next == '"') record.append('"');
                    else { quoted = false; if (next != -1) in.reset(); }
                } else quoted = true;
            } else if ((c == '\n' || c == '\r') && !quoted) {
                if (c == '\r') { in.mark(1); int next = in.read(); if (next != '\n' && next != -1) in.reset(); }
                List<String> fields = parseRecord(record.toString(), delimiter);
                record.setLength(0);
                if (header == null) header = normalizeHeader(fields);
                else if (!isBlank(fields)) { rows++; consumer.accept(header, fields, rows); }
            } else record.append(c);
        }
        if (quoted) throw new IOException("Unclosed quoted field in dataset");
        if (record.length() > 0) {
            List<String> fields = parseRecord(record.toString(), delimiter);
            if (header == null) header = normalizeHeader(fields);
            else if (!isBlank(fields)) { rows++; consumer.accept(header, fields, rows); }
        }
        if (header == null || header.isEmpty()) throw new IOException("Dataset is empty or has no header");
        return rows;
    }

    public static List<String> parseRecord(String line, char delimiter) throws IOException {
        List<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
            else if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (quoted) quoted = false;
                else if (field.length() == 0) quoted = true;
                else throw new IOException("Unexpected quote in unquoted field");
            } else if (c == delimiter && !quoted) { fields.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        if (quoted) throw new IOException("Unclosed quoted field");
        fields.add(field.toString());
        return fields;
    }

    private static List<String> normalizeHeader(List<String> fields) {
        List<String> out = new ArrayList<String>();
        for (String f : fields) out.add(f.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        return Collections.unmodifiableList(out);
    }
    private static boolean isBlank(List<String> fields) { for (String f : fields) if (!f.trim().isEmpty()) return false; return true; }
}

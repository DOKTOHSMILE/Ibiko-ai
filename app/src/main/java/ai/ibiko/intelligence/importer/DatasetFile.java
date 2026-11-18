package ai.ibiko.intelligence.importer;

import java.util.Locale;

/**
 * What a dataset file the person picked is, read from its name and first line: CSV or TSV, a word list (lemma,
 * language, definition) or relations (subject, predicate, object); and the licence they chose, as the policy's
 * decision. Pure Java.
 */
public final class DatasetFile {
    public enum Kind { WORDS, RELATIONS, UNKNOWN }

    /** The licence choices offered, in order, and what each lets the dataset be used for. */
    public static final String[] LICENCES = {
            "Open: MIT, Apache 2.0, CC BY, CC0 or public domain",
            "Non-commercial only (CC BY-NC and similar)",
            "Evaluation or testing only",
            "I don't know"};
    /** The short name kept with every entry, in the order of {@link #LICENCES}. */
    public static final String[] LICENCE_NAMES = {"open", "non-commercial", "evaluation-only", "unknown"};

    private DatasetFile() {
    }

    /** Tab for a .tsv file or a first line with tabs; a comma otherwise. */
    public static char delimiter(String fileName, String firstLine) {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".tsv") || n.endsWith(".tab") || firstLine != null && firstLine.indexOf('\t') >= 0) {
            return '\t';
        }
        return ',';
    }

    /** What the columns hold, from the header line. */
    public static Kind kind(String header, char delimiter) {
        if (header == null) {
            return Kind.UNKNOWN;
        }
        boolean subject = false, predicate = false, object = false, lemma = false, language = false;
        for (String raw : header.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)))) {
            String h = raw.replace("﻿", "").replace("\"", "").trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            subject |= h.equals("subject") || h.equals("head");
            predicate |= h.equals("predicate") || h.equals("relation") || h.equals("type");
            object |= h.equals("object") || h.equals("tail") || h.equals("target");
            lemma |= h.equals("lemma") || h.equals("word") || h.equals("term");
            language |= h.equals("language") || h.equals("lang") || h.equals("language_code");
        }
        if (subject && predicate && object) {
            return Kind.RELATIONS;
        }
        return lemma && language ? Kind.WORDS : Kind.UNKNOWN;
    }

    /** The policy for the licence chosen ({@link #LICENCES} index) and whether the person checked the file. */
    public static DatasetImportPolicy policy(int licence, boolean checked) {
        DatasetImportPolicy.Decision d = licence == 0 ? DatasetImportPolicy.Decision.APPROVED
                : licence == 1 ? DatasetImportPolicy.Decision.NON_COMMERCIAL_ONLY
                : licence == 2 ? DatasetImportPolicy.Decision.EVALUATION_ONLY
                : DatasetImportPolicy.Decision.UNKNOWN;
        return new DatasetImportPolicy(d, checked, checked);
    }
}

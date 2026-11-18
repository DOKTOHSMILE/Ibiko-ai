package ai.ibiko.intelligence.importer;

import ai.ibiko.intelligence.StructuredKnowledgeDatabase;
import android.database.sqlite.SQLiteDatabase;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

/** Imports reviewed lexical TSV/CSV rows into the on-device retrieval store. No network or licence acceptance. */
public final class KnowledgeDatasetImporter {
    public static final class Metadata {
        public final String name, version, sourceUrl, licence, checksum;
        public final DatasetImportPolicy policy;
        public Metadata(String name, String version, String sourceUrl, String licence, String checksum, DatasetImportPolicy policy) {
            this.name = name; this.version = version; this.sourceUrl = sourceUrl; this.licence = licence;
            this.checksum = checksum; this.policy = policy;
        }
    }
    public static final class Report {
        public final long rowsRead, lexemesInserted, skipped;
        Report(long rowsRead, long lexemesInserted, long skipped) { this.rowsRead = rowsRead; this.lexemesInserted = lexemesInserted; this.skipped = skipped; }
    }

    private final StructuredKnowledgeDatabase database;
    public KnowledgeDatasetImporter(StructuredKnowledgeDatabase database) {
        if (database == null) throw new IllegalArgumentException("database is required");
        this.database = database;
    }

    /** Expected header aliases: lemma/word, language/lang, definition/meaning, pos/part_of_speech. */
    public Report importLexicon(Reader reader, char delimiter, Metadata metadata, boolean commercialProduct) throws IOException {
        if (metadata == null || metadata.policy == null) throw new IllegalArgumentException("review metadata is required");
        if (!metadata.policy.allows(DatasetImportPolicy.Purpose.RETRIEVAL, commercialProduct))
            throw new SecurityException(metadata.policy.explanation(DatasetImportPolicy.Purpose.RETRIEVAL, commercialProduct));
        if (empty(metadata.name) || empty(metadata.sourceUrl) || empty(metadata.licence))
            throw new IllegalArgumentException("dataset name, source URL and licence are required");

        final SQLiteDatabase db = database.getWritableDatabase();
        final long[] inserted = {0}, skipped = {0};
        db.beginTransaction();
        try {
            long read = DelimitedDatasetReader.read(reader, delimiter, new DelimitedDatasetReader.RowConsumer() {
                @Override public void accept(List<String> header, List<String> row, long rowNumber) throws IOException {
                    String lemma = value(header, row, "lemma", "word", "term");
                    String language = value(header, row, "language", "lang", "language_code");
                    String definition = value(header, row, "definition", "meaning", "gloss");
                    String pos = value(header, row, "pos", "part_of_speech", "category");
                    if (empty(lemma) || empty(language)) { skipped[0]++; return; }
                    database.putLexeme(language, lemma, pos, definition, metadata.name, metadata.licence, 0.65);
                    inserted[0]++;
                }
            });
            database.registerDataset(metadata.name, metadata.version, metadata.sourceUrl, metadata.licence,
                    "RETRIEVAL_APPROVED_AFTER_HUMAN_REVIEW", System.currentTimeMillis(), metadata.checksum);
            db.setTransactionSuccessful();
            return new Report(read, inserted[0], skipped[0]);
        } finally { db.endTransaction(); }
    }

    /** Imports reviewed graph triples. Expected columns: subject, predicate/relation, object, optional language. */
    public Report importRelations(Reader reader, char delimiter, Metadata metadata, boolean commercialProduct,
            boolean humanReviewed) throws IOException {
        if (!humanReviewed) throw new SecurityException("relationship datasets require explicit human review before graph import");
        if (metadata == null || metadata.policy == null) throw new IllegalArgumentException("review metadata is required");
        if (!metadata.policy.allows(DatasetImportPolicy.Purpose.RETRIEVAL, commercialProduct))
            throw new SecurityException(metadata.policy.explanation(DatasetImportPolicy.Purpose.RETRIEVAL, commercialProduct));
        if (empty(metadata.name) || empty(metadata.sourceUrl) || empty(metadata.licence))
            throw new IllegalArgumentException("dataset name, source URL and licence are required");
        final SQLiteDatabase db = database.getWritableDatabase();
        final long[] inserted = {0}, skipped = {0};
        db.beginTransaction();
        try {
            long read = DelimitedDatasetReader.read(reader, delimiter, new DelimitedDatasetReader.RowConsumer() {
                @Override public void accept(List<String> header, List<String> row, long rowNumber) throws IOException {
                    String subject = value(header, row, "subject", "source", "head");
                    String predicate = value(header, row, "predicate", "relation", "type");
                    String object = value(header, row, "object", "target", "tail");
                    String language = value(header, row, "language", "lang", "language_code");
                    if (empty(subject) || empty(predicate) || empty(object)) { skipped[0]++; return; }
                    database.putReviewedSemanticEdge(subject, predicate, object, language, metadata.name,
                            metadata.licence, 0.70, true);
                    inserted[0]++;
                }
            });
            database.registerDataset(metadata.name, metadata.version, metadata.sourceUrl, metadata.licence,
                    "RETRIEVAL_APPROVED_AFTER_HUMAN_REVIEW", System.currentTimeMillis(), metadata.checksum);
            db.setTransactionSuccessful();
            return new Report(read, inserted[0], skipped[0]);
        } finally { db.endTransaction(); }
    }

    private static String value(List<String> header, List<String> row, String... names) {
        for (String name : names) {
            int i = header.indexOf(name);
            if (i >= 0 && i < row.size()) return row.get(i).trim();
        }
        return "";
    }
    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
}

package ai.ibiko.intelligence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Small on-device, provenance-aware lexical and relationship store. No network access. */
public final class StructuredKnowledgeDatabase extends SQLiteOpenHelper {
    private static final String DB = "ibiko_knowledge.db";
    private static final int VERSION = 2;
    public StructuredKnowledgeDatabase(Context context) { super(context.getApplicationContext(), DB, null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE lexeme (id INTEGER PRIMARY KEY, language TEXT NOT NULL, lemma TEXT NOT NULL, pos TEXT, definition TEXT, source TEXT NOT NULL, licence TEXT, confidence REAL NOT NULL DEFAULT 0.5, UNIQUE(language,lemma,source))");
        db.execSQL("CREATE INDEX lexeme_lookup ON lexeme(language,lemma)");
        db.execSQL("CREATE TABLE relation (id INTEGER PRIMARY KEY, subject TEXT NOT NULL, predicate TEXT NOT NULL, object TEXT NOT NULL, language TEXT, source TEXT NOT NULL, licence TEXT, confidence REAL NOT NULL DEFAULT 0.5)");
        db.execSQL("CREATE INDEX relation_subject ON relation(subject,predicate)");
        db.execSQL("CREATE INDEX relation_object ON relation(object,predicate)");
        db.execSQL("CREATE TABLE dataset_registry (id INTEGER PRIMARY KEY, dataset TEXT NOT NULL UNIQUE, version TEXT, source_url TEXT NOT NULL, licence TEXT, commercial_use TEXT NOT NULL, imported_at INTEGER NOT NULL, checksum TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS relation_object ON relation(object,predicate)");
        // Never drop user knowledge during an upgrade.
    }

    public long putLexeme(String language, String lemma, String pos, String definition, String source, String licence, double confidence) {
        if (empty(language) || empty(lemma) || empty(source)) throw new IllegalArgumentException("language, lemma and source are required");
        ContentValues v = new ContentValues(); v.put("language", language); v.put("lemma", lemma.trim().toLowerCase(java.util.Locale.ROOT));
        v.put("pos", pos); v.put("definition", definition); v.put("source", source); v.put("licence", licence); v.put("confidence", clamp(confidence));
        return getWritableDatabase().insertWithOnConflict("lexeme", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public long putRelation(String subject, String predicate, String object, String language, String source, String licence, double confidence) {
        if (empty(subject) || empty(predicate) || empty(object) || empty(source)) throw new IllegalArgumentException("relationship fields and source are required");
        ContentValues v = new ContentValues(); v.put("subject", subject); v.put("predicate", predicate); v.put("object", object);
        v.put("language", language); v.put("source", source); v.put("licence", licence); v.put("confidence", clamp(confidence));
        return getWritableDatabase().insert("relation", null, v);
    }
    public Cursor findLexeme(String language, String lemma) {
        return getReadableDatabase().query("lexeme", new String[]{"language","lemma","pos","definition","source","licence","confidence"},
                "language=? AND lemma=?", new String[]{language, lemma.toLowerCase(java.util.Locale.ROOT)}, null, null, "confidence DESC", "20");
    }
    /** A word in any language: a dataset may name its languages by code ("yo") or by name ("Yoruba"). */
    public Cursor findLexemeAnyLanguage(String lemma) {
        return getReadableDatabase().query("lexeme", new String[]{"language","lemma","pos","definition","source","licence","confidence"},
                "lemma=?", new String[]{lemma.toLowerCase(java.util.Locale.ROOT)}, null, null, "confidence DESC", "6");
    }

    /** {words, relations, datasets} kept now. */
    public long[] counts() {
        SQLiteDatabase db = getReadableDatabase();
        return new long[]{android.database.DatabaseUtils.queryNumEntries(db, "lexeme"),
                android.database.DatabaseUtils.queryNumEntries(db, "relation"),
                android.database.DatabaseUtils.queryNumEntries(db, "dataset_registry")};
    }

    /** The datasets imported: "name (licence)", newest first. */
    public java.util.List<String> datasets() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        Cursor c = getReadableDatabase().query("dataset_registry", new String[]{"dataset", "licence"}, null, null, null, null,
                "imported_at DESC", "50");
        try {
            while (c.moveToNext()) {
                out.add(c.getString(0) + (c.getString(1) == null ? "" : " (" + c.getString(1) + ")"));
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Removes everything imported (the person's own Ibiko 1 memory is elsewhere and stays). */
    public void clearImported() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("lexeme", null, null);
        db.delete("relation", null, null);
        db.delete("dataset_registry", null, null);
    }

    /** Persist a reviewed semantic edge; heuristic candidates must not be stored as verified facts. */
    public long putReviewedSemanticEdge(String subject, String predicate, String object, String language,
            String source, String licence, double confidence, boolean humanReviewed) {
        if (!humanReviewed) throw new IllegalArgumentException("semantic edges require human review before persistence");
        return putRelation(subject, predicate, object, language, source, licence, confidence);
    }

    /** Find graph edges by exact predicate, with a hard result bound. */
    public Cursor findRelationsByPredicate(String predicate, int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return getReadableDatabase().query("relation",
                new String[]{"subject","predicate","object","language","source","licence","confidence"},
                "predicate=?", new String[]{predicate}, null, null, "confidence DESC", String.valueOf(safeLimit));
    }

    public Cursor findRelations(String subject) {
        return getReadableDatabase().query("relation", new String[]{"subject","predicate","object","language","source","licence","confidence"},
                "subject=?", new String[]{subject}, null, null, "confidence DESC", "50");
    }
    /** Record a dataset only after a human has reviewed its licence and import provenance. */
    public long registerDataset(String name, String version, String sourceUrl, String licence, String commercialUse, long importedAt, String checksum) {
        if (empty(name) || empty(sourceUrl) || empty(commercialUse)) throw new IllegalArgumentException("dataset, source URL and commercial-use decision are required");
        ContentValues v = new ContentValues(); v.put("dataset", name); v.put("version", version); v.put("source_url", sourceUrl);
        v.put("licence", licence); v.put("commercial_use", commercialUse); v.put("imported_at", importedAt); v.put("checksum", checksum);
        return getWritableDatabase().insertWithOnConflict("dataset_registry", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private static double clamp(double d) { return Double.isNaN(d) ? 0.5 : Math.max(0, Math.min(1, d)); }
}

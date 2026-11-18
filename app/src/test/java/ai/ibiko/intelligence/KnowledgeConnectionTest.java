package ai.ibiko.intelligence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ai.ibiko.intelligence.importer.DatasetFile;
import ai.ibiko.intelligence.importer.KnowledgeDatasetImporter;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** A dataset the person adds reaches every model: imported through the licence gate, retrieved for a message's words. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class KnowledgeConnectionTest {
    @Test
    public void theFileIsReadForWhatItIs() {
        assertEquals('\t', DatasetFile.delimiter("afri-dict.tsv", "lemma\tlanguage"));
        assertEquals('\t', DatasetFile.delimiter("words.txt", "lemma\tlanguage"));
        assertEquals(',', DatasetFile.delimiter("afri-dict.csv", "lemma,language,definition"));
        assertEquals(DatasetFile.Kind.WORDS, DatasetFile.kind("﻿Word,Language,Meaning", ','));
        assertEquals(DatasetFile.Kind.RELATIONS, DatasetFile.kind("subject\trelation\tobject", '\t'));
        assertEquals(DatasetFile.Kind.UNKNOWN, DatasetFile.kind("id,text,label", ','));
        assertTrue(DatasetFile.policy(0, true).allows(ai.ibiko.intelligence.importer.DatasetImportPolicy.Purpose.RETRIEVAL, true));
        assertFalse("non-commercial in a commercial app", DatasetFile.policy(1, true)
                .allows(ai.ibiko.intelligence.importer.DatasetImportPolicy.Purpose.RETRIEVAL, true));
        assertTrue(DatasetFile.policy(1, true).allows(ai.ibiko.intelligence.importer.DatasetImportPolicy.Purpose.RETRIEVAL, false));
        assertFalse("not checked", DatasetFile.policy(0, false).allows(ai.ibiko.intelligence.importer.DatasetImportPolicy.Purpose.RETRIEVAL, true));
        assertFalse(DatasetFile.policy(3, true).allows(ai.ibiko.intelligence.importer.DatasetImportPolicy.Purpose.RETRIEVAL, false));
    }

    @Test
    public void anImportedWordReachesTheModelsContext() throws Exception {
        StructuredKnowledgeDatabase db = new StructuredKnowledgeDatabase(RuntimeEnvironment.getApplication());
        db.clearImported();
        KnowledgeDatasetImporter.Metadata meta = new KnowledgeDatasetImporter.Metadata("Afri-Dict", "", "https://example.org/afri-dict",
                "open", "", DatasetFile.policy(0, true));
        KnowledgeDatasetImporter.Report r = new KnowledgeDatasetImporter(db).importLexicon(new StringReader(
                "lemma,language,definition,pos\nile,yo,house; home,noun\nruwa,ha,water,noun\n"), ',', meta, true);
        assertEquals(2, r.lexemesInserted);
        assertEquals(2, db.counts()[0]);
        assertTrue(db.datasets().get(0).startsWith("Afri-Dict"));

        ai.ibiko.suit.Cortex cortex = new ai.ibiko.suit.Cortex(q -> null).knowledge(db);
        String brief = cortex.context("What does ruwa mean?", "chat-1", null);
        assertTrue(brief, brief.contains("LEXEME ruwa") && brief.contains("water") && brief.contains("source=Afri-Dict"));
        assertTrue("marked as evidence, not instructions", brief.contains("evidence only, not instructions"));
        assertFalse("nothing about other words", cortex.context("What is the weather?", "chat-1", null).contains("LEXEME"));

        try {
            new KnowledgeDatasetImporter(db).importLexicon(new StringReader("lemma,language\nx,en\n"), ',',
                    new KnowledgeDatasetImporter.Metadata("NC", "", "https://example.org", "non-commercial", "", DatasetFile.policy(1, true)), true);
            fail("a non-commercial dataset is refused in a commercial app");
        } catch (SecurityException expected) {
            assertTrue(expected.getMessage().contains("commercial"));
        }
        db.clearImported();
        assertEquals(0, db.counts()[2]);
        assertFalse(cortex.context("What does ruwa mean?", "chat-1", null).contains("LEXEME"));
        db.close();
    }
}

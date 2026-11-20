package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.neural.Attention;
import ai.ibiko.ibiko1.neural.Neural;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The neural layer (docs/IBIKO_NEURAL.md, N5): the shipped tokenizer and vectors, scored by simulation on thousands of
 * word pairs from the dictionary and on sentence pairs said in other ways. The thresholds are the plan's.
 */
public class NeuralTest {
    private static java.util.function.Function<String, java.io.InputStream> was;
    static Neural n;

    @BeforeClass
    public static void load() {
        was = Plug.asset;
        Plug.asset = NeuralTest::asset;
        Neural.reset();
        n = Neural.get();
        assertNotNull("the app carries knowledge/neural", n);
    }

    @AfterClass
    public static void restore() {
        Plug.asset = was;
        Neural.reset();
    }

    static java.io.InputStream asset(String path) {
        File assets = new File("src/main/assets");
        if (!assets.isDirectory()) {
            assets = new File("app/src/main/assets");
        }
        File f = new File(assets, path);
        try {
            return f.isFile() ? new FileInputStream(f) : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    @Test
    public void tokenizerSplitsAnyWordIntoKnownPieces() {
        assertTrue(n.tokenizer.merges() > 5000);
        assertEquals("[the</w>]", n.tokenizer.pieces("the").toString());
        // a made-up word still comes out as pieces, the last one ending the word
        List<String> p = n.tokenizer.pieces("unlockableness");
        assertTrue(p.toString(), p.size() > 1 && p.get(p.size() - 1).endsWith("</w>"));
        assertEquals("unlockableness</w>", String.join("", p));
        assertTrue(n.tokenizer.tokens("Ibiko reads anything.").size() >= 3);
    }

    @Test
    public void wordsUsedAlikeSitTogether() {
        assertTrue(n.embeddings.vocabulary() > 30000);
        assertTrue(n.embeddings.similarity("doctor", "physician") > n.embeddings.similarity("doctor", "guitar"));
        assertTrue(n.embeddings.similarity("river", "lake") > n.embeddings.similarity("river", "election"));
        assertTrue(n.embeddings.similarity("happy", "glad") > n.embeddings.similarity("happy", "tractor"));
        assertTrue(n.near("king", 10).toString(), n.near("king", 10).stream().anyMatch(
                w -> w.equals("queen") || w.equals("prince") || w.equals("monarch") || w.equals("throne") || w.equals("emperor")));
        // a word never seen still gets a vector from its pieces, near its stem
        assertTrue(n.embeddings.similarity("footballish", "football") > 0.3);
    }

    /** Synonym pairs from the dictionary against random pairs: how often the synonyms score higher (AUC). */
    @Test
    public void synonymsBeatRandomPairsInThousandsOfSimulations() throws Exception {
        List<String[]> syn = new ArrayList<String[]>();
        List<String> vocab = new ArrayList<String>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                new FileInputStream(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv")), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] c = line.split("\t", -1);
                if (c.length < 4 || !n.embeddings.knows(c[0])) {
                    continue;
                }
                vocab.add(c[0]);
                for (String s : c[3].split(",")) {
                    s = s.trim();
                    if (!s.isEmpty() && !s.equals(c[0]) && n.embeddings.knows(s) && n.embeddings.rank(s) < 20000
                            && n.embeddings.rank(c[0]) < 20000) {
                        syn.add(new String[] {c[0], s});
                    }
                }
            }
        }
        Random rnd = new Random(7);
        int trials = 5000, wins = 0;
        double ties = 0;
        for (int t = 0; t < trials; t++) {
            String[] p = syn.get(rnd.nextInt(syn.size()));
            String other = vocab.get(rnd.nextInt(vocab.size()));
            double a = n.embeddings.similarity(p[0], p[1]), b = n.embeddings.similarity(p[0], other);
            if (a > b) {
                wins++;
            } else if (a == b) {
                ties += 0.5;
            }
        }
        double auc = (wins + ties) / trials;
        System.out.println("synonym AUC " + auc + " over " + trials + " simulations (" + syn.size() + " pairs)");
        assertTrue("synonym AUC " + auc, auc > 0.80);
    }

    /** The same thing said another way scores above something else said in the same words' neighbourhood. */
    @Test
    public void paraphrasesScoreAboveUnrelatedSentences() {
        String[][] same = {
            {"How old are you?", "What is your age?"},
            {"I am very hungry.", "I want to eat something."},
            {"The car broke down on the road.", "My vehicle stopped working on the highway."},
            {"She is a doctor at the hospital.", "She works as a physician in a clinic."},
            {"Where do you live?", "Which city is your home?"},
            {"The weather is cold today.", "It is freezing outside this morning."},
            {"He won the football match.", "His team won the soccer game."},
            {"I bought a new phone.", "I purchased a new mobile."},
            {"The children are playing in the garden.", "The kids play outside in the yard."},
            {"What is the capital of Nigeria?", "Which city is Nigeria's capital?"},
            {"I feel sad today.", "I am unhappy this day."},
            {"Can you help me with my homework?", "Could you assist me with my school work?"},
        };
        int ok = 0;
        Attention a = n.attention;
        for (int i = 0; i < same.length; i++) {
            double s = a.similarity(same[i][0], same[i][1]);
            double worst = -1;
            for (int j = 0; j < same.length; j++) {
                if (j != i) {
                    worst = Math.max(worst, a.similarity(same[i][0], same[j][1]));
                }
            }
            if (s > worst) {
                ok++;
            } else {
                System.out.println("paraphrase missed: " + same[i][0] + " | " + same[i][1] + " " + s + " vs " + worst);
            }
        }
        System.out.println("paraphrases matched " + ok + "/" + same.length);
        assertTrue(ok + "/" + same.length, ok >= same.length * 3 / 4);
    }

    /** Attention moves "bank" towards water beside "river" and towards money beside "loan". */
    @Test
    public void attentionGivesAWordItsSenseInContext() {
        float[] byRiver = n.attention.read("we sat on the bank of the river fishing").of("bank");
        float[] byMoney = n.attention.read("the bank approved my loan and deposit").of("bank");
        float[] water = n.embeddings.vector("water"), money = n.embeddings.vector("money");
        assertTrue(dot(byRiver, water) - dot(byRiver, money) > dot(byMoney, water) - dot(byMoney, money));
    }

    @Test
    public void encodesASentenceInUnderTwoMilliseconds() {
        String s = "The quick brown fox jumps over the lazy dog near the river bank.";
        for (int i = 0; i < 200; i++) {
            n.attention.encode(s + i);
        }
        long t = System.nanoTime();
        int runs = 1000;
        for (int i = 0; i < runs; i++) {
            n.attention.encode(s);
        }
        double ms = (System.nanoTime() - t) / 1e6 / runs;
        System.out.println("encode " + ms + " ms");
        assertTrue(ms + " ms", ms < 2);
    }

    /** The layer in chat: similarity, close words, the odd one out, and sentences said another way. */
    @Test
    public void answersInChat() {
        ai.ibiko.ibiko1.words.Meanings m = new ai.ibiko.ibiko1.words.Meanings(
                ai.ibiko.ibiko1.words.MeaningIndex.of(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv")));
        WordTalk talk = new WordTalk(m);
        String s = talk.reply("How similar are doctor and physician?");
        assertTrue(s, s.contains("close") && s.contains("%"));
        s = talk.reply("how similar are doctor and guitar");
        assertTrue(s, s.contains("not really related") || s.contains("loosely"));
        s = talk.reply("words close to river");
        assertTrue(s, s.contains("lake") || s.contains("tributary") || s.contains("stream"));
        s = talk.reply("Which is the odd one out: apple, banana, mango, tractor?");
        assertTrue(s, s.contains("“tractor”"));
        s = talk.reply("odd one out: lagos, nairobi, accra, football");
        assertTrue(s, s.contains("“football”"));
        assertTrue(WordTalk.asks("what's the similarity between cat and dog"));
        // a sentence said in other words, matched through the word vectors where the dictionary has no link
        assertEquals(ai.ibiko.ibiko1.words.Meanings.Verdict.SAME,
                m.compare("The car broke down on the highway", "The vehicle broke down on the road").verdict);
        // and opposites stay opposites, though their vectors sit close
        assertEquals(ai.ibiko.ibiko1.words.Meanings.Verdict.OPPOSITE, m.compare("the car is fast", "the car is slow").verdict);
    }

    /** A question worded unlike the pasted text still finds the sentence that answers it. */
    @Test
    public void aPassageAnswersAQuestionWordedAnotherWay() {
        Passage p = Passage.of("Chika Obi founded Greenleaf Farms in Enugu in 2015. The company grows cassava and yams on "
                + "two hundred hectares. Last year the farm earned four million naira from exports to Ghana. Heavy rain "
                + "flooded the northern fields in August, and the harvest fell by a third. Chika plans to buy tractors "
                + "and hire twenty more workers next spring.", 0);
        assertNotNull(p);
        for (String q : new String[] {"How much did the farm earn by selling abroad?", "What happened with the storms and the crops?",
                "Which machines will be purchased?"}) {
            for (String x : p.sentences) {
                System.out.println(String.format("%.3f %.3f %.3f", n.similarity(q, x), n.attention.alignment(q, x), n.attention.match(q, x)) + "  " + q + " | " + x);
            }
        }
        String s = p.closest("How much did the farm earn by selling abroad?");
        assertTrue(s, s != null && s.contains("four million"));
        s = p.closest("What happened with the storms and the crops?");
        assertTrue(s, s != null && s.contains("flooded"));
        s = p.closest("Which machines will be purchased?");
        assertTrue(s, s != null && s.contains("tractors"));
    }

    /**
     * The reverse dictionary by vectors: a definition, read as a sentence, must sit closer to its own word than to a
     * random one ("a large natural stream of water" → "river"), over thousands of senses.
     */
    @Test
    public void definitionsFindTheirWordInThousandsOfSimulations() throws Exception {
        List<String[]> senses = new ArrayList<String[]>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                new FileInputStream(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv")), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] c = line.split("\t", -1);
                if (c.length > 2 && c[0].matches("[a-z]+") && n.embeddings.rank(c[0]) < 20000 && c[2].split(" ").length >= 4
                        && !c[2].toLowerCase().contains(c[0])) {
                    senses.add(new String[] {c[0], c[2]});
                }
            }
        }
        Random rnd = new Random(11);
        int trials = 3000, wins = 0;
        for (int t = 0; t < trials; t++) {
            String[] s = senses.get(rnd.nextInt(senses.size()));
            String other = senses.get(rnd.nextInt(senses.size()))[0];
            if (n.attention.alignment(s[1], s[0]) > n.attention.alignment(s[1], other)) {
                wins++;
            }
        }
        double auc = wins / (double) trials;
        System.out.println("definition → word AUC " + auc + " over " + trials + " simulations (" + senses.size() + " senses)");
        assertTrue("definition AUC " + auc, auc > 0.75);
    }

    /** The reverse dictionary with and without the vectors' re-ranking: the vectors must not make it worse. */
    @Test
    public void reverseDictionaryWithVectors() {
        String[][] asks = {
            {"someone who flies planes", "pilot"}, {"a person who repairs cars", "mechanic"},
            {"a place where books are kept", "library"}, {"a doctor who treats teeth", "dentist"},
            {"a large natural stream of water", "river"}, {"a young dog", "puppy"}, {"the fear of heights", "acrophobia"},
            {"someone who writes poems", "poet"}, {"a building where films are shown", "cinema"},
            {"a place where you buy medicine", "pharmacy"}, {"very happy", "ecstatic"}, {"a person who bakes bread", "baker"},
            {"frozen water", "ice"}, {"a story about someone's life written by someone else", "biography"},
            {"someone who studies the stars", "astronomer"}, {"the day after today", "tomorrow"},
            {"an animal that eats only plants", "herbivore"}, {"a person who cannot sleep", "insomniac"},
            {"a room where food is cooked", "kitchen"}, {"a word that means the opposite", "antonym"},
        };
        ai.ibiko.ibiko1.words.Meanings m = new ai.ibiko.ibiko1.words.Meanings(
                ai.ibiko.ibiko1.words.MeaningIndex.of(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv")));
        int with = 0, without = 0;
        for (String[] a : asks) {
            with += top3(m, a) ? 1 : 0;
        }
        java.util.function.Function<String, java.io.InputStream> plugged = Plug.asset;
        Plug.asset = p -> null;
        Neural.reset();
        try {
            for (String[] a : asks) {
                without += top3(m, a) ? 1 : 0;
            }
        } finally {
            Plug.asset = plugged;
            Neural.reset();
            n = Neural.get();
        }
        System.out.println("reverse dictionary top-3: with vectors " + with + "/" + asks.length + ", without " + without);
        assertTrue(with + " vs " + without, with >= without);
    }

    private static boolean top3(ai.ibiko.ibiko1.words.Meanings m, String[] a) {
        for (ai.ibiko.ibiko1.words.Meanings.Found f : m.wordsFor(a[0], 3)) {
            if (f.word.equals(a[1])) {
                return true;
            }
        }
        System.out.println("  reverse missed " + a[0] + " → " + a[1] + ": " + m.wordsFor(a[0], 3));
        return false;
    }

    /**
     * The gate that lets memory and the truth checks use the vectors: the same question said another way passes; a
     * question about a different thing that merely sounds alike never does (a wrong match would be a wrong fact).
     */
    @Test
    public void theTruthGateMatchesParaphrasesAndNeverLookAlikes() {
        ai.ibiko.ibiko1.words.Meanings m = new ai.ibiko.ibiko1.words.Meanings(
                ai.ibiko.ibiko1.words.MeaningIndex.of(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv")));
        String[][] same = {
            {"What is the capital of Kenya?", "Which city is the capital of Kenya?"},
            {"Where can I buy a car?", "Where can I purchase a car?"},
            {"How big is the ocean?", "How large is the ocean?"},
            {"Who started the company?", "Who founded the company?"},
            {"What does a physician do?", "What does a doctor do?"},
            {"When did the war end?", "When did the war finish?"},
            {"How fast can a cheetah run?", "How quickly can a cheetah run?"},
            {"Is the film good?", "Is the movie good?"},
        };
        String[][] different = {
            {"What is the capital of Kenya?", "What is the capital of Uganda?"},
            {"What colour is the car?", "What colour is the bus?"},
            {"Is the shop open on Monday?", "Is the shop open on Tuesday?"},
            {"Did prices rise in 2020?", "Did prices fall in 2020?"},
            {"Did prices rise in 2020?", "Did prices rise in 2021?"},
            {"Is the water hot?", "Is the water cold?"},
            {"Is the car red?", "Is the car blue?"},
            {"Is the bridge safe?", "Is the bridge not safe?"},
            {"Who wrote Hamlet?", "Who wrote Macbeth?"},
            {"How tall is Everest?", "How tall is Kilimanjaro?"},
            {"What is the capital of Kenya?", "What is the population of Kenya?"},
            {"Who is the president of Nigeria?", "Who is the president of Ghana?"},
        };
        int ok = 0;
        for (String[] p : same) {
            if (m.sameAsked(p[0], p[1])) {
                ok++;
            } else {
                System.out.println("gate missed paraphrase: " + p[0] + " | " + p[1]);
            }
        }
        for (String[] p : different) {
            assertTrue("look-alike passed the gate: " + p[0] + " | " + p[1], !m.sameAsked(p[0], p[1]));
        }
        System.out.println("truth gate: paraphrases " + ok + "/" + same.length + ", look-alikes 0/" + different.length);
        assertTrue(ok + "/" + same.length, ok >= same.length * 3 / 4);
    }

    /** Memory, the truth ledger and Reflection all know a reworded question, and none mistakes a look-alike for it. */
    @Test
    public void theBrainKnowsARewordedQuestionAndNotALookAlike() throws Exception {
        new ai.ibiko.ibiko1.words.Meanings(
                ai.ibiko.ibiko1.words.MeaningIndex.of(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv"))); // the shared one
        File dir = java.nio.file.Files.createTempDirectory("brain").toFile();
        Knowledge k = new Knowledge(new File(dir, "knowledge.txt"));
        k.confirm("Who founded Greenleaf Farms?", "Chika Obi founded Greenleaf Farms in 2015.");
        Knowledge.Fact f = k.recall("Who started Greenleaf Farms?");
        assertNotNull("a reworded question finds the confirmed answer", f);
        assertTrue(f.answer.contains("Chika Obi"));
        k.confirm("What is the capital of Kenya?", "Nairobi.");
        Knowledge.Fact other = k.recall("What is the capital of Uganda?");
        assertTrue("never the answer about another country", other == null || !other.answer.contains("Nairobi"));
        k.reject("Is the movie good?", "Yes.");
        assertTrue("a rejected answer stays rejected when reworded", k.rejected("Is the film good?", "Yes."));

        ai.ibiko.ibiko1.formulas.TruthLedger ledger = new ai.ibiko.ibiko1.formulas.TruthLedger(new File(dir, "ledger.txt"));
        ledger.record(java.util.Collections.singletonList(new ai.ibiko.ibiko1.formulas.Truth.Checked(
                ai.ibiko.ibiko1.formulas.Truth.Verdict.FALSE, "Lagos is the capital of Nigeria.", "Abuja is.", "a source")),
                "test", 1L);
        assertEquals(ai.ibiko.ibiko1.formulas.Truth.Verdict.FALSE,
                ledger.check("Lagos is the capital city of Nigeria.").verdict);
        assertEquals("a different claim borrows no verdict", ai.ibiko.ibiko1.formulas.Truth.Verdict.UNKNOWN,
                ledger.check("Abuja is the capital of Nigeria.").verdict);

        assertEquals(ai.ibiko.ibiko1.formulas.Reflection.Outcome.RETRIED,
                ai.ibiko.ibiko1.formulas.Reflection.outcome("Who founded Greenleaf Farms?", "Who started Greenleaf Farms?"));
        assertTrue(ai.ibiko.ibiko1.formulas.Reflection.outcome("What is the capital of Kenya?", "What is the capital of Uganda?")
                != ai.ibiko.ibiko1.formulas.Reflection.Outcome.RETRIED);
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }
}

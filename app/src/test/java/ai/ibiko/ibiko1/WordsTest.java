package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.words.GrammarCheck;
import ai.ibiko.ibiko1.words.InnerVoice;
import ai.ibiko.ibiko1.words.MeaningIndex;
import ai.ibiko.ibiko1.words.Meanings;
import ai.ibiko.ibiko1.words.Morph;
import ai.ibiko.ibiko1.words.Morphology;
import ai.ibiko.ibiko1.words.Words;
import org.junit.Test;

/** The words engine against the shipped dictionary: meanings both ways, in context, morphology, grammar, the inner voice. */
public class WordsTest {
    private static final MeaningIndex INDEX = MeaningIndex.of(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv"));
    private static final Meanings M = new Meanings(INDEX);
    private static final WordTalk TALK = new WordTalk(M);

    @Test
    public void meaningsBothWays() {
        assertTrue(INDEX.size() > 190_000);
        assertEquals("pilot", M.wordsFor("someone who flies planes", 3).get(0).word);
        assertEquals("mechanic", M.wordsFor("a person who repairs cars", 3).get(0).word);
        assertEquals("library", M.wordsFor("a place where books are kept", 3).get(0).word);
        assertTrue(TALK.reply("What's a word for someone who flies planes?").startsWith("The word is “pilot”"));
    }

    @Test
    public void aWordsMeaningInContext() {
        assertTrue(M.meant("bank", "I put my money in the bank", null).definition.contains("financial institution"));
        assertTrue(M.meant("bank", "we sat on the bank of the river", null).definition.contains("sloping land"));
        assertTrue(TALK.reply("What does bank mean in \"we sat on the bank of the river\"?").startsWith("Here, “bank” means"));
        assertEquals("repair", M.plain(M.meant("fixed", "The engineer fixed the server", "verb"), "fixed"));
    }

    @Test
    public void synonymsOppositesAndTheSameMeaningWordedAnotherWay() {
        assertTrue(M.synonyms("big", null).contains("large"));
        assertTrue("small finds big, which lists it", M.opposites("small").contains("big"));
        assertTrue(M.opposites("happy").contains("unhappy"));
        assertEquals(Meanings.Verdict.SAME, M.compare("the car is fast", "the automobile is quick").verdict);
        assertEquals(Meanings.Verdict.OPPOSITE, M.compare("the car is fast", "the car is slow").verdict);
        assertEquals(Meanings.Verdict.DIFFERENT, M.compare("the car is fast", "she sings in a choir").verdict);
        assertEquals("only everyday words, only usual senses", "The large dog ran rapidly", M.read("The large dog ran quickly").inOtherWords);
    }

    @Test
    public void aSentenceReadWordByWord() {
        Meanings.Reading r = M.read("The quick engineer fixed the broken server.");
        assertEquals("The quick engineer", r.subject);
        assertEquals("fixed", r.verb);
        assertEquals("the broken server", r.object);
        assertTrue(r.words.size() >= 4);
    }

    @Test
    public void wordsAreBuiltOneAffixAtATime() {
        Morphology morph = new Morphology(M);
        assertEquals("[[un- [happy]Adj]Adj -ness]N", morph.readings("unhappiness").get(0).brackets());
        assertEquals("[[de- [[[center]N -al]Adj -ize]V]V -ation]N", morph.readings("decentralization").get(0).brackets());
        java.util.List<Morphology.Build> two = morph.readings("unlockable");
        assertEquals(2, two.size());
        assertEquals("[[un- [lock]V]V -able]Adj", two.get(0).brackets());
        assertEquals("[un- [[lock]V -able]Adj]Adj", two.get(1).brackets());
        assertEquals("able to be unlocked", two.get(0).meaning());
        assertEquals("not able to be locked", two.get(1).meaning());
        assertTrue(morph.formation("blackboard").contains("compound"));
        assertTrue(morph.formation("brunch").contains("blend"));
        assertTrue(morph.formation("small-small").contains("reduplication"));
        assertTrue(morph.formation("CPU").contains("initialism"));
    }

    @Test
    public void inflectionsAndHowTheirEndingsSound() {
        assertEquals("buses", Morph.plural("bus"));
        assertEquals("children", Morph.plural("child"));
        assertEquals("stopped", Morph.past("stop"));
        assertEquals("went", Morph.past("go"));
        assertEquals("making", Morph.ing("make"));
        assertEquals("happier", Morph.comparative("happy"));
        assertEquals("/s/", Morph.pluralSound("cat"));
        assertEquals("/z/", Morph.pluralSound("dog"));
        assertEquals("/ɪz/", Morph.pluralSound("bus"));
        assertEquals("/t/", Morph.edSound("walk"));
        assertEquals("/d/", Morph.edSound("play"));
        assertEquals("/ɪd/", Morph.edSound("wait"));
        assertEquals("fly", Words.key("flies"));
        assertEquals(Words.key("planes"), Words.key("plane"));
    }

    @Test
    public void grammarRulesAndTheInnerVoice() {
        assertFalse(GrammarCheck.judge("Deployed the engineer node the").grammatical());
        assertTrue(GrammarCheck.judge("The engineer deployed the node.").grammatical());
        GrammarCheck.Judgement j = GrammarCheck.judge("he go to a office every day");
        assertEquals("He goes to an office every day.", j.corrected);
        assertTrue("a double negative is noted, not wrong", GrammarCheck.judge("I don't have no money.").grammatical());
        assertTrue(GrammarCheck.an("hour") && !GrammarCheck.an("university") && GrammarCheck.an("apple") && !GrammarCheck.an("CPU"));
        InnerVoice.Said said = InnerVoice.confirm("I has a apple. he go home.");
        assertEquals("I have an apple. he goes home.", said.text);
        assertTrue(said.step().startsWith("Read it back before saying it"));
        assertNull("a right sentence goes as it is", InnerVoice.confirm("The Mona Lisa was painted by Leonardo da Vinci.").step());
        assertEquals("quoted words are never touched", "He said “a apple” twice.", InnerVoice.confirm("He said “a apple” twice.").text);
        assertEquals("code is never touched", "```\nI has a apple\n```", InnerVoice.confirm("```\nI has a apple\n```").text);
    }

    @Test
    public void itIsAskedInTheChat() {
        assertTrue(WordTalk.asks("What is the plural of bus?"));
        assertTrue(TALK.reply("What is the plural of bus?").contains("“buses”"));
        assertTrue(TALK.reply("Break down the word unlockable").contains("two meanings"));
        assertTrue(TALK.reply("Is this sentence correct: he go to a office").contains("“he go” → “he goes”"));
        assertTrue(TALK.reply("Opposite of small").contains("big"));
        assertTrue(TALK.reply("Do \"the car is fast\" and \"the automobile is quick\" mean the same?").startsWith("Yes"));
        assertFalse(WordTalk.asks("What is the capital of Ghana?"));
    }
}

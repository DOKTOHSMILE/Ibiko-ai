package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Similar models are picked from a server list; Think 2 asks them all and the strongest judges. */
public class ModelRingTest {
    @Test
    public void aFlagshipGetsOtherFlagshipsAndAMiniGetsMinis() {
        List<String> offered = Arrays.asList("gpt-5", "gpt-5-mini", "claude-opus-5-5", "claude-haiku-5-5",
                "gemini-2.5-pro", "gemini-2.5-flash");
        List<String> large = ModelRing.choose("gpt-5", offered);
        assertEquals("gpt-5", large.get(0));
        assertEquals(3, large.size());
        assertTrue(large.contains("claude-opus-5-5"));
        assertTrue(large.contains("gemini-2.5-pro"));
        assertTrue(!large.contains("gpt-5-mini") && !large.contains("claude-haiku-5-5"));

        List<String> small = ModelRing.choose("gpt-5-mini", offered);
        assertEquals("gpt-5-mini", small.get(0));
        assertTrue(small.contains("claude-haiku-5-5"));
        assertTrue(!small.contains("claude-opus-5-5") && !small.contains("gemini-2.5-pro"));
    }

    @Test
    public void everyModelThinksAndTheStrongestThatAnsweredJudges() {
        List<ThinkTwo.Thinker> thinkers = Arrays.asList(new ThinkTwo.Thinker(0, "gpt-5-mini"),
                new ThinkTwo.Thinker(1, "claude-opus-5-5"), new ThinkTwo.Thinker(2, "gemini-2.5-flash"),
                new ThinkTwo.Thinker(0, "gemini-2.5-pro"));
        final List<String> notesAsked = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        final String[] judgedBy = {null}, judgeSaw = {null};
        String answer = ThinkTwo.run((t, system, prompt) -> {
            if (system.contains("You are the final judge")) {
                judgedBy[0] = t.model;
                judgeSaw[0] = prompt;
                return "The one answer.";
            }
            notesAsked.add(t.model);
            return t.model.equals("gemini-2.5-pro") ? null : "Notes from " + t.model; // one thinker fails
        }, thinkers, "The person says: how should a small shop take payments?", "", "You are Ibiko.", () -> false);
        assertEquals("The one answer.", answer);
        assertEquals("every model thinks", 4, notesAsked.size());
        assertEquals("the strongest that answered judges", "claude-opus-5-5", judgedBy[0]);
        assertTrue(judgeSaw[0].contains("Notes from gpt-5-mini") && judgeSaw[0].contains("Notes from gemini-2.5-flash"));
        assertFalse("no model names reach the judge's notes headings", judgeSaw[0].contains("Notes (claude"));
    }

    @Test
    public void aJudgeThatFailsHandsOverToTheNextStrongest() {
        List<ThinkTwo.Thinker> thinkers = Arrays.asList(new ThinkTwo.Thinker(0, "claude-sonnet-4-5"),
                new ThinkTwo.Thinker(1, "claude-opus-4-1"));
        final List<String> judges = new java.util.ArrayList<String>();
        String answer = ThinkTwo.run((t, system, prompt) -> {
            if (system.contains("You are the final judge")) {
                judges.add(t.model);
                return t.model.contains("opus") ? "" : "From the next.";
            }
            return "Notes.";
        }, thinkers, "The person says: plan a launch", "", "", () -> false);
        assertEquals("From the next.", answer);
        assertEquals(Arrays.asList("claude-opus-4-1", "claude-sonnet-4-5"), judges);
    }

    @Test
    public void continueThinksAboutThePlanNotTheWord() {
        String s = ThinkTwo.subject("continue", true, "Build the bakery's website", "Step 3 of 6: the menu page is done.");
        assertTrue(s.contains("Build the bakery's website"));
        assertTrue(s.contains("Step 3 of 6"));
        assertTrue(s.contains("not about the word"));
        assertEquals("The person says: hi there friend", ThinkTwo.subject("hi there friend", false, "x", "y"));
        assertTrue(ThinkTwo.worth("How should a small shop take payments?"));
        assertFalse(ThinkTwo.worth("hi"));
    }

    @Test
    public void strengthRanksSizeThenReasoningThenVersion() {
        assertTrue(ThinkTwo.strength("claude-opus-4-1") > ThinkTwo.strength("claude-sonnet-4-5"));
        assertTrue(ThinkTwo.strength("gpt-5") > ThinkTwo.strength("gpt-5-mini"));
        assertTrue(ThinkTwo.strength("gemini-2.5-pro") > ThinkTwo.strength("gemini-2.5-flash"));
        assertTrue(ThinkTwo.strength("deepseek-reasoner") > ThinkTwo.strength("deepseek-chat"));
        assertTrue(ThinkTwo.strength("llama-3.3-70b") > ThinkTwo.strength("llama-3.1-8b"));
    }
}

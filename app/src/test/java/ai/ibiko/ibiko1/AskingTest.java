package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Asking;

/** Thinking before looking: what a question asks for, about what and when, and holding the research to it. */
public class AskingTest {
    @Test
    public void aQuestionIsReadForWhatItWants() {
        Asking.Frame f = Asking.of("who won the 2027 World Cup?");
        assertEquals(Asking.Wants.WHO, f.wants);
        assertEquals("world cup", f.about);
        assertEquals(2027, f.year);
        assertTrue(f.winner && f.past);
        assertTrue("asked in 2026, it can't have happened", f.notYet(2026));
        assertTrue(!f.notYet(2027));
        assertTrue(!Asking.of("When is the 2027 World Cup?").notYet(2026)); // asking ahead is fine
        assertEquals("africa cup of nations", Asking.of("Who won the 2023 Africa Cup of Nations?").about);
    }

    @Test
    public void aNameItNearlyIsIsReadAsThatName() {
        Set<String> titles = new HashSet<String>(Arrays.asList("world cup", "premier league"));
        String[] r = Asking.repair("who won the 2027 world up?", p -> titles.contains(p.toLowerCase()));
        assertEquals("who won the 2027 World Cup?", r[0]);
        assertEquals("Read “world up” as “World Cup”", r[1]);
        assertNull("nothing near a name: unchanged", Asking.repair("who won the 2027 race?", p -> titles.contains(p))[1]);
        assertNull("already a name", Asking.repair("who won the world cup?", p -> titles.contains(p.toLowerCase()))[1]);
    }

    @Test
    public void researchIsHeldToTheQuestion() {
        Asking.Frame f = Asking.of("who won the 2022 World Cup?");
        assertTrue(f.holds("Argentina won the 2022 FIFA World Cup, beating France on penalties."));
        assertTrue("another year", !f.holds("France won the 2018 World Cup."));
        assertTrue("not a winner", !f.holds("The 2022 World Cup was held in Qatar."));
        assertTrue("another competition", !f.holds("The 2022 BWF World Tour was won by Viktor Axelsen."));
        // the screenshot: three different championships, none of them the World Cup, none saying who won
        String research = "Web results for who won the 2027 world cup\\n[1] 2027 BWF World Tour — https://en.wikipedia.org/wiki/x\\n"
                + "The 2027 BWF World Tour is the tenth season of the BWF World Tour of badminton, a circuit of 36 tournaments.\\n"
                + "[2] 2027 IIHF — https://en.wikipedia.org/wiki/y\\nThe 2027 IIHF World Championship will be hosted by Düsseldorf,"
                + " Gelsenkirchen and Mannheim, Germany, from 13 to 30 May 2027.\\n[3] Motocross — https://en.wikipedia.org/wiki/z\\n"
                + "The 2027 FIM Motocross World Championship will be the 71st FIM Motocross World Championship season.";
        assertNull("nothing answers: no stitched-together answer", ResearchAnswer.from("who won the 2027 World Cup?", research.replace("\\n", "\n")));
        String good = "Web results\\n[1] Final — https://en.wikipedia.org/wiki/f\\nArgentina won the 2022 FIFA World Cup, beating France"
                + " 4–2 on penalties after a 3–3 draw in the final.\\nThe 2022 World Cup was held in Qatar from 20 November to 18 December.";
        ResearchAnswer r = ResearchAnswer.from("who won the 2022 World Cup?", good.replace("\\n", "\n"));
        assertTrue(String.valueOf(r == null ? null : r.text), r != null && r.text.startsWith("Argentina won the 2022 FIFA World Cup")
                && !r.text.contains("Qatar"));
    }

    @Test
    public void notYetIsSaidPlainly() {
        String said = Ibiko1.notYet(Asking.of("who won the 2027 World Cup?"), "who won the 2027 World Cup?", 2026);
        assertTrue(said, said.startsWith("That hasn't happened yet: the 2027 World Cup is still to come (it's 2026 now)"));
        assertTrue(said.contains("no winner"));
    }

    @Test
    public void everyKindOfQuestionIsRead() {
        Object[][] kinds = {
            {"Who founded Microsoft?", Asking.Wants.WHO}, {"Whose phone is this?", Asking.Wants.WHOSE},
            {"What is a zorblat?", Asking.Wants.WHAT}, {"Which is longer, the Nile or the Niger?", Asking.Wants.WHICH},
            {"When did Nigeria become independent?", Asking.Wants.WHEN}, {"What year did the war end?", Asking.Wants.WHEN},
            {"Where is Ikeja?", Asking.Wants.WHERE}, {"Why is the sky blue?", Asking.Wants.WHY},
            {"What caused the Nigerian Civil War?", Asking.Wants.WHY}, {"How is jollof rice made?", Asking.Wants.HOW},
            {"How many states does Nigeria have?", Asking.Wants.HOW_MANY}, {"How much does a bag of rice cost?", Asking.Wants.HOW_MUCH},
            {"How long is the Niger River?", Asking.Wants.HOW_LONG}, {"How old is Wole Soyinka?", Asking.Wants.HOW_OLD},
            {"How tall is Kilimanjaro?", Asking.Wants.MEASURE}, {"Is Lagos in Nigeria?", Asking.Wants.YES_NO},
            {"Did Nigeria win the 1996 Olympic football?", Asking.Wants.YES_NO}, {"Can you tell me why bread rises?", Asking.Wants.WHY},
            {"Explain how a bill becomes law", Asking.Wants.HOW}};
        for (Object[] k : kinds) {
            assertEquals((String) k[0], k[1], Asking.of((String) k[0]).wants);
        }
    }

    @Test
    public void aSentenceMustGiveTheKindOfAnswerAsked() {
        assertTrue(Asking.of("Why is the sky blue?").answers("The sky is blue because air scatters blue light more than red."));
        assertTrue(!Asking.of("Why is the sky blue?").answers("The sky is blue on a clear day."));
        assertTrue(Asking.of("How is jollof rice made?").answers("Jollof rice is made by cooking rice in a tomato and pepper sauce."));
        assertTrue(Asking.of("How many states does Nigeria have?").answers("Nigeria has 36 states and a federal capital territory."));
        assertTrue(!Asking.of("How many states does Nigeria have?").answers("Nigeria is a federal republic in West Africa."));
        assertTrue(Asking.of("How much does a bag of rice cost?").answers("A bag of rice costs ₦75,000 in Lagos."));
        assertTrue(Asking.of("How long did the civil war last?").answers("The war lasted about two and a half years, or 30 months."));
        assertTrue(Asking.of("When did Nigeria become independent?").answers("Nigeria became independent on 1 October 1960."));
        assertTrue(!Asking.of("When did Nigeria become independent?").answers("Nigeria became independent from Britain."));
        assertTrue(Asking.of("Where is Ikeja?").answers("Ikeja is the capital of Lagos State."));
        assertTrue(Asking.of("How tall is Kilimanjaro?").answers("Kilimanjaro is 5,895 m tall."));
        assertTrue(Asking.of("How old is Wole Soyinka?").answers("Wole Soyinka was born in 1934 in Abeokuta."));
    }

    @Test
    public void researchKeepsTheSentencesThatAnswerTheKindAsked() {
        String research = "Web results\n[1] Sky — https://en.wikipedia.org/wiki/Sky\nThe sky is blue on most clear days around the world."
                + "\nThe sky is blue because molecules in the air scatter blue light from the sun more than red light.";
        ResearchAnswer r = ResearchAnswer.from("Why is the sky blue?", research.replace("\\n", "\n"));
        assertTrue(String.valueOf(r == null ? null : r.text), r != null && r.text.contains("because") && !r.text.contains("clear days"));
    }
}

package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import ai.ibiko.ibiko1.formulas.Syntax;
import org.junit.Test;

/** Sentences as nested constituents: phrase structure rules, syntax trees, constituent tests and ambiguity. */
public class SyntaxTest {
    @Test
    public void aSentenceIsAnNpAndAVp() {
        List<Syntax.Tree> t = Syntax.parse("The lead architect deployed the node.");
        assertEquals(1, t.size());
        assertEquals("(S (NP (Det The) (AdjP (Adj lead)) (N architect)) (VP (V deployed) (NP (Det the) (N node))))", t.get(0).bracketed());
        assertTrue(t.get(0).drawn().startsWith(" "));
        assertTrue(t.get(0).drawn().contains("architect"));
    }

    @Test
    public void bothReadingsOfAnAmbiguousSentence() {
        List<Syntax.Tree> t = Syntax.parse("I saw the user with the telescope.");
        assertEquals(2, t.size());
        assertEquals("(S (NP (Pron I)) (VP (V saw) (NP (Det the) (N user)) (PP (P with) (NP (Det the) (N telescope)))))", t.get(0).bracketed());
        assertEquals("(S (NP (Pron I)) (VP (V saw) (NP (NP (Det the) (N user)) (PP (P with) (NP (Det the) (N telescope))))))", t.get(1).bracketed());
        String why = Syntax.ambiguity("I saw the user with the telescope.", t);
        assertTrue(why, why.contains("how the seeing was done") && why.contains("which user"));
    }

    @Test
    public void rulesAreRecursive() {
        Syntax.Tree t = Syntax.parse("The node on the server in the rack in Lagos failed.").get(0);
        assertTrue(t.bracketed(), t.bracketed().startsWith("(S (NP (NP (Det The) (N node)) (PP (P on) (NP (NP"));
        assertTrue(t.bracketed().endsWith("(VP (V failed)))"));
    }

    @Test
    public void constituentsPassTheTestsAndOthersDont() {
        String np = Syntax.constituent("The lead architect deployed the node.", "the lead architect");
        assertTrue(np, np.startsWith("Yes: “The lead architect” is a constituent, a noun phrase (NP)."));
        assertTrue(np, np.contains("Substitution: She deployed the node."));
        assertTrue(np, np.contains("Movement: It was the lead architect that deployed the node."));
        String pp = Syntax.constituent("The engineer deployed the node on the server.", "on the server");
        assertTrue(pp, pp.contains("prepositional phrase") && pp.contains("deployed the node there"));
        assertTrue(Syntax.constituent("The engineer deployed the node on the server.", "deployed the").startsWith("No:"));
    }

    @Test
    public void questionsAndNamesParse() {
        assertEquals("(S (Wh What) (Aux did) (NP (Name Edison)) (VP (V invent)))", Syntax.parse("What did Edison invent?").get(0).bracketed());
        assertTrue(Syntax.parse("Chiamaka Okafor started a company in 2014.").get(0).bracketed()
                .startsWith("(S (NP (Name Chiamaka) (Name Okafor)) (VP (V started)"));
        assertEquals("one reading: \"very\" only goes with \"quickly\"", 1, Syntax.parse("She compiled the code very quickly.").size());
    }

    @Test
    public void itIsAskedInTheChat() {
        assertTrue(SyntaxTalk.reply("Parse: the engineer deployed the node on the server").contains("Reading A"));
        assertTrue(SyntaxTalk.reply("Draw the syntax tree of \"The lead architect deployed the node\"").contains("```"));
        assertTrue(SyntaxTalk.reply("Is \"on the server\" a constituent in \"The engineer deployed the node on the server\"?").startsWith("Yes"));
        assertTrue(SyntaxTalk.reply("Why is \"I saw the user with the telescope\" ambiguous?").startsWith("It's ambiguous"));
        assertTrue(SyntaxTalk.reply("What are the constituents of the new server crashed?").contains("[the new server] NP"));
        assertNull(SyntaxTalk.reply("What is the capital of Ghana?"));
    }
}

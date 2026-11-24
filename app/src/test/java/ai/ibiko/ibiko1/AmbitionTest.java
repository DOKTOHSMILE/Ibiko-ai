package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.ibiko.ibiko1.ideas.Ambition;

/** Open requests of any kind are read for what is to be made and what it's for; the ones other tools own are left to them. */
public class AmbitionTest {
    @Test
    public void anyOpenRequestIsRead() {
        Ambition.Ask plan = Ambition.read("Write a business plan for my bakery");
        assertNotNull(plan);
        assertEquals(Ambition.Kind.MAKE, plan.kind);
        assertEquals("business plan", plan.goal);
        assertEquals("my bakery", plan.about);
        assertEquals("business plan for a bakery", plan.whole());
        Ambition.Ask party = Ambition.read("Can you plan a birthday party for my son?");
        assertEquals("birthday party", party.goal);
        Ambition.Ask app = Ambition.read("Design an app for farmers");
        assertTrue(app.page);
        Ambition.Ask ideas = Ambition.read("Give me some ideas for a church fundraiser");
        assertEquals(Ambition.Kind.IDEAS, ideas.kind);
        assertEquals("church fundraiser", ideas.goal);
        assertNotNull(Ambition.read("Help me write a wedding speech"));
        assertNotNull(Ambition.read("Create a marketing strategy for my fashion brand"));
    }

    @Test
    public void whatOtherToolsOwnIsLeftToThem() {
        assertNull("code", Ambition.read("Write a function to sort a list in Python"));
        assertNull("a drawing", Ambition.read("Design a logo"));
        assertNull("3D", Ambition.read("Build me a chair 40 cm wide"));
        assertNull("a change to what's there", Ambition.read("Make it blue"));
        assertNull("about something there", Ambition.read("Build the screen"));
        assertNull("a letter", Ambition.read("Write me a letter to my mum"));
        assertNull("small", Ambition.read("Make a list of countries in Africa"));
        assertNull("can't be made here", Ambition.read("Make me a cup of tea"));
        assertNull("an app it builds itself", Ambition.read("Make a calculator"));
        assertNull("not a request", Ambition.read("What is a business plan?"));
    }
}

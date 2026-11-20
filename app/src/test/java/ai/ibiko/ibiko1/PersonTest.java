package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

/** What the person says about themselves: understood, kept, and used; and messages cut into their clauses. */
public class PersonTest {
    @Test
    public void whatTheySayIsUnderstoodAndKept() throws Exception {
        java.io.File dir = Files.createTempDirectory("person").toFile();
        Person p = new Person(dir);
        assertEquals("Got it, you're a woman.", p.heard("I'm a woman"));
        assertEquals("Nice to meet you, Ada.", p.heard("my name is ada"));
        assertEquals("Noted: you're 30.", p.heard("I am 30 years old"));
        assertEquals("Got it, you're a nurse.", p.heard("I'm a nurse"));
        assertEquals("Got it, you're an engineer.", new Person(null).heard("I work as an engineer"));
        assertEquals("Nice, you're from Enugu.", p.heard("I'm from Enugu"));
        assertTrue(p.heard("I'm so tired").startsWith("Sorry you're tired"));
        assertNull("not about themselves", p.heard("Lagos is big"));
        assertNull(p.heard("I'm looking for a recipe"));
        assertEquals("You're Ada.", p.asked("What's my name?", null));
        assertEquals("You're from Enugu.", p.asked("Where am I from?", null));
        assertTrue(new Person(dir).about(null).contains("your name is Ada"));
        assertTrue("kept across brains", new Person(dir).about(Arrays.asList("you have two cats")).contains("you have two cats"));
        assertEquals("You haven't told me yet. Tell me and I'll remember.", new Person(null).asked("Where do I live?", null));
    }

    @Test
    public void aMessageIsCutIntoItsClauses() {
        assertEquals(Arrays.asList("I'm a man", "what are you?"), SelfModel.clauses("I'm a man, what are you?"));
        assertEquals(Arrays.asList("I live in Lagos", "I like football"), SelfModel.clauses("I live in Lagos and I like football"));
        assertEquals(Arrays.asList("Who founded Microsoft and when?"), SelfModel.clauses("Who founded Microsoft and when?"));
    }

    @Test
    public void questionsAboutIbikoAreToldApartFromRequests() {
        assertTrue(SelfModel.aboutSelf("Do you dream?"));
        assertTrue(SelfModel.aboutSelf("What's your purpose?"));
        assertTrue(SelfModel.aboutSelf("Are you happy?"));
        assertTrue(!SelfModel.aboutSelf("Can you write a poem?"));
        assertTrue(!SelfModel.aboutSelf("What do you know about Lagos?"));
        assertTrue(!SelfModel.aboutSelf("Who founded Microsoft?"));
    }
}

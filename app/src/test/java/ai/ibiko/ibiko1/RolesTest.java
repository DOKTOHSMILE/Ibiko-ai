package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Who did what to whom: the roles of a question, and the answer read from the shipped articles by their roles. */
public class RolesTest {
    private static final Roles ROLES = new Roles(new Encyclopedia(path -> {
        try {
            return new java.io.FileInputStream(new java.io.File("src/main/assets", path));
        } catch (java.io.IOException e) {
            return null;
        }
    }));

    @Test
    public void questionsAreReadForTheirRoles() {
        Roles.Reading r = Roles.question("Who invented the telephone?");
        assertEquals(Roles.Asks.SUBJECT, r.asks);
        assertEquals("invent", r.verb);
        assertEquals("the telephone", r.object);
        r = Roles.question("What did Alexander Graham Bell invent?");
        assertEquals(Roles.Asks.OBJECT, r.asks);
        assertEquals("Alexander Graham Bell", r.subject);
        r = Roles.question("Who was the Mona Lisa painted by?");
        assertEquals(Roles.Asks.SUBJECT, r.asks);
        assertTrue(r.passive);
        assertEquals("paint", r.verb);
        assertNull("not who-did-what", Roles.question("Who is the president of Nigeria?"));
        assertNull(Roles.question("What did you mean?"));
        assertNull(Roles.question("What is a volcano?"));
    }

    @Test
    public void answersAreReadFromTheArticlesByTheirRoles() {
        assertEquals("The Mona Lisa was painted by Leonardo da Vinci.", ROLES.answer("Who painted the Mona Lisa?").text);
        assertEquals("Things Fall Apart was written by Chinua Achebe.", ROLES.answer("Who wrote Things Fall Apart?").text);
        assertEquals("The Mona Lisa was painted by Leonardo da Vinci.", ROLES.answer("Who was the Mona Lisa painted by?").text);
        assertTrue(ROLES.answer("What did Thomas Edison invent?").text.startsWith("Thomas Edison developed"));
        assertNull("no sentence with that action: nothing said", ROLES.answer("Who ate the Mona Lisa?"));
    }
}

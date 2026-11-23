package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Apps;

/** Small web apps: which one a request asks for, and the whole tested page handed over. */
public class AppsTest {
    private static Apps apps() {
        return new Apps(TextCode.open(path -> {
            try {
                return new FileInputStream("src/main/assets/" + path);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/web/apps.tsv"));
    }

    @Test
    public void theAppAskedFor() {
        Apps a = apps();
        assertEquals(8, a.size());
        assertEquals("todo", a.asked("Create a todo app").id);
        assertEquals("calculator", a.asked("Make a calculator").id);
        assertEquals("bill_split", a.asked("I need a tip calculator").id);
        assertEquals("countdown", a.asked("Write a countdown timer").id);
        assertEquals("contact_form", a.asked("Make a contact form").id);
        assertEquals("login", a.asked("Build a login page").id);
        assertEquals("quiz", a.asked("Make a quiz with a countdown").id);
        assertEquals("stopwatch", a.asked("Can you make me a stopwatch?").id);
        assertNull("a question about one, not a request", a.asked("How does a calculator work?"));
        assertNull("named before the asking", a.asked("My calculator broke, make it stop"));
        assertNull(a.asked("Make a cake"));
    }

    @Test
    public void theWholePageWithHowItWasTested() {
        Apps a = apps();
        String r = Apps.reply(a.asked("Make a calculator"));
        assertTrue(r, r.startsWith("Here's a calculator: one HTML file. Save it as calculator.html"));
        assertTrue(r.contains("```html\n<!doctype html>") && r.contains("</html>\n```"));
        assertTrue("its script came through whole, with no escapes left", r.contains("function calc(text)") && !r.contains("\\n"));
        assertTrue(r.contains("Tested in a browser: pressed 12 + 3 × 4 = and got 24"));
    }
}

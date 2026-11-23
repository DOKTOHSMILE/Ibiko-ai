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

    @Test
    public void theAppJustMadeChangesAsAsked() throws Exception {
        Apps a = apps();
        Apps.Made quiz = new Apps.Made(a.asked("Make a quiz"));
        assertTrue(Apps.asksChange(quiz, "Add a question: What is 2+2? 3, 4, 5. Answer: 4"));
        assertEquals("added \"What is 2+2?\" (answer: 4), in place of my sample questions",
                Apps.change(quiz, "Add a question: What is 2+2? 3, 4, 5. Answer: 4"));
        assertTrue(quiz.html.contains("{ q: \"What is 2+2?\", choices: [\"3\", \"4\", \"5\"], answer: 1 }"));
        assertTrue("the samples are gone", !quiz.html.contains("capital of Nigeria"));
        assertEquals("added \"Which is a fruit?\" (answer: Mango)",
                Apps.change(quiz, "add another question: Which is a fruit? Mango or Brick or Stone; the right answer is mango"));
        assertTrue(quiz.html.contains("answer: 1 },\n  { q: \"Which is a fruit?\""));
        assertNull("the answer must be one of the choices", Apps.change(quiz, "Add a question: Who? A, B. Answer: C"));
        assertEquals("made its colour green", Apps.change(quiz, "make it green"));
        assertEquals("named it Science Quiz", Apps.change(quiz, "Call it Science Quiz"));
        assertTrue(quiz.html.contains("<h1>Science Quiz</h1>") && quiz.html.contains("--accent:#16a34a"));
        keep(quiz);

        Apps.Made form = new Apps.Made(a.asked("Make a contact form"));
        assertEquals("messages go to WhatsApp 2348031234567", Apps.change(form, "My WhatsApp number is +234 803 123 4567"));
        assertTrue(form.html.contains("const WHATSAPP = \"2348031234567\""));
        assertTrue(Apps.change(form, "my number is 0803 123 4567").contains("need the country code"));
        assertEquals("emails go to ada@example.com", Apps.change(form, "my email is ada@example.com"));
        assertTrue(form.html.contains("const EMAIL = \"ada@example.com\""));
        assertTrue("a quiz question means nothing to a form", !Apps.asksChange(form, "Add a question: Who? A, B. Answer: A"));

        Apps.Made login = new Apps.Made(a.asked("Build a login page"));
        assertTrue(Apps.change(login, "My sign-in server is https://api.example.com/login").startsWith("it signs in at https://api.example.com/login"));
        assertTrue(login.html.contains("const SIGN_IN_URL = \"https://api.example.com/login\""));
        assertNull(Apps.change(login, "what time is it"));
        keep(form);
        keep(login);
    }

    /** The changed pages, for a run in a real browser (tools/test_web_apps.js), when APPS_OUT names a folder. */
    private static void keep(Apps.Made m) throws java.io.IOException {
        String dir = System.getenv("APPS_OUT");
        if (dir != null) {
            java.nio.file.Path out = java.nio.file.Paths.get(dir, m.app.id + ".html");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.write(out, m.html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}

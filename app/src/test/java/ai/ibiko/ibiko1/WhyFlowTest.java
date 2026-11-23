package ai.ibiko.ibiko1;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Reasons told are understood, kept, used for "why", and followed back with "why?" until it has to ask. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WhyFlowTest {
    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
        final List<String> steps = Collections.synchronizedList(new ArrayList<String>());
        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { steps.add(step); }
        @Override public void stepsDone() { }
        @Override public void say(String persona, String text) { said.add(text); }
        @Override public void askRating(String question, String answer) { }
        @Override public void noRating(String text) { }
        @Override public boolean ratingWaiting() { return false; }
        @Override public void clearRating() { }
        @Override public boolean online() { return false; }
        @Override public String sum(String message) { return null; }
        @Override public String recallTalk(String question) { return null; }
        @Override public void learnTalk(String question, String answer) { }
        @Override public void forgetTalk(String question) { }
        @Override public int talkSize() { return 0; }
        @Override public int shapes() { return 0; }
        @Override public List<String> aboutPilot() { return new ArrayList<String>(); }
        @Override public void answered(Ibiko1.How how) { }
        @Override public void note(String text) { }
        @Override public void later(Runnable r, long ms) { r.run(); }
        @Override public void onScreen(Runnable r) { r.run(); }
        @Override public android.content.Context context() { return RuntimeEnvironment.getApplication(); }
    }

    private static String ask(Ibiko1 brain, Chat chat, String q) throws InterruptedException {
        int n = chat.said.size();
        chat.steps.clear();
        brain.answer(q);
        for (int i = 0; i < 400 && chat.said.size() == n; i++) {
            Thread.sleep(25);
        }
        return chat.said.size() > n ? chat.said.get(n) : null;
    }

    @Test(timeout = 120000)
    public void reasonsAreKeptAndFollowedBack() throws Exception {
        Store.reset();
        Chat chat = new Chat();
        Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
        String a = ask(brain, chat, "The harvest in Kano was poor because the rains failed.");
        assertTrue(a, a.startsWith("Noted: The harvest in Kano was poor because the rains failed"));
        String b = ask(brain, chat, "The rains failed because the winds shifted south.");
        assertTrue(b, b.startsWith("Noted:"));
        String why = ask(brain, chat, "Why was the harvest in Kano poor?");
        assertTrue(why, why.startsWith("Because the rains failed, which came about because the winds shifted south."));
        String further = ask(brain, chat, "Why?");
        assertTrue(further, further.startsWith("That's as far back as what I've read goes"));
        String told = ask(brain, chat, "The ocean warmed up");
        assertTrue(told, told.contains("The winds shifted south because the ocean warmed up"));
        String way = ask(brain, chat, "Garri is made by grating, pressing and frying cassava.");
        assertTrue(way, way.startsWith("Noted: how Garri is made"));
        String how = ask(brain, chat, "How is garri made?");
        assertTrue(how, how.startsWith("Garri is made by grating, pressing and frying cassava."));
        String ahead = ask(brain, chat, "What happens if the winds shift south?");
        assertTrue(ahead, ahead.startsWith("The rains failed, and as a result the harvest in Kano was poor."));
        // next time, the chain goes a step further
        ask(brain, chat, "Why was the harvest in Kano poor?");
        String again = ask(brain, chat, "why?");
        assertTrue(again, again.startsWith("The winds shifted south because the ocean warmed up."));
    }

    @Test(timeout = 120000)
    public void twoThingsAreToldApartFromTheEncyclopedia() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new java.io.File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        try {
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
            String told = ask(brain, chat, "What is the difference between an ocean and a sea?");
            assertTrue(told, told.startsWith("Ocean is a large body of water between continents; sea is a large body of salt water."));
            assertTrue(told, told.contains("• Ocean:") && told.contains("• Sea:"));
            assertTrue(String.valueOf(chat.steps), chat.steps.contains("Finding what kind of thing each is"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 120000)
    public void whatGoesAgainstWhatItHoldsIsAskedAboutNotTakenQuietly() throws Exception {
        Store.reset();
        Chat chat = new Chat();
        Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
        String told = ask(brain, chat, "Ghana became independent on 6 March 1957.");
        assertTrue(told, told.startsWith("Noted:"));
        String against = ask(brain, chat, "Ghana became independent on 6 March 1958.");
        assertTrue(against, against.startsWith("That's not what I have. I read that Ghana became independent on 6 March 1957, "
                + "but you say Ghana became independent on 6 March 1958. Should I change it"));
        String kept = ask(brain, chat, "No, you're right");
        assertTrue(kept, kept.startsWith("Okay, I'll keep what I had: Ghana became independent on 6 March 1957."));
        String again = ask(brain, chat, "Ghana became independent on 6 March 1958.");
        assertTrue(again, again.startsWith("That's not what I have. I have read more than once"));
        String changed = ask(brain, chat, "Yes, change it");
        assertTrue(changed, changed.startsWith("Updated: Ghana became independent on 6 March 1958. I've dropped 6 March 1957."));
        // a measure far off the one held is asked about too; one close to it isn't a contradiction
        ask(brain, chat, "Lagos has about 15 million people.");
        String more = ask(brain, chat, "Lagos has about 21 million people.");
        assertTrue(more, more.startsWith("That's not what I have. I read that the population of Lagos is 15,000,000"));
        String not = ask(brain, chat, "What is the capital of France?");
        assertTrue("a new question is answered, not taken as yes or no: " + not, !not.startsWith("Updated") && !not.startsWith("Okay, I'll keep"));
    }

    @Test(timeout = 180000)
    public void staysOnTheSubjectAndLinksNewThingsToIt() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new java.io.File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        try {
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
            ask(brain, chat, "Tell me about Nigeria");
            // a part of the subject, named with nothing else: of the subject
            ask(brain, chat, "What is the capital?");
            assertTrue(String.valueOf(chat.steps), chat.steps.toString().contains("“the capital” is the capital of Nigeria"));
            // something of another kind: linked to the subject, not the last question asked again about it
            String oil = ask(brain, chat, "What about oil?");
            assertTrue(String.valueOf(chat.steps), chat.steps.toString().contains("oil, with Nigeria"));
            assertTrue(oil, !oil.toLowerCase(java.util.Locale.ROOT).contains("capital of oil"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void remembersTheConversationAndGoesBackToASubject() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new java.io.File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        try {
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
            ask(brain, chat, "Tell me about Nigeria");
            ask(brain, chat, "What is the capital?");
            ask(brain, chat, "Who was Nelson Mandela?");
            String recap = ask(brain, chat, "What have we talked about?");
            assertTrue(recap, recap.startsWith("So far we've talked about:"));
            assertTrue(recap, recap.contains("Nigeria (2 questions") && recap.contains("Nelson Mandela"));
            String said = ask(brain, chat, "What did you say about Nigeria?");
            assertTrue(said, said.startsWith("You asked “What is the capital of Nigeria?”"));
            String back = ask(brain, chat, "Let's go back to Nigeria");
            assertTrue(back, back.startsWith("Back to Nigeria."));
            ask(brain, chat, "What is the population?");
            assertTrue(String.valueOf(chat.steps), chat.steps.toString().contains("the population of Nigeria"));
            String first = ask(brain, chat, "What was the first thing I asked?");
            assertTrue(first, first.equals("The first thing you asked was: “Tell me about Nigeria”"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void questionsInsideQuestionsAreWorkedOutStepByStep() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new java.io.File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        try {
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
            String capital = ask(brain, chat, "What is the capital of the country where Mount Kilimanjaro is?");
            assertTrue(String.valueOf(chat.steps), chat.steps.toString().contains("Working out the country Mount Kilimanjaro is in: Tanzania"));
            assertTrue(capital, capital.contains("Dodoma"));
            String no = ask(brain, chat, "Is Lagos the capital of Nigeria?");
            assertTrue(no, no.startsWith("No. The capital of Nigeria is Abuja, not Lagos."));
            String kenya = ask(brain, chat, "Was Barack Obama born in Kenya?");
            assertTrue(kenya, kenya.startsWith("No. Barack Obama was born in Honolulu"));
            String yes = ask(brain, chat, "Is Swahili spoken in Tanzania?");
            assertTrue(yes, yes.startsWith("Yes."));
            String born = ask(brain, chat, "When was the founder of Microsoft born?");
            assertTrue(String.valueOf(chat.steps), chat.steps.toString().contains("the founder of Microsoft: Bill Gates"));
            assertTrue(born, born.contains("1955"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void writesCodeFromItsTestedRecipes() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new java.io.File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        try {
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
            String go = ask(brain, chat, "How do I reverse a string in Go?");
            assertTrue(go, go.startsWith("Here's how to reverse a string in Go:") && go.contains("```go") && go.contains("Tested"));
            String rust = ask(brain, chat, "And in Rust?");
            assertTrue("the same task, another language: " + rust, rust.startsWith("Here's how to reverse a string in Rust:"));
            String any = ask(brain, chat, "Write a factorial function");
            assertTrue("the chat's language goes on: " + any, any.startsWith("Here's how to work out a factorial in Rust:"));
            String site = ask(brain, chat, "Build me a website for my bakery called Sweet Crumbs in Lagos, WhatsApp 0803 123 4567");
            assertTrue(site, site.startsWith("Here's a restaurant or food website for Sweet Crumbs in Lagos:") && site.contains("```html\n<!doctype html>"));
            String need = ask(brain, chat, "What does a restaurant website need?");
            assertTrue(need, need.startsWith("A restaurant or food website shows the food"));
            String made = ask(brain, chat, "What is a website made of?");
            assertTrue(made, made.startsWith("Every website is made of the same layers"));
            String read = ask(brain, chat, "What does this do?\n```\ndef add(a, b):\n    return a + b\n\nprint(add(2, 3))\n```");
            assertTrue(read, read.startsWith("This is Python code.") && read.contains("Line by line:"));
            String kotlin = ask(brain, chat, "How do I sort a list in Kotlin?");
            assertTrue("offline, not in its recipes: said plainly: " + kotlin, kotlin.startsWith("I don't have that in Kotlin yet"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void questionsAboutCodeJustRead() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new java.io.File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        try {
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
            String code = "def factorial(n):\n    result = 1\n    for i in range(2, n + 1):\n        result *= i\n    return result\n\nprint(factorial(5))";
            String read = ask(brain, chat, "Explain this:\n```python\n" + code + "\n```");
            assertTrue(read, read.startsWith("This is Python code. It's the usual way to work out a factorial."));
            String go = ask(brain, chat, "Convert it to Go");
            assertTrue(go, go.startsWith("It does the same as my recipe to work out a factorial, so here it is in Go:") && go.contains("```go"));
            String line = ask(brain, chat, "What does line 4 do?");
            assertTrue(line, line.startsWith("Line 4: result *= i") && line.contains("Multiplies result by i."));
            String ok = ask(brain, chat, "Are there any mistakes?");
            assertTrue(ok, ok.startsWith("I can't see a mistake in it"));
        } finally {
            Plug.asset = was;
        }
    }
}

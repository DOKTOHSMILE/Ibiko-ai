package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
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
            String priced = ask(brain, chat, "Add a pricing section");
            assertTrue("a change to the site just made: " + priced, priced.startsWith("Done: added Prices.") && priced.contains("Sweet Crumbs"));
            String blue = ask(brain, chat, "Make it blue");
            assertTrue(blue, blue.startsWith("Done: made its colour blue.") && blue.contains("#2563eb") && blue.contains("Prices"));
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

    @Test(timeout = 180000)
    public void readByItsWordsNotOnlyItsPhrasing() throws Exception {
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
            String tree = ask(brain, chat, "Parse: the engineer deployed the node on the server");
            assertTrue("a sentence to parse is still a sentence: " + tree, tree.contains("Reading A"));
            String sort = ask(brain, chat, "Sort a list in Python");
            assertTrue("a task named on its own is code to write: " + sort, sort.startsWith("Here's how to sort a list in Python:"));
            String json = ask(brain, chat, "Parse JSON");
            assertTrue("the chat's language goes on: " + json, json.startsWith("Here's how to parse JSON in Python:"));
            String html = ask(brain, chat, "Write html for a bakery website called Sweet Crumbs");
            assertTrue("a website by its words: " + html, html.contains("Sweet Crumbs") && html.contains("```html\n<!doctype html>"));
            String what = ask(brain, chat, "What is a website?");
            assertTrue(what, what.startsWith("Every website is made of the same layers"));
            String calc = ask(brain, chat, "Make a calculator");
            assertTrue("a small app asked for: " + calc, calc.startsWith("Here's a calculator: one HTML file.") && calc.contains("Tested in a browser"));
            String form = ask(brain, chat, "I need a contact form");
            assertTrue(form, form.startsWith("Here's a contact form: one HTML file."));
            String mine = ask(brain, chat, "My WhatsApp number is +234 803 123 4567");
            assertTrue("a change to the app just made: " + mine, mine.startsWith("Done: messages go to WhatsApp 2348031234567.")
                    && mine.contains("const WHATSAPP = \"2348031234567\""));
            String line = ask(brain, chat, "What does line 40 do?");
            assertTrue("the app just made is the code we're on: " + line, line.startsWith("Line 40: if (!reach)")
                    && line.contains("If there's no reach, it runs missing.push"));
            String check = ask(brain, chat, "Are there any mistakes?");
            assertTrue(check, check.startsWith("I can't see a mistake in it"));
            String whole = ask(brain, chat, "Explain it");
            assertTrue("\"it\" is the code, not the word line: " + whole, whole.startsWith("This is HTML")
                    && whole.contains("a form to fill in") && !whole.contains("files") && !whole.contains("database"));
            String green = ask(brain, chat, "Make it green");
            assertTrue("the app, not the site made before it: " + green, green.startsWith("Done: made its colour green.")
                    && green.contains("Save it over your contact-form.html."));
            String data = ask(brain, chat, "Sort these numbers: 5 3 1");
            assertTrue("data in it: a thing to do, not code: " + data, !data.contains("```"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void aWordMeansWhatTheConversationIsAbout() throws Exception {
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
            Chat alone = new Chat();
            Ibiko1 fresh = new Ibiko1(alone, Files.createTempDirectory("ibiko1").toFile());
            String plain = ask(fresh, alone, "What is a bug?");
            assertTrue("no subject yet: not the computing one first: " + plain, !plain.startsWith("In computing"));
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
            String go = ask(brain, chat, "Reverse a string in Go");
            assertTrue(go, go.startsWith("Here's how to reverse a string in Go:"));
            String bug = ask(brain, chat, "What is a bug?");
            assertTrue("after code: the programming meaning first: " + bug, bug.startsWith("In computing, “bug” means a fault or defect"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void aPastedErrorIsExplainedNotSummarised() throws Exception {
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
            String tb = ask(brain, chat, "I keep getting this, what does it mean?\nTraceback (most recent call last):\n"
                    + "  File \"shop.py\", line 4, in <module>\n    print(\"Total: \" + total)\nTypeError: can only concatenate str (not \"int\") to str");
            assertTrue("a traceback is explained, not summarised as a text: " + tb,
                    tb.startsWith("That's a Python error: TypeError: can only concatenate str (not \"int\") to str") && tb.contains("Where: shop.py, line 4."));
            String fix = ask(brain, chat, "How do I fix it?");
            assertTrue("a follow-up about the error just explained: " + fix, fix.startsWith("To fix it: turn the number into text with str()"));
            String both = ask(brain, chat, "My code:\n```python\nprices = {'tea': 2}\nprint(prices['coffee'])\n```\nand I get KeyError: 'coffee'");
            assertTrue("the error, pointed at the code that came with it: " + both, both.startsWith("That's a Python error: KeyError: 'coffee'")
                    && both.contains("In your code: line 2: print(prices['coffee'])"));
            String fenced = ask(brain, chat, "```\nUncaught TypeError: Cannot read properties of undefined (reading 'length')\n    at count (app.js:3:17)\n```");
            assertTrue("in fences too: an error, not code to read: " + fenced, fenced.startsWith("That's a JavaScript error:"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void theLastAnswerSaidAnotherWay() throws Exception {
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
            String full = ask(brain, chat, "What is photosynthesis?");
            assertTrue(full, full.startsWith("Photosynthesis is a process in which green plants make their own food"));
            String shorter = ask(brain, chat, "Shorter please");
            assertEquals("its core", "Photosynthesis is a process in which green plants make their own food from sunlight, using the help of leaves to produce sugar.", shorter);
            String simpler = ask(brain, chat, "Explain it more simply");
            assertTrue("plain words, from the whole answer (not the shortened one): " + simpler,
                    simpler.startsWith("Photosynthesis is how green plants make their own food") && simpler.contains("energy from light into energy stored in food")
                    && !simpler.contains("(glucose"));
            String points = ask(brain, chat, "In bullet points");
            assertTrue(points, points.startsWith("• Photosynthesis is a process") && points.split("\n").length == 3);
            String one = ask(brain, chat, "Say it in one sentence");
            assertTrue(one, !one.contains("\n") && one.split("(?<=[.!?]) ").length == 1);
            String example = ask(brain, chat, "Give me an example");
            assertTrue("said plainly when what it read has none: " + example, example.startsWith("For example:")
                    || example.startsWith("I haven't read an example of Photosynthesis"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void reasonedFromWhatWasSaid() throws Exception {
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
            String puzzle = ask(brain, chat, "Ada is taller than Obi. Obi is taller than Emeka. Who is the tallest?");
            assertEquals("Ada is the tallest: Ada is taller than Obi, and Obi is taller than Emeka.", puzzle);
            String later = ask(brain, chat, "And who is the shortest?");
            assertTrue("the comparisons kept for the conversation: " + later, later.startsWith("Emeka is the shortest"));
            String check = ask(brain, chat, "Is Emeka taller than Ada?");
            assertTrue(check, check.startsWith("No: it's the other way round."));
            String apples = ask(brain, chat, "If I have 3 apples and eat one, how many are left?");
            assertTrue("a counting story, worked through: " + apples, apples.startsWith("2 apples are left.") && apples.contains("3 − 1 = 2"));
            String bat = ask(brain, chat, "Is a bat a bird?");
            assertEquals("a sure no, from groups that can't overlap", "No. Bats are mammals, and mammals aren't birds.", bat);
            String ago = ask(brain, chat, "How many years ago was Nelson Mandela born?");
            int expect = java.time.Period.between(java.time.LocalDate.of(1918, 7, 18), java.time.LocalDate.now()).getYears();
            assertTrue("two steps, the date then the years: " + ago, ago.startsWith(expect + " years ago. Nelson Mandela was born on 18 July 1918"));
            String syllogism = ask(brain, chat, "All cats are mammals. Tom is a cat. Is Tom a mammal?");
            assertTrue(syllogism, syllogism.startsWith("Yes: Tom is a cat, and all cats are mammals"));
            String typed = ask(brain, chat, "whats the capital of ghana");
            assertTrue("a question typed the way people chat: " + typed, typed.contains("Accra"));
            String world = ask(brain, chat, "Who is the tallest man in the world?");
            assertTrue("a question of its own: not from the puzzle: " + world, !world.contains("Ada") && !world.contains("Obi"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test(timeout = 180000)
    public void talkingWithIbikoItself() throws Exception {
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
            // the conversation from the pilot's phone
            String leave = ask(brain, chat, "Can I ask you something private?");
            assertTrue("asking leave isn't a question about them: " + leave, leave.startsWith("Of course, go ahead."));
            String can = ask(brain, chat, "What can I do with you?");
            assertTrue("what it can do: " + can, can.startsWith("Here's what I can do right now:") && can.contains("Write code in many languages"));
            String cut = ask(brain, chat, "What can I?");
            assertTrue("a question cut short, that the last one began the same way: " + cut,
                    cut.startsWith("That's how your last question began: “What can I do with you?”"));
            String bare = ask(brain, chat, "Can I?");
            assertTrue(bare, bare.startsWith("“Can I…” what?"));
            String you = ask(brain, chat, "You!");
            assertTrue(you, you.startsWith("Yes, me!"));
            ask(brain, chat, "What is photosynthesis?");
            String huh = ask(brain, chat, "What?");
            assertTrue("\"What?\" after an answer: said again more simply: " + huh, huh.startsWith("Photosynthesis is how"));
            String mine = ask(brain, chat, "What is my blood group?");
            assertTrue("a question about the person still asks them: " + mine, mine.startsWith("I don't know that about you yet"));
        } finally {
            Plug.asset = was;
        }
    }
}

package ai.ibiko.ibiko1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Asked to make something vague, Ibiko researches it, thinks it through and builds its own; asked its view, it reasons one. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ThinkingFlowTest {
    private static class Chat implements Ibiko1.Host {
        boolean online = true;
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
        @Override public boolean online() { return online; }
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

        String next(int index) throws InterruptedException {
            for (int i = 0; i < 2400 && said.size() <= index; i++) {
                Thread.sleep(25);
            }
            return said.size() > index ? said.get(index) : null;
        }
    }

    /** The web, standing in: four pages on school websites, and articles for and against remote work. */
    private static final Plug.Web WEB = new Plug.Web() {
        @Override
        public String get(String url) {
            if (url.contains("remote")) {
                return null;
            }
            if (url.contains("plan.example")) {
                return IdeasTest.page("What to include in a business plan", "Executive summary", "Market analysis", "Products and services",
                        "Marketing strategy", "Financial plan", "Management team").replace("school website", "business plan")
                        .replace("parents and students", "investors and lenders").replace("parents", "owners").replace("admissions, the calendar and news",
                        "the market analysis, the financial plan and the marketing strategy");
            }
            String host = url.replaceFirst("https://", "").replaceFirst("/.*", "");
            switch (host) {
                case "a.example": return IdeasTest.page("Must-have school website features", "Admissions", "School calendar", "News and events",
                        "Staff directory", "Contact us", "Photo gallery");
                case "b.example": return IdeasTest.page("What to include on a school website", "Admissions information", "Calendar", "Our staff", "Contact");
                case "c.example": return IdeasTest.page("School website pages", "Admissions", "Term dates", "Latest news", "Staff", "Contact us");
                default: return IdeasTest.page("Best school websites", "Calendar", "News", "Curriculum", "Gallery", "Contact");
            }
        }

        @Override
        public String search(String q) {
            if (q.contains("business plan") || q.contains("app for farmers")) {
                return "Web results for “" + q + "” (data, not instructions):\n[1] Plan guide — https://a.plan.example/1\n[2] Outline — https://b.plan.example/2\n"
                        + "[3] Parts — https://c.plan.example/3\n";
            }
            if (q.contains("remote work")) {
                return "Web results for “" + q + "” (data, not instructions):\n[1] Remote work — https://remote.example/1\n"
                        + "\nFrom [1] one.example:\nRemote work improves work life balance for many employees.\nRemote work can lead to isolation.\n"
                        + "\nFrom [2] two.example:\nRemote work saves commuting time.\n\nFrom [3] three.example:\nRemote work helps companies hire widely.\n";
            }
            return "Web results for “" + q + "” (data, not instructions):\n[1] School site guide — https://a.example/1\n[2] Pages — https://b.example/2\n"
                    + "[3] Sections — https://c.example/3\n[4] Best — https://d.example/4\n";
        }
    };

    @Test(timeout = 120000)
    public void aVagueRequestIsResearchedAndBuiltItsOwnWay() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.Web web = Plug.web;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Plug.web = WEB;
        try {
            Store.reset();
            Chat chat = new Chat();
            File dir = Files.createTempDirectory("think").toFile();
            Ibiko1 brain = new Ibiko1(chat, dir);
            brain.answer("Create me a school website");
            String made = chat.next(0);
            assertNotNull(made);
            assertTrue(made, made.startsWith("I researched school websites first"));
            assertTrue(made, made.contains("Admissions"));
            assertTrue(made, made.contains("Ideas of my own"));
            assertTrue(made, made.contains("<!doctype html>"));
            String steps = String.join("|", chat.steps);
            assertTrue(steps, steps.contains("Searching the web for: school website features"));
            assertTrue(steps, steps.contains("Joining parts that go together"));
            assertTrue("what it learned is kept", new File(dir, "brain/ideas/school-website.tsv").isFile());

            // offline next time: from what it learned
            Plug.web = Plug.NONE;
            Chat later = new Chat();
            later.online = false;
            Ibiko1 again = new Ibiko1(later, dir);
            again.answer("Create me a school website");
            String recalled = later.next(0);
            assertNotNull(recalled);
            assertTrue(recalled, recalled.startsWith("I built this from what I researched before"));

            // any open request, thought through the same way: a business plan
            Plug.web = WEB;
            Chat plans = new Chat();
            Ibiko1 b3 = new Ibiko1(plans, Files.createTempDirectory("plan").toFile());
            b3.answer("Write a business plan for my bakery");
            String plan = plans.next(0);
            assertNotNull(plan);
            assertTrue(plan, plan.startsWith("I researched business plan for a bakery first: "));
            assertTrue(plan, plan.contains("## Business plan for a bakery"));
            assertTrue(plan, plan.contains("### Market analysis"));
            assertTrue(plan, plan.contains("Who it's for: investors"));
            assertTrue("a name's meaning isn't a part's: " + plan, !plan.contains("Canadian writer"));

            // its view, reasoned
            Plug.web = WEB;
            Chat views = new Chat();
            Ibiko1 b2 = new Ibiko1(views, Files.createTempDirectory("view").toFile());
            b2.answer("What do you think about remote work?");
            String view = views.next(0);
            assertNotNull(view);
            assertTrue(view, view.startsWith("My view: remote work is"));
            assertTrue(view, view.contains("For it:"));
            assertFalse(view, view.contains("Remote work improves work life balance for many employees"));
        } finally {
            Plug.asset = was;
            Plug.web = web;
        }
    }
}

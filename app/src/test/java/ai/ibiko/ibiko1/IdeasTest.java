package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Sites;
import ai.ibiko.ibiko1.ideas.Corpus;
import ai.ibiko.ibiko1.ideas.Ideation;
import ai.ibiko.ibiko1.ideas.Maker;
import ai.ibiko.ibiko1.ideas.Opinion;
import ai.ibiko.ibiko1.ideas.Writer;

/**
 * Thinking from research, not from stored samples: the pages here stand in for the web (the app ships nothing about
 * schools); what Ibiko makes comes from what they share, weighed, joined into new ideas, and said in its own words.
 */
public class IdeasTest {
    static String page(String title, String... items) {
        StringBuilder b = new StringBuilder("<html><head><title>" + title + "</title><script>var x=1;</script></head><body>"
                + "<nav><ul><li>Home</li><li>Login</li><li>Privacy policy</li></ul></nav><h1>" + title + "</h1><ul>");
        for (String i : items) {
            b.append("<li>").append(i).append("</li>");
        }
        b.append("</ul><p>A good school website helps parents and students find what they need quickly, such as admissions, the calendar and news.</p>")
                .append("<p>Many schools publish term dates on the calendar so that parents can plan the year ahead of time.</p></body></html>");
        return b.toString();
    }

    static Corpus schools() {
        Corpus c = new Corpus();
        c.addPage("a.example", page("10 must-have school website features", "Admissions", "School calendar", "News and events", "Staff directory",
                "Contact us", "Photo gallery"));
        c.addPage("b.example", page("What to include on a school website", "Admissions information", "Calendar", "Our staff", "Contact",
                "Parent portal"));
        c.addPage("c.example", page("School website pages", "Admissions", "Term dates", "Latest news", "Staff", "Contact us", "Curriculum"));
        c.addPage("d.example", page("Best school websites", "Calendar", "News", "Curriculum", "Gallery", "Contact"));
        return c;
    }

    static boolean person(String w) {
        return Arrays.asList("parent", "student", "teacher", "pupil").contains(w);
    }

    @Test
    public void partsAreWeighedFromWhatSourcesShare() {
        Ideation.Plan plan = Ideation.think(schools(), "school", "website", "create me a school website", w -> w.equals("curriculum"), IdeasTest::person);
        List<String> keys = new ArrayList<String>();
        for (Ideation.Part p : plan.parts) {
            keys.add(p.key);
        }
        assertTrue(keys.toString(), keys.contains("admission"));
        assertTrue(keys.toString(), keys.contains("calendar"));
        assertTrue(keys.toString(), keys.contains("contact"));
        assertTrue(keys.toString(), keys.contains("staff"));
        assertFalse("the web's furniture isn't a part: " + keys, keys.contains("home") || keys.contains("login") || keys.contains("privacy policy"));
        assertFalse("the thing's own name isn't a part: " + keys, keys.contains("school") || keys.contains("website"));
        Ideation.Part calendar = plan.parts.get(keys.indexOf("calendar"));
        assertTrue("in most sources, so likely: " + calendar.p, calendar.p > 0.6);
        assertEquals(Arrays.asList("parents", "students"), plan.audience);
        assertFalse("ideas joined from related parts: " + plan.parts, plan.ideas.isEmpty());
        for (Ideation.Idea i : plan.ideas) {
            assertFalse("a new idea is no source's: " + i.title, schools().sources.toString().toLowerCase().contains(i.title.toLowerCase()));
        }
    }

    @Test
    public void theSiteIsWrittenInItsOwnWords() {
        Corpus c = schools();
        Ideation.Plan plan = Ideation.think(c, "school", "website", "create me a school website", w -> false, IdeasTest::person);
        Sites.Brief brief = Sites.brief("create me a school website called Bright Stars in Kano");
        Maker.Made made = Maker.site(plan, "school", brief, w -> w.equals("calendar") ? "a list of dates and the events planned for them" : null, null, c, false);
        String html = Sites.build(made.design);
        assertTrue(html, html.contains("<h1>Bright Stars</h1>"));
        assertTrue(html, html.contains("id=\"contact\""));
        assertTrue(made.said, made.said.startsWith("I researched school websites first: 4 sources."));
        assertTrue(made.said, made.said.contains("Who it's for: parents and students."));
        assertTrue(made.said, made.said.contains("Ideas of my own"));
        for (Corpus.Source s : c.sources) {
            for (String sentence : s.sentences) {
                assertFalse("no sentence of a source is copied: " + sentence, html.contains(sentence));
            }
        }
        assertFalse(Writer.copies(Writer.say("calendar", "Bright Stars", "Kano", plan.audience, w -> null, c), c));
    }

    @Test
    public void copiesAreCaught() {
        Corpus c = schools();
        assertTrue(Writer.copies("Many schools publish term dates on the calendar so that parents plan", c));
        assertFalse(Writer.copies("Term dates for the year, for families to plan around.", c));
    }

    @Test
    public void aViewIsReasonedNotRepeated() {
        Corpus c = new Corpus();
        Corpus.Source a = c.add("a");
        a.sentences.add("Remote work improves work life balance for many employees.");
        a.sentences.add("Remote work can lead to isolation for some workers.");
        Corpus.Source b = c.add("b");
        b.sentences.add("Studies show remote work saves commuting time and boosts productivity.");
        Corpus.Source d = c.add("c");
        d.sentences.add("Critics say remote work brings burnout and isolation risks.");
        Corpus.Source e = c.add("d");
        e.sentences.add("Remote work helps companies hire from anywhere.");
        String view = Opinion.form("remote work", c);
        assertNotNull(view);
        assertTrue(view, view.startsWith("My view: remote work is "));
        assertTrue("its sureness in numbers and words: " + view, view.matches("(?s).*about \\d+% that it's good on balance \\(.+\\).*"));
        assertTrue(view, view.contains("For it:"));
        assertTrue(view, view.contains("Against it:"));
        assertTrue(view, view.contains("isolation"));
        assertTrue(view, view.contains("Looking ahead, it could lead to isolation"));
        assertTrue(view, view.contains("What would change my mind"));
        for (Corpus.Source s : c.sources) {
            for (String sentence : s.sentences) {
                assertFalse("reasons in its own few words: " + sentence, view.contains(sentence.replaceAll("\\.$", "")));
            }
        }
    }
}

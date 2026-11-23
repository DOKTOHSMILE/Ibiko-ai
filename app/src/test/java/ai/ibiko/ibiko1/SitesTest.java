package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Sites;

/** Websites: kinds known, explained and told apart, and a whole site built for a request. */
public class SitesTest {
    private static Sites sites() {
        return new Sites(TextCode.open(path -> {
            try {
                return new FileInputStream("src/main/assets/" + path);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/web/sites.tsv"));
    }

    @Test
    public void readsWhatIsAsked() {
        assertEquals(Sites.Ask.Is.BUILD, Sites.read("Build me a website for my bakery called Sweet Crumbs in Lagos").is);
        assertEquals(Sites.Ask.Is.BUILD, Sites.read("Can you create an online store for my fashion brand?").is);
        assertEquals(Sites.Ask.Is.MADE_OF, Sites.read("What is a website made of?").is);
        Sites.Ask e = Sites.read("What does a restaurant website need?");
        assertEquals(Sites.Ask.Is.EXPLAIN, e.is);
        assertEquals("restaurant", e.a);
        Sites.Ask c = Sites.read("What is the difference between a blog and a portfolio website?");
        assertEquals(Sites.Ask.Is.COMPARE, c.is);
        assertEquals("blog", c.a);
        assertEquals("portfolio", c.b);
        assertNull(Sites.read("What is the capital of Nigeria?"));
        assertNull(Sites.read("Make me a cup of tea"));
    }

    @Test
    public void knowsTheKinds() {
        Sites s = sites();
        assertEquals(20, s.size());
        assertEquals("restaurant", s.kind("my bakery").id);
        assertEquals("shop", s.kind("an online store for shoes").id);
        assertEquals("church", s.kind("our church in Enugu").id);
        assertEquals("salon", s.kind("a barber shop").id);
        assertNull(s.kind("something else entirely"));
        String said = Sites.explain(s.kind("restaurant"));
        assertTrue(said, said.startsWith("A restaurant or food website shows the food"));
        assertTrue(said, said.contains("• Sections: ") && said.contains("ordering on WhatsApp") && said.contains("Design patterns"));
        String diff = Sites.compare(s.kind("blog"), s.kind("portfolio"));
        assertTrue(diff, diff.contains("A blog publishes articles") && diff.contains("a portfolio shows your best work"));
        assertTrue(Sites.madeOf().contains("Structure (HTML)") && Sites.madeOf().contains("Behaviour (JavaScript)"));
    }

    @Test
    public void theBriefIsReadFromTheRequest() {
        Sites.Brief b = Sites.brief("Build me a website for my bakery called Sweet Crumbs in Lagos, WhatsApp 0803 123 4567, email hi@sweetcrumbs.ng");
        assertEquals("Sweet Crumbs", b.name);
        assertEquals("Lagos", b.place);
        assertEquals("08031234567", b.phone);
        assertEquals("hi@sweetcrumbs.ng", b.email);
        assertEquals("bakery", b.trade);
        assertEquals("Grace Salon", Sites.brief("make a site for \"Grace Salon\" in Abuja").name);
    }

    @Test
    public void aWholeSiteIsBuilt() {
        Sites s = sites();
        Sites.Brief b = Sites.brief("Build me a website for my bakery called Sweet Crumbs in Lagos, WhatsApp 0803 123 4567");
        String html = Sites.build(s.kind("bakery"), b);
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("<meta name=\"viewport\""));
        assertTrue(html.contains("<title>Sweet Crumbs · Lagos</title>"));
        for (String id : new String[] {"menu", "hours", "about", "gallery", "testimonials", "contact"}) {
            assertTrue(id, html.contains("<section id=\"" + id + "\">"));
        }
        assertTrue("the Nigerian number, international for WhatsApp", html.contains("https://wa.me/2348031234567"));
        assertTrue("a menu button on phones", html.contains("aria-controls=\"nav\""));
        assertBalanced(html);
        for (String kind : new String[] {"landing page", "online shop", "portfolio", "blog", "school", "church", "ngo", "wedding", "real estate",
            "salon", "hotel", "clinic", "saas", "news", "gym", "agency", "law firm", "resume", "business"}) {
            Sites.Kind k = s.kind(kind);
            assertNotNull(kind, k);
            assertBalanced(Sites.build(k, new Sites.Brief()));
        }
    }

    /** Every tag closed in order (void tags aside): the page is well formed. */
    private static void assertBalanced(String html) {
        Pattern tag = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*?(/?)>");
        String body = html.replaceAll("(?s)<script>.*?</script>", "").replaceAll("(?s)<style>.*?</style>", "");
        Deque<String> open = new ArrayDeque<String>();
        Matcher m = tag.matcher(body);
        while (m.find()) {
            String name = m.group(2).toLowerCase();
            if (name.matches("meta|br|input|img|link|hr")) {
                continue;
            }
            if (m.group(1).isEmpty()) {
                open.push(name);
            } else {
                assertEquals("closing </" + name + "> at " + m.start(), name, open.pop());
            }
        }
        assertTrue("left open: " + open, open.isEmpty());
    }

    @Test
    public void theSiteJustMadeChangesAsAsked() {
        Sites.Kind k = sites().kind("my bakery");
        Sites.Design d = Sites.design(k, Sites.brief("Build a website for my bakery called Sweet Crumbs in Lagos"));
        assertTrue(Sites.asksChange("Add a FAQ section"));
        assertTrue(Sites.asksChange("make it dark"));
        assertTrue(Sites.asksChange("Call it Mama's Kitchen"));
        assertTrue(!Sites.asksChange("Add more salt to the soup"));
        assertTrue(!Sites.asksChange("no problem"));
        assertTrue(!Sites.asksChange("use python"));

        assertEquals("added Questions", Sites.change(d, "Add a FAQ section"));
        String html = Sites.build(d);
        assertTrue(html.contains("id=\"faq\""));
        String lastBlock = d.sections.get(d.sections.size() - 1)[0];
        assertTrue("the page still ends with its form: " + lastBlock, lastBlock.matches("contact|booking|rsvp|cta"));
        assertEquals("it already has Questions", Sites.change(d, "add a faq"));
        assertEquals("made it dark", Sites.change(d, "make it dark"));
        assertTrue(Sites.build(d).contains("#0f172a"));
        assertEquals("named it Mama's Kitchen", Sites.change(d, "Call it Mama's Kitchen"));
        assertTrue(Sites.build(d).contains("Mama's Kitchen") || Sites.build(d).contains("Mama&#39;s Kitchen") || Sites.build(d).contains("Mama&apos;s Kitchen"));
        assertEquals("put in 08031234567 (calls and WhatsApp; the forms send to it)", Sites.change(d, "my whatsapp number is 0803 123 4567"));
        assertTrue(Sites.build(d).contains("2348031234567") || Sites.build(d).contains("08031234567"));
        String removed = Sites.change(d, "remove the faq section");
        assertEquals("took out Questions", removed);
        assertNull(Sites.change(d, "remove the hero"));
        assertNull(Sites.change(d, "what time is it"));
    }
}

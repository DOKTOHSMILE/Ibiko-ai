package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import ai.ibiko.brain.MiniJson;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Pictures by formula: fresh for every seed, the model's design honoured, the words kept as text. */
public class DesignGenTest {
    @Test
    public void everySeedGivesItsOwnPictureAndTheSameSeedTheSame() {
        Set<String> seen = new HashSet<String>();
        for (long s = 1; s <= 30; s++) {
            seen.add(DesignGen.make(s, "Clarity Before Code", "Plan first", "Tech", null, 1080, 1080));
        }
        assertEquals("30 seeds, 30 pictures", 30, seen.size());
        assertEquals(DesignGen.make(7, "A", "B", "C", null, 1080, 1080), DesignGen.make(7, "A", "B", "C", null, 1080, 1080));
    }

    @Test
    public void theModelsDesignIsUsed() {
        DesignGen.Spec spec = DesignGen.Spec.of(MiniJson.parse("{\"mood\":\"light\",\"colors\":[\"#e11d48\",\"#0ea5e9\"],"
                + "\"background\":\"grid\",\"shapes\":[\"ring\",\"sparkle\"],\"layout\":\"card\",\"font\":\"Playfair Display\","
                + "\"accent\":\"Code\",\"upper\":false}"));
        String html = DesignGen.make(42, "Clarity Before Code", "Plan first", "Tech", null, 1080, 1080, spec);
        assertTrue(html.contains("Playfair+Display"));
        assertTrue(html.contains("#e11d48"));
        assertTrue("the accent word lit", html.contains(">Code</span>"));
        assertTrue("a card layout", html.contains("box-shadow:0 30px 80px"));
        assertTrue("a grid background", html.contains("background-size"));
        assertTrue("the ring and the sparkle", html.contains("border-radius:50%;border:") && html.contains("<path d=\"M50 0 C54"));
    }

    @Test
    public void aWrongOrMissingChoiceFallsToTheFormulaAndWordsStayText() {
        DesignGen.Spec bad = DesignGen.Spec.of(MiniJson.parse("{\"colors\":[\"red\"],\"font\":\"Comic Sans\",\"layout\":\"diagonal\"}"));
        String html = DesignGen.make(3, "<script>alert(1)</script> Deals", "Line", "Tag", null, 1080, 1080, bad);
        assertFalse(html.toLowerCase().contains("<script>"));
        assertTrue(html.toLowerCase().contains("&lt;script&gt;"));
        assertFalse(html.contains("Comic"));
        assertNotEquals("", html);
        String photo = DesignGen.make(3, "Fresh stock", "Line", "Tag", Catalog.PHOTO + "p1", 1080, 1080);
        assertTrue("a product photo fills the picture", photo.contains("src=\"" + Catalog.PHOTO + "p1\""));
    }
}

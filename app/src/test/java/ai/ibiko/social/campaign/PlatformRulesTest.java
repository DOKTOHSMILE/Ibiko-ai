package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The networks' rules: what each bans outright, what is only risky, and what is fine (household gadgets are). */
public class PlatformRulesTest {
    @Test
    public void stunGunsAreBannedOnFacebookAndRiskyOnX() {
        List<PlatformRules.Flag> fb = PlatformRules.check("facebook", "Tazers and stun guns in stock!");
        assertEquals(1, fb.size());
        assertEquals("weapons", fb.get(0).what);
        assertEquals(PlatformRules.Level.BANNED, fb.get(0).level);
        assertTrue(PlatformRules.banned("instagram", "Pepper spray for your safety"));
        assertFalse("X limits it, doesn't ban it", PlatformRules.banned("x", "Tazers in stock"));
        assertEquals(PlatformRules.Level.RISKY, PlatformRules.check("twitter", "Tazers in stock").get(0).level);
    }

    @Test
    public void everydayGadgetsAreFine() {
        for (String ok : new String[]{"Gas cylinders delivered to your door", "Hot glue guns and heat guns for crafts",
                "Ginger and garlic grinders", "A secure lock for your gate", "Bullet points: fast, cheap, durable"}) {
            assertTrue(ok, PlatformRules.check("facebook", ok).isEmpty());
        }
    }

    @Test
    public void claimsAndBaitAreRiskyEverywhere() {
        List<PlatformRules.Flag> f = PlatformRules.check("linkedin", "This cream cures acne. Like and share to win!");
        assertEquals(2, f.size());
        assertEquals(PlatformRules.Level.RISKY, f.get(0).level);
        assertTrue(PlatformRules.banned("x", "Guaranteed profit of 30% weekly returns"));
        assertTrue(PlatformRules.said(f).startsWith("Risky: health claims"));
    }

    @Test
    public void theBriefIsCheckedBeforePlanning() {
        Campaign.Brief b = new Campaign.Brief();
        b.business = "Valineto3 Enterprises";
        b.offer = "(Tazers), gas cylinders and other gadgets";
        List<PlatformRules.Flag> f = PlatformRules.brief(b, Arrays.asList("facebook", "x"));
        assertEquals(1, f.size()); // the same rule once, for the network that bans it
        assertEquals("weapons", f.get(0).what);
        assertTrue(PlatformRules.brief(b, Arrays.asList("x")).isEmpty());
        Product p = new Product();
        p.name = "Vape pen";
        b.offer = "Gadgets";
        b.products.add(p);
        assertEquals("tobacco and vapes", PlatformRules.brief(b, Arrays.asList("instagram")).get(0).what);
    }
}

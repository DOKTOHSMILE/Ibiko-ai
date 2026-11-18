package ai.ibiko.social;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The social playbook names real codes and real capabilities, so a model following it calls what exists. */
public class SocialPlaybookTest {
    @Test
    public void everyCapabilityItNamesExistsAndEveryVerbIsAReal() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("social\\.[a-z_]+\\.[a-z_]+|social\\.accounts\\.list")
                .matcher(SocialPlaybook.RULES + SocialPlaybook.SHORT);
        int named = 0;
        while (m.find()) {
            named++;
            assertTrue(m.group(), ai.ibiko.social.intel.SocialCapabilityRegistry.get(m.group()) != null);
        }
        assertTrue("it points to the capabilities", named >= 8);
        String spec = ai.ibiko.suit.FunctionCode.SO.toString() + " " + ai.ibiko.suit.FunctionCode.SO.name();
        for (String verb : new String[] {"SEARCH", "TRENDING", "CONNECT", "POST", "COMMENT"}) {
            assertTrue(verb, SocialPlaybook.RULES.contains("§SO " + verb));
        }
        assertTrue(spec, SocialPlaybook.RULES.contains("§SP LIST") && SocialPlaybook.RULES.contains("§AC post on"));
        assertTrue("the short form is short", SocialPlaybook.SHORT.length() < SocialPlaybook.RULES.length() / 3);
        assertTrue(SocialPlaybook.SHORT.contains("yes") && SocialPlaybook.RULES.contains("Never say something was posted"));
    }
}

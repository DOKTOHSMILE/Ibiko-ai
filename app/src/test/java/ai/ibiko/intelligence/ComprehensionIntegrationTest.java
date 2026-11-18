package ai.ibiko.intelligence;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ComprehensionIntegrationTest {
    @Test public void comprehensionPassesLocalUnderstandingEvidenceToModelBrief() {
        String brief = ai.ibiko.suit.Comprehension.of("Please do not delete the file tomorrow").brief();
        assertTrue(brief.contains("LOCAL UNDERSTANDING"));
        assertTrue(brief.contains("negated=true"));
        assertTrue(brief.contains("temporal=true"));
    }
}

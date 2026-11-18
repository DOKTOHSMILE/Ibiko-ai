package ai.ibiko.intelligence.importer;

import static org.junit.Assert.*;
import org.junit.Test;

public class DatasetImportPolicyTest {
    @Test public void unknownAndUnreviewedDatasetsAreBlocked() {
        assertFalse(new DatasetImportPolicy(DatasetImportPolicy.Decision.APPROVED, false, true)
                .allows(DatasetImportPolicy.Purpose.RETRIEVAL, true));
        assertFalse(new DatasetImportPolicy(DatasetImportPolicy.Decision.UNKNOWN, true, true)
                .allows(DatasetImportPolicy.Purpose.TRAINING, false));
    }
    @Test public void nonCommercialCanBeUsedOnlyOutsideCommercialBuilds() {
        DatasetImportPolicy p = new DatasetImportPolicy(DatasetImportPolicy.Decision.NON_COMMERCIAL_ONLY, true, true);
        assertTrue(p.allows(DatasetImportPolicy.Purpose.RETRIEVAL, false));
        assertFalse(p.allows(DatasetImportPolicy.Purpose.RETRIEVAL, true));
    }
    @Test public void evaluationOnlyCannotBeImportedAsKnowledgeOrTraining() {
        DatasetImportPolicy p = new DatasetImportPolicy(DatasetImportPolicy.Decision.EVALUATION_ONLY, true, true);
        assertFalse(p.allows(DatasetImportPolicy.Purpose.RETRIEVAL, false));
        assertFalse(p.allows(DatasetImportPolicy.Purpose.TRAINING, false));
        assertTrue(p.allows(DatasetImportPolicy.Purpose.EVALUATION, false));
    }
}

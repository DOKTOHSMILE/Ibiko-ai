package ai.ibiko.intelligence.importer;

/** Explicit human-reviewed gate. A public download URL is not permission to train or redistribute. */
public final class DatasetImportPolicy {
    public enum Purpose { RETRIEVAL, TRAINING, EVALUATION }
    public enum Decision { APPROVED, NON_COMMERCIAL_ONLY, EVALUATION_ONLY, UNKNOWN }

    private final Decision decision;
    private final boolean licenceReviewed;
    private final boolean provenanceReviewed;

    public DatasetImportPolicy(Decision decision, boolean licenceReviewed, boolean provenanceReviewed) {
        if (decision == null) throw new IllegalArgumentException("decision is required");
        this.decision = decision;
        this.licenceReviewed = licenceReviewed;
        this.provenanceReviewed = provenanceReviewed;
    }

    public boolean allows(Purpose purpose, boolean commercialProduct) {
        if (purpose == null || !licenceReviewed || !provenanceReviewed) return false;
        if (decision == Decision.UNKNOWN) return false;
        if (commercialProduct && decision != Decision.APPROVED) return false;
        if (purpose == Purpose.TRAINING && decision == Decision.EVALUATION_ONLY) return false;
        if (purpose == Purpose.RETRIEVAL && decision == Decision.EVALUATION_ONLY) return false;
        return true;
    }

    public String explanation(Purpose purpose, boolean commercialProduct) {
        if (!licenceReviewed) return "Licence has not been reviewed by a human.";
        if (!provenanceReviewed) return "Dataset provenance has not been reviewed.";
        if (decision == Decision.UNKNOWN) return "Licence or permitted use is unknown.";
        if (commercialProduct && decision != Decision.APPROVED) return "This dataset is not approved for commercial-product use.";
        if (purpose != null && purpose != Purpose.EVALUATION && decision == Decision.EVALUATION_ONLY)
            return "This dataset is restricted to evaluation.";
        return "Import permitted by the recorded review decision.";
    }
}

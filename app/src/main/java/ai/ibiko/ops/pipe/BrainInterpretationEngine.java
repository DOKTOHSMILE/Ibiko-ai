package ai.ibiko.ops.pipe;

/** Turns a decomposition into a structured reading and rejects a reading that drops a negation. */
public final class BrainInterpretationEngine {
    public interface ModelReading {
        SemanticTypes.Interpretation read(SemanticTypes.Decomposition decomposition);
    }

    public SemanticTypes.Interpretation interpret(SemanticTypes.Decomposition d, ModelReading model) {
        if (d == null) {
            SemanticTypes.Interpretation invalid = new SemanticTypes.Interpretation();
            invalid.status = "insufficient";
            invalid.intent = "clarify";
            invalid.clarification = true;
            invalid.clarify = "I need the request again before I can interpret it.";
            return invalid;
        }
        SemanticTypes.Interpretation local = baseline(d);
        // A model must not talk its way past an unclear transcript or an unresolved target.
        if (model == null || "insufficient".equals(local.status) || "ambiguous".equals(local.status)) {
            return local;
        }
        SemanticTypes.Interpretation proposed = model.read(d);
        if (proposed == null) {
            local.status = "insufficient";
            return local;
        }
        if (contradicts(d, proposed)) {
            local.status = "invalid";
            local.intent = "reject";
            local.operation = "none";
            local.clarification = false;
            local.clarify = "I won't do that, because it contradicts what you said.";
            return local;
        }
        // Model enrichment may add interpretation, but cannot erase the user's explicit constraints.
        for (String constraint : local.constraints) {
            if (constraint != null && !constraint.trim().isEmpty() && !proposed.constraints.contains(constraint)) {
                proposed.constraints.add(constraint);
            }
        }
        if (proposed.target == null || proposed.target.trim().isEmpty()) proposed.target = local.target;
        if (proposed.parent == null || proposed.parent.trim().isEmpty()) proposed.parent = local.parent;
        if (proposed.intent == null || proposed.intent.trim().isEmpty()) proposed.intent = local.intent;
        if (proposed.operation == null || proposed.operation.trim().isEmpty()) proposed.operation = local.operation;
        if (proposed.status == null || proposed.status.trim().isEmpty()) proposed.status = local.status;
        if (proposed.confidenceLabel == null || proposed.confidenceLabel.trim().isEmpty()) proposed.confidenceLabel = local.confidenceLabel;
        return proposed;
    }

    private static SemanticTypes.Interpretation baseline(SemanticTypes.Decomposition d) {
        SemanticTypes.Interpretation i = new SemanticTypes.Interpretation();
        i.target = d.target;
        i.parent = d.parent;
        i.constraints.addAll(d.preserve);
        i.constraints.addAll(d.negations);
        if (d.transcriptConfidence < 0.45) {
            i.status = "insufficient";
            i.clarification = true;
            i.clarify = "I didn't catch that clearly.";
            return i;
        }
        if (d.ambiguous && d.preserve.isEmpty()) {
            i.status = "ambiguous";
            i.clarification = true;
            i.clarify = "Which thing do you mean?";
            i.intent = "clarify";
            return i;
        }
        String action = d.action == null ? "" : d.action.trim();
        if ("clock".equals(action) || "picker".equals(action) || "learn".equals(action) || "news".equals(action)) {
            i.intent = action;
            i.operation = action;
            i.status = "understood";
            return i;
        }
        if ("explain".equals(action)) {
            i.intent = "explain";
            i.operation = "explain";
            i.status = "understood";
            return i;
        }
        if (d.question && ("chat".equals(action) || action.isEmpty())) {
            i.intent = "answer";
            i.operation = "answer";
            i.status = "pass";
            return i;
        }
        i.intent = action;
        i.operation = action;
        i.status = "chat".equals(action) ? "unsupported" : "understood";
        return i;
    }

    static boolean contradicts(SemanticTypes.Decomposition d, SemanticTypes.Interpretation proposed) {
        String original = d.original == null ? "" : d.original.toLowerCase(java.util.Locale.ROOT);
        String operation = proposed.operation == null ? "" : proposed.operation.toLowerCase(java.util.Locale.ROOT);
        boolean userForbidsDelete = java.util.regex.Pattern.compile(
                "\\b(?:do not|don't|dont|never|must not|mustn't|cannot|can't)\\s+(?:delete|remove|erase|wipe)\\b")
                .matcher(original).find() || original.contains("what would happen");
        boolean proposesDelete = "delete".equals(operation) || "remove".equals(operation)
                || "erase".equals(operation) || "wipe".equals(operation);
        if (userForbidsDelete && proposesDelete) return true;
        if (!d.negations.isEmpty() && proposesDelete && original.matches("(?s).*\\b(?:do not|don't|dont|never)\\s+(?:delete|remove|erase|wipe)\\b.*")) {
            return true;
        }
        for (String keep : d.preserve) {
            if (keep.equals(proposed.target) && proposesDelete) {
                return true;
            }
        }
        return false;
    }
}

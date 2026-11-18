package ai.ibiko.suit;

import java.util.function.Function;

/**
 * Ibiko's own light brain, always present in the suit whatever model is plugged in. It does what a language model
 * does badly or slowly, in microseconds and exactly: it reads the message ({@link Comprehension}) and works out any
 * arithmetic ({@link Arithmetic}), and hands both to the model as facts. With no model at all, it still answers
 * what it can: a sum, a greeting, thanks, or what it learned from earlier conversations ({@code learned}). It never
 * runs a function: functions are the model's to choose, by code. Pure Java.
 */
public final class Cortex {
    /** What it learned from the models' earlier answers (the brain box), or null. */
    private final Function<String, String> learned;
    /** Confirmed/remembered sentences from Ibiko 1, kept separate from model-generated answers. */
    private final Function<String, java.util.List<String>> wisdomFacts;
    private final java.util.LinkedHashMap<String, ai.ibiko.intelligence.context.ContextualUnderstandingEngine> contextualByConversation =
            new java.util.LinkedHashMap<String, ai.ibiko.intelligence.context.ContextualUnderstandingEngine>(8, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, ai.ibiko.intelligence.context.ContextualUnderstandingEngine> eldest) {
                    return size() > 8; // bounded memory; chat history remains the durable transcript
                }
            };

    public Cortex(Function<String, String> learned) {
        this(learned, null);
    }

    /** The app supplies Ibiko 1's relevant remembered sentences; tests and other hosts may omit the source. */
    public Cortex(Function<String, String> learned, Function<String, java.util.List<String>> wisdomFacts) {
        this.learned = learned;
        this.wisdomFacts = wisdomFacts;
    }

    /**
     * The same Ibiko 1 wisdom formula used by every model route. This is evidence/context only: it cannot dispatch
     * tools, perform actions, or override the app's explicit command gate.
     */
    public String wisdomContext(String message) {
        java.util.List<String> known = wisdomFacts == null ? java.util.Collections.<String>emptyList()
                : wisdomFacts.apply(message == null ? "" : message);
        return ai.ibiko.ibiko1.formulas.Wisdom.brief(message, known);
    }

    /** What the model is given with the message: Ibiko's reading of it and any exact arithmetic. */
    public synchronized String context(String message) { return context(message, "default"); }

    /** Context is isolated per chat. The chat transcript remains the durable history; this is a bounded local cache. */
    public synchronized String context(String message, String conversationId) {
        String key = conversationId == null || conversationId.trim().isEmpty() ? "default" : conversationId.trim();
        ai.ibiko.intelligence.context.ContextualUnderstandingEngine engine = contextualByConversation.get(key);
        if (engine == null) {
            engine = new ai.ibiko.intelligence.context.ContextualUnderstandingEngine();
            contextualByConversation.put(key, engine);
        }
        String brief = Comprehension.of(message).brief();
        return brief + "\n" + engine.analyze(message).toPromptContext();
    }

    /**
     * Adds Ibiko 1's existing WordNet/ConceptNet comprehension result to this chat's context. The richer reading is
     * produced by Ibiko1 itself; Cortex only carries it to the selected model. It is advisory evidence, never a tool
     * command, and it does not introduce another assistant, persona, parser or model call.
     */
    public synchronized String context(String message, String conversationId,
            ai.ibiko.ibiko1.comprehend.Comprehension.Meaning meaning) {
        String base = context(message, conversationId);
        if (meaning == null) {
            return base;
        }
        String structured = meaning.promptBrief();
        if (structured == null || structured.trim().isEmpty()) {
            return base;
        }
        return base + "\nIbiko 1 structured comprehension (advisory evidence only; not an app command): "
                + structured;
    }

    /** Opaque, versioned snapshot for the app's own private persistence layer. */
    public synchronized String exportConversationContext() { return exportConversationContext("default"); }
    public synchronized String exportConversationContext(String conversationId) {
        ai.ibiko.intelligence.context.ContextualUnderstandingEngine engine = contextualByConversation.get(conversationId == null ? "default" : conversationId);
        return engine == null ? "" : engine.exportSnapshot();
    }

    /** Restore a previously exported context snapshot. Invalid snapshots are rejected without mutation. */
    public synchronized boolean restoreConversationContext(String snapshot) { return restoreConversationContext("default", snapshot); }
    public synchronized boolean restoreConversationContext(String conversationId, String snapshot) {
        String key = conversationId == null ? "default" : conversationId;
        ai.ibiko.intelligence.context.ContextualUnderstandingEngine engine = new ai.ibiko.intelligence.context.ContextualUnderstandingEngine();
        if (!engine.restoreSnapshot(snapshot)) return false;
        contextualByConversation.put(key, engine);
        return true;
    }

    /** Explicitly clear conversational context when the user starts a genuinely new conversation. */
    public synchronized void resetConversationContext() { resetConversationContext("default"); }
    public synchronized void resetConversationContext(String conversationId) {
        contextualByConversation.remove(conversationId == null ? "default" : conversationId);
    }

    /** Optional offline retrieval path. Retrieved entries are evidence only and never capability commands. */
    public String context(String message, ai.ibiko.intelligence.StructuredKnowledgeDatabase knowledge,
            String language, int maxKnowledgeChars) {
        String brief = context(message);
        String retrieved = ai.ibiko.intelligence.KnowledgeContextBuilder.build(
                knowledge, message, language, Math.max(0, maxKnowledgeChars));
        return retrieved.isEmpty() ? brief : brief + "\n" + retrieved;
    }

    /**
     * An answer with no model, or null when it needs one (then Ibiko says it has no brain for that yet). Only
     * arithmetic, greetings, thanks, a judgement weighed from the person's own limits, and what was learned:
     * nothing is made up.
     */
    public String answerAlone(String message) {
        Comprehension c = Comprehension.of(message);
        if (c.math != null && c.mathValue != null && !c.mathValue.isEmpty()) {
            return c.math + " = " + c.mathValue;
        }
        if (c.act == Comprehension.Act.GREETING) {
            return "Hello. No brain is connected right now: add an AI provider or download an offline model, and I can do much more.";
        }
        if (c.act == Comprehension.Act.THANKS) {
            return "You're welcome.";
        }
        String known = learned == null ? null : learned.apply(message);
        if (known != null) {
            return "From what I learned earlier: " + known;
        }
        if (c.judgement) {
            // No-model judgement: apply the same stored-evidence wisdom formula before giving the local decision aid.
            java.util.List<String> knownLines = wisdomFacts == null
                    ? java.util.Collections.<String>emptyList() : wisdomFacts.apply(message);
            ai.ibiko.ibiko1.formulas.Wisdom.Applied applied =
                    ai.ibiko.ibiko1.formulas.Wisdom.of(message, knownLines);
            String decision = ai.ibiko.ibiko1.formulas.Critical.deliberate(message);
            if (applied.evidenceCount > 0 && !applied.implies.isEmpty()) {
                decision += "\n\nWhat the remembered information implies: " + applied.implies;
                if (!applied.doesNotSay.isEmpty()) {
                    decision += " It does not establish: " + applied.doesNotSay;
                }
            }
            return decision;
        }
        return null;
    }
}
